package com.futo.platformplayer.dialogs

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.FCastGuideActivity
import com.futo.platformplayer.activities.PolycentricWhyActivity
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.views.buttons.BigButton


class CastingHelpDialog(context: Context?) : AlertDialog(context) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_casting_help, null));

        findViewById<BigButton>(R.id.button_guide).onClick.subscribe {
            context.startActivity(Intent(context, FCastGuideActivity::class.java))
        }

        findViewById<BigButton>(R.id.button_video).onClick.subscribe {
            try {
                //TODO: Replace the URL with the casting video URL
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://fcast.org/"))
                context.startActivity(browserIntent);
            } catch (e: Throwable) {
                Logger.i(TAG, "Failed to open browser.", e)
            }
        }

        findViewById<BigButton>(R.id.button_website).onClick.subscribe {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://fcast.org/"))
                context.startActivity(browserIntent);
            } catch (e: Throwable) {
                Logger.i(TAG, "Failed to open browser.", e)
            }
        }

        findViewById<BigButton>(R.id.button_technical).onClick.subscribe {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gitlab.com/futo-org/fcast/-/wikis/Protocol-version-1"))
                context.startActivity(browserIntent);
            } catch (e: Throwable) {
                Logger.i(TAG, "Failed to open browser.", e)
            }
        }

        findViewById<BigButton>(R.id.button_close).onClick.subscribe {
            dismiss()
            UIDialogs.showCastingAddDialog(context)
        }
    }

    companion object {
        private val TAG = "CastingTutorialDialog";
    }
}