package com.futo.platformplayer.sync.internal

enum class Opcode(val value: UByte) {
    PING(0u),
    PONG(1u),
    NOTIFY(2u),
    STREAM(3u),
    DATA(4u),
    REQUEST(5u),
    RESPONSE(6u),
    RELAY(7u)
}

enum class NotifyOpcode(val value: UByte) {
    AUTHORIZED(0u),
    UNAUTHORIZED(1u),
    CONNECTION_INFO(2u)
}

enum class StreamOpcode(val value: UByte) {
    START(0u),
    DATA(1u),
    END(2u)
}

enum class RequestOpcode(val value: UByte) {
    CONNECTION_INFO(0u),
    TRANSPORT(1u),
    TRANSPORT_RELAYED(2u),
    PUBLISH_RECORD(3u),
    DELETE_RECORD(4u),
    LIST_RECORD_KEYS(5u),
    GET_RECORD(6u),
    BULK_PUBLISH_RECORD(7u),
    BULK_GET_RECORD(8u),
    BULK_CONNECTION_INFO(9u),
    BULK_DELETE_RECORD(10u)
}

enum class ResponseOpcode(val value: UByte) {
    CONNECTION_INFO(0u),
    TRANSPORT(1u),
    TRANSPORT_RELAYED(2u), //TODO: Server errors also included in this one, disentangle?
    PUBLISH_RECORD(3u),
    DELETE_RECORD(4u),
    LIST_RECORD_KEYS(5u),
    GET_RECORD(6u),
    BULK_PUBLISH_RECORD(7u),
    BULK_GET_RECORD(8u),
    BULK_CONNECTION_INFO(9u),
    BULK_DELETE_RECORD(10u)
}

enum class RelayOpcode(val value: UByte) {
    DATA(0u),
    RELAYED_DATA(1u),
    ERROR(2u),
    RELAYED_ERROR(3u),
    RELAY_ERROR(4u)
}