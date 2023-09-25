package com.futo.platformplayer.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.futo.platformplayer.R

class TestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
    }

    companion object {
        private const val TAG = "TestActivity";
    }
}