package com.futo.platformplayer.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.futo.platformplayer.R
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.views.buttons.BigButton

class PolycentricWhyActivity : AppCompatActivity() {
    private lateinit var _buttonVideo: BigButton;
    private lateinit var _buttonTechnical: BigButton;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_polycentric_why);
        setNavigationBarColorAndIcons();

        _buttonVideo = findViewById(R.id.button_video);
        _buttonTechnical = findViewById(R.id.button_technical);
        findViewById<ImageButton>(R.id.button_back).setOnClickListener {
            finish();
        };

        _buttonVideo.onClick.subscribe {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=xYL96hb_p78"));
            startActivity(browserIntent);
        };

        _buttonTechnical.onClick.subscribe {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.polycentric.io"));
            startActivity(browserIntent);
        };
    }

    companion object {
        private const val TAG = "PolycentricWhyActivity";
    }
}