package com.futo.platformplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.Layout
import android.text.method.ScrollingMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.dialogs.AutoUpdateDialog
import com.futo.platformplayer.dialogs.AutomaticBackupDialog
import com.futo.platformplayer.dialogs.AutomaticRestoreDialog
import com.futo.platformplayer.dialogs.CastingAddDialog
import com.futo.platformplayer.dialogs.CastingHelpDialog
import com.futo.platformplayer.dialogs.ChangelogDialog
import com.futo.platformplayer.dialogs.CommentDialog
import com.futo.platformplayer.dialogs.ConnectCastingDialog
import com.futo.platformplayer.dialogs.ConnectedCastingDialog
import com.futo.platformplayer.dialogs.ImportDialog
import com.futo.platformplayer.dialogs.ImportOptionsDialog
import com.futo.platformplayer.dialogs.MigrateDialog
import com.futo.platformplayer.dialogs.PluginUpdateDialog
import com.futo.platformplayer.dialogs.ProgressDialog
import com.futo.platformplayer.engine.exceptions.PluginException
import com.futo.platformplayer.fragment.mainactivity.main.MainFragment
import com.futo.platformplayer.fragment.mainactivity.main.SourceDetailFragment
import com.futo.platformplayer.fragment.mainactivity.main.VideoDetailFragment
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.ImportCache
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateBackup
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.stores.v2.ManagedStore
import com.futo.platformplayer.views.ToastView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import userpackage.Protocol
import java.io.File

class UIDialogs {
    companion object {
        private val TAG = "Dialogs"

        private val _openDialogs = arrayListOf<AlertDialog>();

        private fun registerDialogOpened(dialog: AlertDialog) {
            _openDialogs.add(dialog);
        }

        private fun registerDialogClosed(dialog: AlertDialog) {
            _openDialogs.remove(dialog);
        }

        fun dismissAllDialogs() {
            for (openDialog in _openDialogs) {
                openDialog.dismiss();
            }

            _openDialogs.clear();
        }

        fun showDialogProgress(context: Context, handler: ((ProgressDialog)->Unit)) {
            val dialog = ProgressDialog(context, handler);
            registerDialogOpened(dialog);
            dialog.setOnDismissListener { registerDialogClosed(dialog) };
            dialog.show();
        }

        fun showDialogOk(context: Context, icon: Int, text: String, handler: (()->Unit)? = null) {
            showDialog(context, icon, text, null, null, 0, Action("Ok", { handler?.invoke(); }, ActionStyle.PRIMARY));
        }

        fun multiShowDialog(context: Context, finally: (() -> Unit)?, vararg dialogDescriptor: Descriptor?) = multiShowDialog(context, dialogDescriptor.toList(), finally);
        fun multiShowDialog(context: Context, vararg dialogDescriptor: Descriptor?) = multiShowDialog(context, dialogDescriptor.toList());
        fun multiShowDialog(context: Context, dialogDescriptor: List<Descriptor?>, finally: (()->Unit)? = null) {
            if(dialogDescriptor.isEmpty()) {
                if (finally != null) {
                    finally()
                };
                return;
            }
            if(dialogDescriptor[0] == null) {
                multiShowDialog(context, dialogDescriptor.drop(1), finally);
                return;
            }
            val currentDialog = dialogDescriptor[0]!!;
            if(!currentDialog.shouldShow()) {
                multiShowDialog(context, dialogDescriptor.drop(1), finally);
                return;
            }

            showDialog(context,
                currentDialog.icon,
                currentDialog.text,
                currentDialog.textDetails,
                currentDialog.code,
                currentDialog.defaultCloseAction,
                *currentDialog.actions.map {
                    return@map Action(it.text, {
                        it.action();
                        multiShowDialog(context, dialogDescriptor.drop(1), finally);
                    }, it.style);
                }.toTypedArray());
        }

        fun showUrlHandlingPrompt(context: Context, onYes: (() -> Unit)? = null) {
            val builder = AlertDialog.Builder(context)
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_url_handling, null)
            builder.setView(view)

            val dialog = builder.create()
            registerDialogOpened(dialog)

            view.findViewById<TextView>(R.id.button_no).apply {
                this.setOnClickListener {
                    dialog.dismiss()
                }
            }

            view.findViewById<LinearLayout>(R.id.button_yes).apply {
                this.setOnClickListener {
                    if (BuildConfig.IS_PLAYSTORE_BUILD) {
                        dialog.dismiss()
                        showDialogOk(context, R.drawable.ic_error_pred, context.getString(R.string.play_store_version_does_not_support_default_url_handling)) {
                            onYes?.invoke()
                        }
                    } else {
                        try {
                            val intent =
                                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri = Uri.fromParts("package", context.packageName, null)
                            intent.data = uri
                            context.startActivity(intent)
                        } catch (e: Throwable) {
                            toast(context, context.getString(R.string.failed_to_show_settings))
                        }

                        onYes?.invoke()
                        dialog.dismiss()
                    }
                }
            }

            dialog.setOnDismissListener {
                registerDialogClosed(dialog)
            }

            dialog.show()
        }

