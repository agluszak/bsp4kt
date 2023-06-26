package com.jetbrains.bsp.services

/**
 * Use on a class or interface to prefix all declared [JsonRequest] and [JsonNotification] methods.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class JsonSegment(val value: String = "")
