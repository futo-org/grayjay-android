package com.futo.platformplayer

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.core.content.ContextCompat
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.dialogs.*
import com.futo.platformplayer.engine.exceptions.PluginException
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.stores.v2.ManagedStore
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


        fun showAutomaticBackupDialog(context: Context) {
            val dialog = AutomaticBackupDialog(context);
            registerDialogOpened(dialog);
            dialog.setOnDismissListener { registerDialogClosed(dialog) };
            dialog.show();
        }
        fun showAutomaticRestoreDialog(context: Context, scope: CoroutineScope) {
            val dialog = AutomaticRestoreDialog(context, scope);
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
                if(textDetails == null)
                    this.visibility = View.GONE;
                else
                    this.text = textDetails;
            };
            view.findViewById<TextView>(R.id.dialog_text_code).apply {
                if(code == null)
                    this.visibility = View.GONE;
                else
                    this.text = code;
            };
            view.findViewById<LinearLayout>(R.id.dialog_buttons).apply {
                val buttons = actions.map<Action, TextView> { act ->
                    val buttonView = TextView(context);
                    val dp10 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics).toInt();
                    val dp28 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28f, resources.displayMetrics).toInt();
                    val dp14 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14.0f, resources.displayMetrics);
                    buttonView.layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                        if(actions.size > 1)
                            this.marginEnd = dp28;
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
                    if(act.style != ActionStyle.NONE && act.style != ActionStyle.DANGEROUS_TEXT)
                        buttonView.setPadding(dp28, dp10, dp28, dp10);
                    else
                        buttonView.setPadding(dp10, dp10, dp10, dp10);

                    return@map buttonView;
                };
                if(actions.size <= 1)
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
        fun showGeneralRetryErrorDialog(context: Context, msg: String, ex: Throwable? = null, retryAction: (() -> Unit)? = null, closeAction: (() -> Unit)? = null) {
            val pluginInfo = if(ex is PluginException)
                "\nPlugin [${ex.config.name}]" else "";
            showDialog(context,
                R.drawable.ic_error_pred,
                "${msg}${pluginInfo}",
                (if(ex != null ) "${ex.message}" else ""),
                if(ex is PluginException) ex.code else null,
                0,
                UIDialogs.Action("Retry", {
                    retryAction?.invoke();
                }, UIDialogs.ActionStyle.PRIMARY),
                UIDialogs.Action("Close", {
                    closeAction?.invoke()
                }, UIDialogs.ActionStyle.NONE)
            );
        }

        fun showSingleButtonDialog(context: Context, icon: Int, text: String, buttonText: String, action: (() -> Unit)) {
            val singleButtonAction = Action(buttonText, action)
            showDialog(context, icon, text, null, null, -1, singleButtonAction)
        }

        fun showDataRetryDialog(context: Context, reason: String? = null, retryAction: (() -> Unit)? = null, closeAction: (() -> Unit)? = null) {
            val retryButtonAction = Action("Retry", retryAction ?: {}, ActionStyle.PRIMARY)
            val closeButtonAction = Action("Close", closeAction ?: {}, ActionStyle.ACCENT)
            showDialog(context, R.drawable.ic_no_internet_86dp, "Data Retry", reason, null, 0, closeButtonAction, retryButtonAction)
        }


        fun showConfirmationDialog(context: Context, text: String, action: () -> Unit, cancelAction: (() -> Unit)? = null) {
            val confirmButtonAction = Action("Confirm", action, ActionStyle.PRIMARY)
            val cancelButtonAction = Action("Cancel", cancelAction ?: {}, ActionStyle.ACCENT)
            showDialog(context, R.drawable.ic_error, text, null, null, 0, cancelButtonAction, confirmButtonAction)
        }

        fun showUpdateAvailableDialog(context: Context, lastVersion: Int) {
            val dialog = AutoUpdateDialog(context);
            registerDialogOpened(dialog);
            dialog.setOnDismissListener { registerDialogClosed(dialog) };
            dialog.show();
            dialog.setMaxVersion(lastVersion);
        }

        fun showChangelogDialog(context: Context, lastVersion: Int) {
            val dialog = ChangelogDialog(context);
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

        fun showImportDialog(context: Context, store: ManagedStore<*>, name: String, reconstructions: List<String>, onConcluded: () -> Unit) {
            val dialog = ImportDialog(context, store, name, reconstructions, onConcluded);
            registerDialogOpened(dialog);
            dialog.setOnDismissListener { registerDialogClosed(dialog) };
            dialog.show();
        }


        fun showCastingDialog(context: Context) {
            val d = StateCasting.instance.activeDevice;
            if (d != null) {
                val dialog = ConnectedCastingDialog(context);
                registerDialogOpened(dialog);
                dialog.setOnDismissListener { registerDialogClosed(dialog) };
                dialog.show();
            } else {
                val dialog = ConnectCastingDialog(context);
                registerDialogOpened(dialog);
                dialog.setOnDismissListener { registerDialogClosed(dialog) };
                dialog.show();
            }
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
                        Toast.makeText(it, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show();
                    }
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to show toast.", e);
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

        constructor(text: String, action: ()->Unit, style: ActionStyle = ActionStyle.NONE) {
            this.text = text;
            this.action = action;
            this.style = style;
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