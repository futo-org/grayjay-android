package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueArray
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.chapters.ChapterType
import com.futo.platformplayer.api.media.models.chapters.IChapter
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow

class JSChapter : IChapter {
    override val name: String;
    override val type: ChapterType;
    override val timeStart: Int;
    override val timeEnd: Int;

    constructor(name: String, timeStart: Int, timeEnd: Int, type: ChapterType = ChapterType.NORMAL) {
        this.name = name;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.type = type;
    }


    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject): IChapter {
            val context = "Chapter";

            val name = obj.getOrThrow<String>(config,"name", context);
            val type = ChapterType.fromInt(obj.getOrDefault<Int>(config, "type", context, ChapterType.NORMAL.value) ?: ChapterType.NORMAL.value);
            val timeStart = obj.getOrThrow<Int>(config, "timeStart", context);
            val timeEnd = obj.getOrThrow<Int>(config, "timeEnd", context);

            return JSChapter(name, timeStart, timeEnd, type);
        }

        fun fromV8(config: IV8PluginConfig, arr: V8ValueArray): List<IChapter> {
            return arr.keys.mapNotNull {
                val obj = arr.get<V8ValueObject>(it);
                return@mapNotNull fromV8(config, obj);
            };
        }
    }
}