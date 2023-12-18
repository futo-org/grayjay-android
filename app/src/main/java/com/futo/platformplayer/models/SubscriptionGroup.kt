package com.futo.platformplayer.models

import java.util.UUID

@kotlinx.serialization.Serializable
open class SubscriptionGroup {
    var id: String = UUID.randomUUID().toString();
    var name: String;
    var image: ImageVariable? = null;
    var urls: MutableList<String> = mutableListOf();
    var priority: Int = 99;

    constructor(name: String) {
        this.name = name;
    }
    constructor(parent: SubscriptionGroup) {
        this.id = parent.id;
        this.name = parent.name;
        this.image = parent.image;
        this.urls = parent.urls;
        this.priority = parent.priority;
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