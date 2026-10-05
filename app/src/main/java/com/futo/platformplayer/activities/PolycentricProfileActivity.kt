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
import android.view.View
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
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.AvatarVariant
import com.futo.platformplayer.polycentric.PolycentricAdapter
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.views.buttons.BigButton
import com.futo.platformplayer.views.overlays.LoaderOverlay
import com.github.dhaval2404.imagepicker.ImagePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

class PolycentricProfileActivity : AppCompatActivity() {
    private lateinit var _buttonHelp: ImageButton;
    private lateinit var _editName: EditText;
    private lateinit var _buttonExport: BigButton;
    private lateinit var _buttonModeration: BigButton;
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
        _buttonModeration = findViewById(R.id.button_moderation);
        _buttonLogout = findViewById(R.id.button_logout);
        _buttonDelete = findViewById(R.id.button_delete);
        _loaderOverlay = findViewById(R.id.loader_overlay);
        _textSystem = findViewById(R.id.text_system)
        findViewById<TextView>(R.id.text_cta2).apply {
            text = PolycentricAdapter.WEB_BASE_URL
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PolycentricAdapter.WEB_BASE_URL)))
            }
        }
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

        // TODO: reuse this button for Harbor's backup key concept
        _buttonExport.visibility = View.GONE;

        _buttonModeration.onClick.subscribe {
            startActivity(Intent(this, PolycentricModerationActivity::class.java));
        };

        _buttonLogout.onClick.subscribe {
            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                try {
                    StatePolycentric.instance.logout();
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to log out.", e);
                }
            }
            startActivity(Intent(this, PolycentricHomeActivity::class.java));
            finish();
        }

        _buttonDelete.onClick.subscribe {
            UIDialogs.showConfirmationDialog(this, getString(R.string.are_you_sure_you_want_to_remove_this_profile), {
                StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                    try {
                        StatePolycentric.instance.deleteIdentity();
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to delete identity.", e);
                    }
                }
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
    }

    private fun updateUI() {
        val identity = StatePolycentric.instance.activeIdentity();
        if (identity == null) {
            finish();
            return;
        }
        _textSystem.text = identity;
        _loaderOverlay.show();

        lifecycleScope.launch(Dispatchers.IO) {
            val profile = runCatching { StatePolycentric.instance.getIdentityProfile(identity) }.getOrNull();
            withContext(Dispatchers.Main) {
                val name = profile?.name;
                if (name != null) {
                    _username = name;
                    _editName.text.clear();
                    _editName.text.append(name);
                }

                Glide.with(_imagePolycentric)
                    .load(profile?.avatarUrl)
                    .placeholder(R.drawable.placeholder_profile)
                    .crossfade()
                    .into(_imagePolycentric)

                _loaderOverlay.hide();
            }
        }
    }

    private fun saveIfRequired() {
        if (!::_username.isInitialized) {
            return;
        }

        val username = _editName.text.toString();
        val avatarUri = _avatarUri;
        val nameChanged = _username != username;
        if (!nameChanged && avatarUri == null) {
            return;
        }

        if (username.length < 3) {
            UIDialogs.toast(this, getString(R.string.name_must_be_at_least_3_characters_long));
            return;
        }

        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            try {
                val avatar = avatarUri?.let { buildAvatarVariants(it) };
                StatePolycentric.instance.updateProfile(username, avatar?.first, avatar?.second ?: "image/png");
                _username = username;
                _avatarUri = null;
                withContext(Dispatchers.Main) {
                    UIDialogs.toast(this@PolycentricProfileActivity, getString(R.string.changes_have_been_saved));
                }
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to save polycentric profile.", e);
                withContext(Dispatchers.Main) {
                    UIDialogs.toast(this@PolycentricProfileActivity, getString(R.string.failed_to_synchronize_changes));
                }
            }
        }
    }

    private fun buildAvatarVariants(uri: Uri): Pair<List<AvatarVariant>, String>? {
        val bytes = readBytesFromUri(applicationContext.contentResolver, uri) ?: return null;
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null;
        val mime = getMimeType(applicationContext.contentResolver, uri) ?: "image/png";
        val compressFormat = if (mime == "image/jpeg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG;
        val outMime = if (compressFormat == Bitmap.CompressFormat.JPEG) "image/jpeg" else "image/png";

        val variants = arrayListOf(256, 128, 32).map { resolution ->
            val scaled = Bitmap.createScaledBitmap(bitmap, resolution, resolution, true);
            val outputStream = ByteArrayOutputStream();
            scaled.compress(compressFormat, 100, outputStream);
            AvatarVariant(outputStream.toByteArray(), resolution, resolution);
        };
        return Pair(variants, outMime);
    }

    override fun onBackPressed() {
        saveIfRequired();
        super.onBackPressed();
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
        mimeType = contentResolver.getType(uri);
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
