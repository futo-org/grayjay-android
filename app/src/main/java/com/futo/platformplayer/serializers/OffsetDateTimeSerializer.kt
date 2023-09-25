package com.futo.platformplayer.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class OffsetDateTimeNullableSerializer : KSerializer<OffsetDateTime?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: OffsetDateTime?) {
        encoder.encodeLong(value?.toEpochSecond() ?: -1);
    }
    override fun deserialize(decoder: Decoder): OffsetDateTime? {
        val epochSecond = decoder.decodeLong();
        if(epochSecond > 9999999999)
            return OffsetDateTime.MAX;
        else if(epochSecond < -9999999999)
            return OffsetDateTime.MIN;
        return OffsetDateTime.of(LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
class OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: OffsetDateTime) {
        encoder.encodeLong(value.toEpochSecond());
    }
    override fun deserialize(decoder: Decoder): OffsetDateTime {
        val epochSecond = Math.max(decoder.decodeLong(), 0);
        if(epochSecond > 9999999999)
            return OffsetDateTime.MAX;
        else if(epochSecond < -9999999999)
            return OffsetDateTime.MIN;
        return OffsetDateTime.of(LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC), ZoneOffset.UTC);
    }
}