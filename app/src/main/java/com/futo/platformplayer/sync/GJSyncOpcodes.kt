package com.futo.platformplayer.sync.internal

class GJSyncOpcodes {
    companion object {
        val sendToDevices: UByte = 101.toUByte();

        val syncStateExchange: UByte = 150.toUByte();

        val syncExport: UByte = 201.toUByte();

        val syncSubscriptions: UByte = 202.toUByte();

        val syncHistory: UByte = 203.toUByte();
        val syncSubscriptionGroups: UByte = 204.toUByte();
        val syncPlaylists: UByte = 205.toUByte();
        val syncWatchLater: UByte = 206.toUByte();
    }
}