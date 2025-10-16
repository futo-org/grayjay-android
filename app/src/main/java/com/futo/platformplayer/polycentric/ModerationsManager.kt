package com.futo.platformplayer.polycentric

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONObject

class ModerationsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("polycentric_moderation", Context.MODE_PRIVATE)
    
    private val _moderationLevels = MutableLiveData<Map<String, Int>>()
    val moderationLevels: LiveData<Map<String, Int>> = _moderationLevels
    
    init {
        loadModerationLevels()
    }
    
    private fun loadModerationLevels() {
        val levels = mutableMapOf<String, Int>()
        levels["hate"] = prefs.getInt("offensive_level", 2)
        levels["sexual"] = prefs.getInt("explicit_level", 1)
        levels["violence"] = prefs.getInt("violence_level", 1)
        _moderationLevels.value = levels
    }
    
    fun setModerationLevel(category: String, level: Int) {
        when (category) {
            "hate" -> prefs.edit().putInt("offensive_level", level).apply()
            "sexual" -> prefs.edit().putInt("explicit_level", level).apply()
            "violence" -> prefs.edit().putInt("violence_level", level).apply()
        }
        
        val currentMap = _moderationLevels.value?.toMutableMap() ?: mutableMapOf()
        currentMap[category] = level
        _moderationLevels.value = currentMap
    }
    
    fun getModerationLevelsJson(): String {
        val json = JSONObject()
        moderationLevels.value?.forEach { (key, value) ->
            json.put(key, value)
        }
        return json.toString()
    }
    
    fun shouldFilter(category: String, contentLevel: Int): Boolean {
        val userLevel = when (category) {
            "hate" -> prefs.getInt("offensive_level", 2)
            "sexual" -> prefs.getInt("explicit_level", 1)
            "violence" -> prefs.getInt("violence_level", 1)
            else -> 3
        }
        
        return contentLevel > userLevel
    }
    
    fun getCurrentModerationLevels(): Map<String, Int>? {
        return moderationLevels.value
    }
    
    companion object {
        @Volatile
        private var instance: ModerationsManager? = null
        
        fun initialize(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = ModerationsManager(context.applicationContext)
                    }
                }
            }
        }
        
        fun getInstance(): ModerationsManager {
            return instance ?: throw IllegalStateException("ModerationsManager not initialized")
        }
    }
} 