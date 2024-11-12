package com.futo.platformplayer.models

import com.futo.platformplayer.serializers.OffsetDateTimeSerializer
import java.time.OffsetDateTime
import java.util.UUID

@kotlinx.serialization.Serializable
open class SubscriptionGroup {
    var id: String = UUID.randomUUID().toString();
    var name: String;
    var image: ImageVariable? = null;
    var urls: MutableList<String> = mutableListOf();
    var priority: Int = 99;

    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastChange : OffsetDateTime = OffsetDateTime.MIN;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var creationTime : OffsetDateTime = OffsetDateTime.now();

    constructor(name: String) {
        this.name = name;
    }
    constructor(parent: SubscriptionGroup) {
        this.id = parent.id;
        this.name = parent.name;
        this.image = parent.image;
        this.urls = parent.urls;
        this.priority = parent.priority;
        this.lastChange = parent.lastChange;
        this.creationTime = parent.creationTime;
    }

    class Selectable(parent: SubscriptionGroup, isSelected: Boolean = false): SubscriptionGroup(parent) {
        var selected: Boolean = isSelected;
    }

    class Add: SubscriptionGroup("+") {
        init {
            urls.add("+");
        }
    }
}