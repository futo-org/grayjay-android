package com.futo.platformplayer.serializers

import com.futo.platformplayer.Settings
import com.futo.platformplayer.logging.Logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class FlexibleBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleBoolean", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeBoolean(value);
    }
    override fun deserialize(decoder: Decoder): Boolean {
        Logger.i("Settings", "Deserializing Flexible Boolean");

        val element = (decoder as JsonDecoder).decodeJsonElement();

        if(element.jsonPrimitive.booleanOrNull != null)
            return element.jsonPrimitive.boolean;
        else if(element.jsonPrimitive.intOrNull != null)
            return element.jsonPrimitive.int == 1;
        else if(element.jsonPrimitive.isString) {
            val strValue = element.jsonPrimitive.content;
            val intValue = strValue.toIntOrNull();
            val value = if(intValue != null)
                intValue == 1;
            else
                strValue.toBooleanStrictOrNull() ?: throw SerializationException("Non-Boolean type found in flexible boolean for value [${strValue}]");
            return value;
        }
        else throw SerializationException("Failed to deserialize flexible boolean with value: ${element.jsonPrimitive.contentOrNull}");
    }
}