        fun showAutomaticBackupDialog(context: Context, skipRestoreCheck: Boolean = false, onClosed: (()->Unit)? = null) {
            val dialogAction: ()->Unit = {
                val dialog = AutomaticBackupDialog(context);
                registerDialogOpened(dialog);
                dialog.setOnDismissListener { registerDialogClosed(dialog); onClosed?.invoke() };
                dialog.show();
            };
            if(StateBackup.hasAutomaticBackup() && !skipRestoreCheck)
                UIDialogs.showDialog(context, R.drawable.ic_move_up, context.getString(R.string.an_old_backup_is_available), context.getString(R.string.would_you_like_to_restore_this_backup), null, 0,
                    UIDialogs.Action(context.getString(R.string.cancel), {}), //To nothing
                    UIDialogs.Action(context.getString(R.string.override), {
                        dialogAction();
                    }, UIDialogs.ActionStyle.DANGEROUS),
                    UIDialogs.Action(context.getString(R.string.restore), {
                        UIDialogs.showAutomaticRestoreDialog(context, StateApp.instance.scope);
                    }, UIDialogs.ActionStyle.PRIMARY)
                );
            else {
                dialogAction();
            }
        }
        fun showAutomaticRestoreDialog(context: Context, scope: CoroutineScope) {
            val dialog = AutomaticRestoreDialog(context, scope);
            registerDialogOpened(dialog);
            dialog.setOnDismissListener { registerDialogClosed(dialog) };
            dialog.show();
        }

        fun showPluginUpdateDialog(context: Context, oldConfig: SourcePluginConfig, newConfig: SourcePluginConfig) {
            val dialog = PluginUpdateDialog(context, oldConfig, newConfig);
            registerDialogOpened(dialog);
            dialog.setOnDismissListener { registerDialogClosed(dialog) };
            dialog.show();
        }

        fun showDialog(context: Context, icon: Int, text: String, textDetails: String? = null, code: String? = null, defaultCloseAction: Int, vararg actions: Action) {
            val builder = AlertDialog.Builder(context);
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_multi_button, null);
            builder.setView(view);

            val dialog = builder.create();
            registerDialogOpened(dialog);

