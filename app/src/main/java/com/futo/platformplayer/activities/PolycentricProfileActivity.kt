package com.futo.platformplayer.activities

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.dp
import com.futo.platformplayer.fullyBackfillServersAnnounceExceptions
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.polycentric.PolycentricStorage
import com.futo.platformplayer.selectBestImage
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.views.buttons.BigButton
import com.futo.platformplayer.views.overlays.LoaderOverlay
import com.futo.polycentric.core.Store
import com.futo.polycentric.core.SystemState
import com.futo.polycentric.core.systemToURLInfoSystemLinkUrl
import com.futo.polycentric.core.toBase64Url
import com.futo.polycentric.core.toURLInfoSystemLinkUrl
import com.github.dhaval2404.imagepicker.ImagePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import userpackage.Protocol
import java.io.ByteArrayOutputStream
import java.io.InputStream

class PolycentricProfileActivity : AppCompatActivity() {
    private lateinit var _buttonHelp: ImageButton;
    private lateinit var _editName: EditText;
    private lateinit var _buttonExport: BigButton;
    private lateinit var _buttonOpenHarborProfile: BigButton;
    private lateinit var _buttonLogout: BigButton;
    private lateinit var _buttonDelete: BigButton;
    private lateinit var _username: String;
    private lateinit var _imagePolycentric: ImageView;
    private lateinit var _loaderOverlay: LoaderOverlay;
    private lateinit var _textSystem: TextView;
    private var _avatarUri: Uri? = null;

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_polycentric_profile);
        setNavigationBarColorAndIcons();

        _buttonHelp = findViewById(R.id.button_help);
        _imagePolycentric = findViewById(R.id.image_polycentric);
        _editName = findViewById(R.id.edit_profile_name);
        _buttonExport = findViewById(R.id.button_export);
        _buttonOpenHarborProfile = findViewById(R.id.button_open_harbor_profile);
        _buttonLogout = findViewById(R.id.button_logout);
        _buttonDelete = findViewById(R.id.button_delete);
        _loaderOverlay = findViewById(R.id.loader_overlay);
        _textSystem = findViewById(R.id.text_system)
        findViewById<ImageButton>(R.id.button_back).setOnClickListener {
            saveIfRequired();
            finish();
        };

        _imagePolycentric.setOnClickListener {
            ImagePicker.with(this)
                .cropSquare()
                .maxResultSize(256, 256)
                .start();
        }

        _buttonHelp.setOnClickListener {
            startActivity(Intent(this, PolycentricWhyActivity::class.java));
        };

        _buttonExport.onClick.subscribe {
            startActivity(Intent(this, PolycentricBackupActivity::class.java));
        };

        _buttonOpenHarborProfile.onClick.subscribe {
            val processHandle = StatePolycentric.instance.processHandle!!;
            processHandle?.let {
                val systemState = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(it.system));
                val url = it.system.systemToURLInfoSystemLinkUrl(systemState.servers.asIterable());
                val navUrl = "https://harbor.social/" + url.substring("polycentric://".length)
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(navUrl)))
            }
        }

        _buttonLogout.onClick.subscribe {
            StatePolycentric.instance.setProcessHandle(null);
            startActivity(Intent(this, PolycentricHomeActivity::class.java));
            finish();
        }

        _buttonDelete.onClick.subscribe {
            UIDialogs.showConfirmationDialog(this, getString(R.string.are_you_sure_you_want_to_remove_this_profile), {
                val processHandle = StatePolycentric.instance.processHandle;
                if (processHandle == null) {
                    UIDialogs.toast(this, getString(R.string.no_process_handle_set));
                    return@showConfirmationDialog;
                }

                StatePolycentric.instance.setProcessHandle(null);
                Store.instance.removeProcessSecret(processHandle.system);
                PolycentricStorage.instance.removeProcessSecret(processHandle.system);
                startActivity(Intent(this, PolycentricHomeActivity::class.java));
                finish();
            });
        }

        _textSystem.setOnLongClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("system", _textSystem.text)
            clipboard.setPrimaryClip(clip)
            return@setOnLongClickListener true
        }

        updateUI()

        StatePolycentric.instance.processHandle?.let { processHandle ->
            _loaderOverlay.show()

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    processHandle.fullyBackfillClient(PolycentricCache.SERVER)

                    withContext(Dispatchers.Main) {
                        updateUI();
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        UIDialogs.toast(this@PolycentricProfileActivity, getString(R.string.failed_to_backfill_client));
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        _loaderOverlay.hide()
                    }
                }
            }
        }
    }

    private fun saveIfRequired() {
        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            try {
                var hasChanges = false;
                val username = _editName.text.toString();
                if (username.length < 3) {
                    withContext(Dispatchers.Main) {
                        UIDialogs.toast(this@PolycentricProfileActivity, getString(R.string.name_must_be_at_least_3_characters_long));
                    }
                    return@launch;
                }

                val processHandle = StatePolycentric.instance.processHandle;
                if (processHandle == null) {
                    withContext(Dispatchers.Main) {
                        UIDialogs.toast(this@PolycentricProfileActivity, getString(R.string.process_handle_unset));
                    }
                    return@launch;
                }

                if (_username != username) {
                    _username = username;
                    processHandle.setUsername(username);
                    hasChanges = true;
                }

                val avatarUri = _avatarUri;
                if (avatarUri != null) {
                    val bytes = readBytesFromUri(applicationContext.contentResolver, avatarUri);
                    if (bytes == null) {
                        withContext(Dispatchers.Main) {
                            UIDialogs.toast(this@PolycentricProfileActivity, getString(R.string.failed_to_read_image));
                        }

                        return@launch;
                    }

                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size);
                    val imageBundleBuilder = Protocol.ImageBundle.newBuilder();
                    val resolutions = arrayListOf(256, 128, 32);
                    for (resolution in resolutions) {
                        val image = Bitmap.createScaledBitmap(bitmap, resolution, resolution, true)

                        val outputStream = ByteArrayOutputStream()
                        val originalMimeType = getMimeType(applicationContext.contentResolver, avatarUri) ?: "image/png"
                        val compressFormat = when(originalMimeType) {
                            "image/png" -> Pair(Bitmap.CompressFormat.PNG, "image/png")
                            "image/jpeg" -> Pair(Bitmap.CompressFormat.JPEG, "image/jpeg")
                            else -> Pair(Bitmap.CompressFormat.PNG, "image/png")
                        }
                        image.compress(compressFormat.first, 100, outputStream)
                        val imageBytes = outputStream.toByteArray()

                        val imageRanges = processHandle.publishBlob(imageBytes)
                        val imageManifest = Protocol.ImageManifest.newBuilder()
                            .setMime(compressFormat.second)
                            .setWidth(image.width.toLong())
                            .setHeight(image.height.toLong())
                            .setByteCount(imageBytes.size.toLong())
                            .setProcess(processHandle.processSecret.process.toProto())
                            .addAllSections(imageRanges.map { it.toProto() })
                            .build()

                        imageBundleBuilder.addImageManifests(imageManifest)
                    }

                    processHandle.setAvatar(imageBundleBuilder.build())
                    hasChanges = true;

                    _avatarUri = null;
                }

                if (hasChanges) {
                    try {
                        Logger.i(TAG, "Started backfill");
                        processHandle.fullyBackfillServersAnnounceExceptions();
                        Logger.i(TAG, "Finished backfill");
                        withContext(Dispatchers.Main) {
                            UIDialogs.toast(this@PolycentricProfileActivity, getString(R.string.changes_have_been_saved));
                        }
                    } catch (e: Throwable) {
                        Logger.w(TAG, "Failed to synchronize changes", e);
                        withContext(Dispatchers.Main) {
                            UIDialogs.toast(this@PolycentricProfileActivity, getString(R.string.failed_to_synchronize_changes));
                        }
                    }
                }
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to save polycentric profile.", e)
            }
        }
    }

    override fun onBackPressed() {
        saveIfRequired();
        super.onBackPressed();
    }

    private fun updateUI() {
        val processHandle = StatePolycentric.instance.processHandle!!;
        val systemState = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(processHandle.system))
        _textSystem.text = processHandle.system.key.toBase64Url()
        _username = systemState.username;
        _editName.text.clear();
        _editName.text.append(_username);

        val dp_80 = 80.dp(resources)
        val avatar = systemState.avatar.selectBestImage(dp_80 * dp_80);

        Glide.with(_imagePolycentric)
            .load(avatar?.toURLInfoSystemLinkUrl(processHandle.system.toProto(), avatar.process, systemState.servers.toList()))
            .placeholder(R.drawable.placeholder_profile)
            .crossfade()
            .into(_imagePolycentric)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            val uri: Uri = data?.data!!
            _imagePolycentric.setImageURI(uri);
            _avatarUri = uri;
        } else if (resultCode == ImagePicker.RESULT_ERROR) {
            UIDialogs.toast(this, ImagePicker.getError(data));
        } else {
            UIDialogs.toast(this, getString(R.string.image_picker_cancelled));
        }
    }

    private fun getMimeType(contentResolver: ContentResolver, uri: Uri): String? {
        var mimeType: String?;

        // Try to get MIME type from the content URI
        mimeType = contentResolver.getType(uri);

        // If the MIME type couldn't be determined from the content URI, try using the file extension
        if (mimeType == null) {
            val fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.lowercase());
        }

        return mimeType;
    }

    private fun readBytesFromUri(contentResolver: ContentResolver, uri: Uri): ByteArray? {
        var inputStream: InputStream? = null;
        val outputStream = ByteArrayOutputStream();

        try {
            inputStream = contentResolver.openInputStream(uri);
            if (inputStream != null) {
                val buffer = ByteArray(4096);
                var bytesRead: Int;
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                return outputStream.toByteArray()
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to read bytes from URI '${uri}'.");
        } finally {
            inputStream?.close();
            outputStream.close();
        }
        return null
    }

    companion object {
        private const val TAG = "PolycentricProfileActivity";
    }
}