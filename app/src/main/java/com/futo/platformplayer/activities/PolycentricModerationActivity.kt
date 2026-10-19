package com.futo.platformplayer.activities

import android.content.Context
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.futo.platformplayer.R
import com.futo.platformplayer.polycentric.ModerationLabels
import com.futo.platformplayer.polycentric.ModerationLevel
import com.futo.platformplayer.polycentric.ModerationsManager
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.others.RadioGroupView

/** Hide/Warn/Show settings for Harbor moderation labels. */
class PolycentricModerationActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_polycentric_moderation)
        setNavigationBarColorAndIcons()

        val manager = try {
            ModerationsManager.getInstance()
        } catch (e: IllegalStateException) {
            finish()
            return
        }

        findViewById<ImageButton>(R.id.button_back).setOnClickListener {
            finish()
        }

        val layoutLabels = findViewById<LinearLayout>(R.id.layout_labels)
        val levels = listOf(
            getString(R.string.moderation_level_hide) to ModerationLevel.HIDE,
            getString(R.string.moderation_level_warn) to ModerationLevel.WARN,
            getString(R.string.moderation_level_show) to ModerationLevel.SHOW,
        )
        for (entry in ModerationLabels.ENTRIES) {
            val row = layoutInflater.inflate(R.layout.view_moderation_label, layoutLabels, false)
            row.findViewById<TextView>(R.id.text_label_name).setText(entry.name)
            row.findViewById<TextView>(R.id.text_label_description).setText(entry.description)

            val radio = row.findViewById<RadioGroupView>(R.id.radio_level)
            radio.setOptions(levels, listOf(manager.getLevel(entry.value)), multiSelect = false, atLeastOne = true)
            radio.onSelectedChange.subscribe { selected ->
                val level = selected.firstOrNull() as? ModerationLevel ?: return@subscribe
                manager.setLevel(entry.value, level)
            }

            layoutLabels.addView(row)
        }
    }
}
