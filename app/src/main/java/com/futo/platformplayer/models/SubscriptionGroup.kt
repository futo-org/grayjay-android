package com.futo.platformplayer.models

import java.util.UUID

@kotlinx.serialization.Serializable
class SubscriptionGroup {
    val id: String = UUID.randomUUID().toString();
    var name: String;
    var image: ImageVariable? = null;
    var urls: MutableList<String> = mutableListOf();

    constructor(name: String) {
        this.name = name;
    }
}