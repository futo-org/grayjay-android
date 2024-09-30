package com.futo.platformplayer.sync.internal

class GJSyncOpcodes {
    companion object {
        val sendToDevices: UByte = 101.toUByte();

        val syncExport: UByte = 201.toUByte();
        val syncSubscriptions: UByte = 202.toUByte();
        val syncHistory: UByte = 203.toUByte();
    }
}