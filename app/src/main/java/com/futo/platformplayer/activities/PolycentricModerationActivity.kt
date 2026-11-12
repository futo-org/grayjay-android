package com.futo.platformplayer.activities

import android.content.Context
import android.os.Bundle
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.futo.platformplayer.polycentric.ModerationsManager
import com.futo.platformplayer.R
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.setNavigationBarColorAndIcons

class PolycentricModerationActivity : AppCompatActivity() {
    private lateinit var _seekbarOffensive: SeekBar
    private lateinit var _seekbarExplicit: SeekBar
    private lateinit var _seekbarViolence: SeekBar
    private lateinit var _textOffensiveDesc: TextView
    private lateinit var _textExplicitDesc: TextView
    private lateinit var _textViolenceDesc: TextView
    private lateinit var _textOffensiveValue: TextView
    private lateinit var _textExplicitValue: TextView
    private lateinit var _textViolenceValue: TextView
    private lateinit var _moderationsManager: ModerationsManager

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_polycentric_moderation)
        setNavigationBarColorAndIcons()
        
        _moderationsManager = ModerationsManager.getInstance()
        try {
            _moderationsManager = ModerationsManager.getInstance()
        } catch (e: IllegalStateException) {
            finish()
            return
        }
        
        _seekbarOffensive = findViewById(R.id.seekbar_offensive)
        _seekbarExplicit = findViewById(R.id.seekbar_explicit)
        _seekbarViolence = findViewById(R.id.seekbar_violence)
        _textOffensiveDesc = findViewById(R.id.text_offensive_desc)
        _textExplicitDesc = findViewById(R.id.text_explicit_desc)
        _textViolenceDesc = findViewById(R.id.text_violence_desc)
        _textOffensiveValue = findViewById(R.id.text_offensive_value)
        _textExplicitValue = findViewById(R.id.text_explicit_value)
        _textViolenceValue = findViewById(R.id.text_violence_value)
        
        findViewById<ImageButton>(R.id.button_back).setOnClickListener {
            finish()
        }
        
        loadSettings()
        setupListeners()
    }
    
    private fun loadSettings() {
        val levels = _moderationsManager.moderationLevels.value ?: mapOf()
        
        val offensiveLevel = levels["hate"] ?: 2
        val explicitLevel = levels["sexual"] ?: 1
        val violenceLevel = levels["violence"] ?: 1
        
        _seekbarOffensive.progress = offensiveLevel
        _seekbarExplicit.progress = explicitLevel
        _seekbarViolence.progress = violenceLevel
        
        updateDescriptionText(_seekbarOffensive, _textOffensiveDesc, _textOffensiveValue, getOffensiveDescriptions())
        updateDescriptionText(_seekbarExplicit, _textExplicitDesc, _textExplicitValue, getExplicitDescriptions())
        updateDescriptionText(_seekbarViolence, _textViolenceDesc, _textViolenceValue, getViolenceDescriptions())
    }
    
    private fun setupListeners() {
        _seekbarOffensive.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateDescriptionText(seekBar, _textOffensiveDesc, _textOffensiveValue, getOffensiveDescriptions())
                if (fromUser) {
                    _moderationsManager.setModerationLevel("hate", progress)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        _seekbarExplicit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateDescriptionText(seekBar, _textExplicitDesc, _textExplicitValue, getExplicitDescriptions())
                if (fromUser) {
                    _moderationsManager.setModerationLevel("sexual", progress)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        _seekbarViolence.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateDescriptionText(seekBar, _textViolenceDesc, _textViolenceValue, getViolenceDescriptions())
                if (fromUser) {
                    _moderationsManager.setModerationLevel("violence", progress)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun updateDescriptionText(seekBar: SeekBar?, textDesc: TextView, textValue: TextView, descriptions: Array<String>) {
        val progress = seekBar?.progress ?: 0
        textDesc.text = descriptions[progress]
        textValue.text = progress.toString()
    }
    
    private fun getOffensiveDescriptions(): Array<String> {
        return arrayOf(
            "Neutral, general terms, no bias or hate.",
            "Mildly sensitive, factual.",
            "Potentially offensive content",
            "Offensive content"
        )
    }
    
    private fun getExplicitDescriptions(): Array<String> {
        return arrayOf(
            "No explicit content",
            "Mildly suggestive, factual or educational",
            "Moderate sexual content, non-graphic",
            "Explicit sexual content"
        )
    }
    
    private fun getViolenceDescriptions(): Array<String> {
        return arrayOf(
            "Non-violent",
            "Mild violence, factual or contextual",
            "Moderate violence, some graphic content.",
            "Graphic violence"
        )
    }
} 