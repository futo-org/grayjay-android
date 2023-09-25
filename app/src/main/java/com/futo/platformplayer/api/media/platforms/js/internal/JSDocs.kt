package com.futo.platformplayer.api.media.platforms.js.internal

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class JSDocs(val order: Int, val code: String, val description: String)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class JSOptional()

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class JSDocsParameter(val name: String, val description: String, val order: Int = 0)

@kotlinx.serialization.Serializable
data class JSCallDocs(val title: String, val code: String, val description: String, val parameters: List<JSParameterDocs>, val isOptional: Boolean = false);
@kotlinx.serialization.Serializable
data class JSParameterDocs(val name: String, val description: String);