            view.findViewById<ImageView>(R.id.dialog_icon).apply {
                this.setImageResource(icon);
            }
            view.findViewById<TextView>(R.id.dialog_text).apply {
                this.text = text;
            };
            view.findViewById<TextView>(R.id.dialog_text_details).apply {
                if (textDetails == null)
                    this.visibility = View.GONE;
                else {
                    this.text = textDetails;
                }
            };
            view.findViewById<TextView>(R.id.dialog_text_code).apply {
                if (code == null) this.visibility = View.GONE;
                else {
                    this.text = code;
                    this.movementMethod = ScrollingMovementMethod.getInstance();
                    this.visibility = View.VISIBLE;
                    this.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                }
            };
            view.findViewById<LinearLayout>(R.id.dialog_buttons).apply {
                val center = actions.any { it?.center == true };
                val buttons = actions.map<Action, TextView> { act ->
                    val buttonView = TextView(context);
                    val dp10 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics).toInt();
                    val dp28 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28f, resources.displayMetrics).toInt();
                    val dp14 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14.0f, resources.displayMetrics).toInt();
                    buttonView.layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                        this.marginStart = if(actions.size >= 2) dp14 / 2 else dp28 / 2;
                        this.marginEnd = if(actions.size >= 2) dp14 / 2 else dp28 / 2;
                    };
                    buttonView.setTextColor(Color.WHITE);
                    buttonView.textSize = 14f;
                    buttonView.typeface = resources.getFont(R.font.inter_regular);
                    buttonView.text = act.text;
                    buttonView.setOnClickListener { act.action(); dialog.dismiss(); };
                    when(act.style) {
                        ActionStyle.PRIMARY -> buttonView.setBackgroundResource(R.drawable.background_button_primary);
                        ActionStyle.ACCENT -> buttonView.setBackgroundResource(R.drawable.background_button_accent);
                        ActionStyle.DANGEROUS -> buttonView.setBackgroundResource(R.drawable.background_button_pred);
                        ActionStyle.DANGEROUS_TEXT -> buttonView.setTextColor(ContextCompat.getColor(context, R.color.pastel_red))
                        else -> buttonView.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
                    }
                    val paddingSpecialButtons = if(actions.size > 2) dp14 else dp28;
                    if(act.style != ActionStyle.NONE && act.style != ActionStyle.DANGEROUS_TEXT)
                        buttonView.setPadding(paddingSpecialButtons, dp10, paddingSpecialButtons, dp10);
                    else
                        buttonView.setPadding(dp10, dp10, dp10, dp10);

                    return@map buttonView;
                };
                if(actions.size <= 1 || center)
                    this.gravity = Gravity.CENTER;
                else
                    this.gravity = Gravity.END;
                for(button in buttons)
                    this.addView(button);
            };
            dialog.setOnCancelListener {
                if(defaultCloseAction >= 0 && defaultCloseAction < actions.size)
                    actions[defaultCloseAction].action();
            }
            dialog.setOnDismissListener {
                registerDialogClosed(dialog);
            }
            dialog.show();
        }

        fun showGeneralErrorDialog(context: Context, msg: String, ex: Throwable? = null, button: String = "Ok", onOk: (()->Unit)? = null) {
            showDialog(context,
                R.drawable.ic_error_pred,
                msg, (if(ex != null ) "${ex.message}" else ""), if(ex is PluginException) ex.code else null,
                0,
                UIDialogs.Action(button, {
                    onOk?.invoke();
                }, UIDialogs.ActionStyle.PRIMARY)
            );
        }
        fun showGeneralRetryErrorDialog(context: Context, msg: String, ex: Throwable? = null, retryAction: (() -> Unit)? = null, closeAction: (() -> Unit)? = null, mainFragment: MainFragment? = null) {
            val pluginConfig = if(ex is PluginException) ex.config else null;
            val pluginInfo = if(ex is PluginException)
                "\nPlugin [${ex.config.name}]" else "";

            var exMsg = if(ex != null ) "${ex.message}" else "";
            if(pluginConfig != null && pluginConfig is SourcePluginConfig && StatePlugins.instance.hasUpdateAvailable(pluginConfig))
                exMsg += "\n\nAn update is available"

            if(mainFragment != null && pluginConfig != null && pluginConfig is SourcePluginConfig && StatePlugins.instance.hasUpdateAvailable(pluginConfig))
                showDialog(context,
                    R.drawable.ic_error_pred,
                    "${msg}${pluginInfo}",
                    exMsg,
                    if(ex is PluginException) ex.code else null,
                    1,
                    UIDialogs.Action(context.getString(R.string.update), {
                        mainFragment.navigate<SourceDetailFragment>(SourceDetailFragment.UpdatePluginAction(pluginConfig));
                        if(mainFragment is VideoDetailFragment)
                            mainFragment.minimizeVideoDetail();
                    }, UIDialogs.ActionStyle.ACCENT),
                    UIDialogs.Action(context.getString(R.string.close), {
                        closeAction?.invoke()
                    }, UIDialogs.ActionStyle.NONE),
                    UIDialogs.Action(context.getString(R.string.retry), {
                        retryAction?.invoke();
                    }, UIDialogs.ActionStyle.PRIMARY)
                );
            else
                showDialog(context,
                    R.drawable.ic_error_pred,
                    "${msg}${pluginInfo}",
                    exMsg,
                    if(ex is PluginException) ex.code else null,
                    0,
                    UIDialogs.Action(context.getString(R.string.close), {
                        closeAction?.invoke()
                    }, UIDialogs.ActionStyle.NONE),
                    UIDialogs.Action(context.getString(R.string.retry), {
                        retryAction?.invoke();
                    }, UIDialogs.ActionStyle.PRIMARY)
                );
        }

        fun showSingleButtonDialog(context: Context, icon: Int, text: String, buttonText: String, action: (() -> Unit)) {
            val singleButtonAction = Action(buttonText, action)
            showDialog(context, icon, text, null, null, -1, singleButtonAction)
        }

        fun showDataRetryDialog(context: Context, reason: String? = null, retryAction: (() -> Unit)? = null, closeAction: (() -> Unit)? = null) {
            val retryButtonAction = Action(context.getString(R.string.retry), retryAction ?: {}, ActionStyle.PRIMARY)
            val closeButtonAction = Action(context.getString(R.string.close), closeAction ?: {}, ActionStyle.ACCENT)
            showDialog(context, R.drawable.ic_no_internet_86dp, context.getString(R.string.data_retry), reason, null, 0, closeButtonAction, retryButtonAction)
        }


        fun showConfirmationDialog(context: Context, text: String, action: () -> Unit, cancelAction: (() -> Unit)? = null) {
            val confirmButtonAction = Action(context.getString(R.string.confirm), action, ActionStyle.PRIMARY)
            val cancelButtonAction = Action(context.getString(R.string.cancel), cancelAction ?: {}, ActionStyle.ACCENT)
            showDialog(context, R.drawable.ic_error, text, null, null, 0, cancelButtonAction, confirmButtonAction)
        }

        fun showConfirmationDialog(context: Context, text: String, action: () -> Unit, cancelAction: (() -> Unit)? = null, doNotAskAgainAction: (() -> Unit)? = null) {
            val confirmButtonAction = Action(context.getString(R.string.confirm), action, ActionStyle.PRIMARY)
            val cancelButtonAction = Action(context.getString(R.string.cancel), cancelAction ?: {}, ActionStyle.ACCENT)
            val doNotAskAgain = Action(context.getString(R.string.do_not_ask_again), doNotAskAgainAction ?: {}, ActionStyle.NONE)
            showDialog(context, R.drawable.ic_error, text, null, null, 0, doNotAskAgain, cancelButtonAction, confirmButtonAction)
        }

        fun showUpdateAvailableDialog(context: Context, lastVersion: Int, hideExceptionButtons: Boolean = false) {
            val dialog = AutoUpdateDialog(context);
            registerDialogOpened(dialog);
            dialog.setOnDismissListener { registerDialogClosed(dialog) };
            dialog.show();
            dialog.setMaxVersion(lastVersion);

            if (hideExceptionButtons) {
                dialog.hideExceptionButtons()
            }
        }

        fun showChangelogDialog(context: Context, lastVersion: Int, changelogs: Map<Int, String>? = null) {
            val dialog = ChangelogDialog(context, changelogs);
            registerDialogOpened(dialog);
            dialog.setOnDismissListener { registerDialogClosed(dialog) };
            dialog.show();
            dialog.setMaxVersion(lastVersion);
        }

        fun showInstallDownloadedUpdateDialog(context: Context, apkFile: File) {
            val dialog = AutoUpdateDialog(context);
            registerDialogOpened(dialog);
            dialog.setOnDismissListener { registerDialogClosed(dialog) };
            dialog.showPredownloaded(apkFile);
        }

        fun showMigrateDialog(context: Context, store: ManagedStore<*>, onConcluded: ()->Unit) {
            if(!store.hasMissingReconstructions())
                onConcluded();
            else
            {
                val dialog = MigrateDialog(context, store, onConcluded);
                registerDialogOpened(dialog);
                dialog.setOnDismissListener { registerDialogClosed(dialog) };
                dialog.show();
            }
        }

        fun showImportDialog(context: Context, store: ManagedStore<*>, name: String, reconstructions: List<String>, cache: ImportCache?, onConcluded: () -> Unit) {
            val dialog = ImportDialog(context, store, name, reconstructions, cache, onConcluded);
            registerDialogOpened(dialog);
            dialog.setOnDismissListener { registerDialogClosed(dialog) };
            dialog.show();
        }
        fun showImportOptionsDialog(context: MainActivity) {
            val dialog = ImportOptionsDialog(context);
            registerDialogOpened(dialog);
            dialog.setOnDismissListener { registerDialogClosed(dialog) };
            dialog.show();
        }


        fun showCastingDialog(context: Context) {
            val d = StateCasting.instance.activeDevice;
            if (d != null) {
                val dialog = ConnectedCastingDialog(context);
                if (context is Activity) {
                    dialog.setOwnerActivity(context)
                }
                registerDialogOpened(dialog);
                dialog.setOnDismissListener { registerDialogClosed(dialog) };
                dialog.show();
            } else {
                val dialog = ConnectCastingDialog(context);
                if (context is Activity) {
                    dialog.setOwnerActivity(context)
                }
                registerDialogOpened(dialog);
                val c = context
                if (c is Activity) {
                    dialog.setOwnerActivity(c);
                }
                dialog.setOnDismissListener { registerDialogClosed(dialog) };
                dialog.show();
            }
        }

        fun showCastingTutorialDialog(context: Context) {
            val dialog = CastingHelpDialog(context);
            registerDialogOpened(dialog);
            dialog.setOnDismissListener { registerDialogClosed(dialog) };
            dialog.show();
        }

        fun showCastingAddDialog(context: Context) {
            val dialog = CastingAddDialog(context);
            registerDialogOpened(dialog);
            dialog.setOnDismissListener { registerDialogClosed(dialog) };
            dialog.show();
        }

        fun toast(context : Context, text : String, long : Boolean = false) {
            Toast.makeText(context, text, if(long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show();
        }
        fun toast(text : String, long : Boolean = false) {
            StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                try {
                    StateApp.withContext {
                        toast(it, text, long);
                    }
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to show toast.", e);
                }
            }
        }
        fun appToast(text: String, long: Boolean = false) {
            appToast(ToastView.Toast(text, long))
        }
        fun appToastError(text: String, long: Boolean) {
            StateApp.withContext {
                appToast(ToastView.Toast(text, long, it.getColor(R.color.pastel_red)));
            };
        }
        fun appToast(toast: ToastView.Toast) {
            StateApp.withContext {
                if(it is MainActivity) {
                    it.showAppToast(toast);
                }
            }
        }

        fun showClickableToast(context: Context, text: String, onClick: () -> Unit, isLongDuration: Boolean = false) {
            //TODO: Is not actually clickable...
            val toastDuration = if (isLongDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            val toast = Toast(context)

            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val customView = inflater.inflate(R.layout.toast_clickable, null)
            val toastTextView: TextView = customView.findViewById(R.id.toast_text)
            toastTextView.text = text
            customView.setOnClickListener {
                onClick()
            }

            toast.view = customView
            toast.duration = toastDuration
            toast.show()
        }

        fun showCommentDialog(context: Context, contextUrl: String, ref: Protocol.Reference, onCommentAdded: (comment: IPlatformComment) -> Unit) {
            val dialog = CommentDialog(context, contextUrl, ref);
            registerDialogOpened(dialog);
            dialog.setOnDismissListener { registerDialogClosed(dialog) };
            dialog.onCommentAdded.subscribe { onCommentAdded(it); };
            dialog.show()
        }
    }

    class Descriptor(val icon: Int, val text: String, val textDetails: String? = null, val code: String? = null, val defaultCloseAction: Int, vararg acts: Action) {
        var shouldShow: ()->Boolean = {true};
        val actions: List<Action> = acts.toList();

        fun withCondition(shouldShow: () -> Boolean): Descriptor {
            this.shouldShow = shouldShow;
            return this;
        }
    }
    class Action {
        val text: String;
        val action: ()->Unit;
        val style: ActionStyle;
        var center: Boolean;

        constructor(text: String, action: ()->Unit, style: ActionStyle = ActionStyle.NONE, center: Boolean = false) {
            this.text = text;
            this.action = action;
            this.style = style;
            this.center = center;
        }
    }
    enum class ActionStyle {
        NONE,
        PRIMARY,
        ACCENT,
        DANGEROUS,
        DANGEROUS_TEXT
    }
}