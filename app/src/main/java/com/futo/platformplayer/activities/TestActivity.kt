package com.futo.platformplayer.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.views.TargetTapLoaderView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        val view = findViewById<TargetTapLoaderView>(R.id.test_view)
        view.startLoader(10000)

        lifecycleScope.launch {
            delay(5000)
            view.startLoader()
        }
    }

    companion object {
        private const val TAG = "TestActivity";
    }
}