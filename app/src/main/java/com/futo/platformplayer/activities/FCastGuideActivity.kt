package com.futo.platformplayer.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.dialogs.CastingHelpDialog
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.buttons.BigButton

class FCastGuideActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fcast_guide);
        setNavigationBarColorAndIcons();

        findViewById<TextView>(R.id.text_explanation).apply {
            val guideText = """
                <h3>1. Install FCast Receiver:</h3>
                <p>- Open Play Store, FireStore, or FCast website on your TV/desktop.<br>
                - Search for "FCast Receiver", install and open it.</p>
                <br>
                
                <h3>2. Prepare the Grayjay App:</h3>
                <p>- Ensure it's connected to the same network as the FCast Receiver.</p>
                <br>
                
                <h3>3. Initiate Casting from Grayjay:</h3>
                <p>- Click the cast button in Grayjay.</p>
                <br>
                
                <h3>4. Connect to FCast Receiver:</h3>
                <p>- Wait for your device to show in the list or add it manually with its IP address.</p>
                <br>
                
                <h3>5. Confirm Connection:</h3>
                <p>- Click "OK" to confirm your device selection.</p>
                <br>
                
                <h3>6. Start Casting:</h3>
                <p>- Press "start" next to the device you've added.</p>
                <br>
                
                <h3>7. Play Your Video:</h3>
                <p>- Start any video in Grayjay to cast.</p>
                <br>
                
                <h3>Finding Your IP Address:</h3>
                <p><b>On FCast Receiver (Android):</b> Displayed on the main screen.<br>
                <b>On Windows:</b> Use 'ipconfig' in Command Prompt.<br>
                <b>On Linux:</b> Use 'hostname -I' or 'ip addr' in Terminal.<br>
                <b>On MacOS:</b> System Preferences > Network.</p>
            """.trimIndent()

            text = Html.fromHtml(guideText, Html.FROM_HTML_MODE_COMPACT)
        }

        findViewById<ImageButton>(R.id.button_back).setOnClickListener {
            UIDialogs.showCastingTutorialDialog(this)
            finish()
        }

        findViewById<BigButton>(R.id.button_close).onClick.subscribe {
            UIDialogs.showCastingTutorialDialog(this)
            finish()
        }

        findViewById<BigButton>(R.id.button_website).onClick.subscribe {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://fcast.org/"))
                startActivity(browserIntent);
            } catch (e: Throwable) {
                Logger.i(TAG, "Failed to open browser.", e)
            }
        }

        findViewById<BigButton>(R.id.button_technical).onClick.subscribe {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gitlab.com/futo-org/fcast/-/wikis/Protocol-version-1"))
                startActivity(browserIntent);
            } catch (e: Throwable) {
                Logger.i(TAG, "Failed to open browser.", e)
            }
        }
    }

    override fun onBackPressed() {
        UIDialogs.showCastingTutorialDialog(this)
        finish()
    }

    companion object {
        private const val TAG = "FCastGuideActivity";
    }
}