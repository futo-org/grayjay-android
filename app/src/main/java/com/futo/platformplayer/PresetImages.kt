package com.futo.platformplayer

class PresetImages {
    companion object {
        val images = mapOf<String, Int>(
            Pair("xp_book", R.drawable.xp_book),
            Pair("xp_forest", R.drawable.xp_forest),
            Pair("xp_code", R.drawable.xp_code),
            Pair("xp_controller", R.drawable.xp_controller),
            Pair("xp_laptop", R.drawable.xp_laptop)
        );

        fun getPresetResIdByName(name: String): Int {
            return images[name] ?: -1;
        }
        fun getPresetNameByResId(id: Int): String? {
            return images.entries.find { it.value == id }?.key;
        }
    }
}