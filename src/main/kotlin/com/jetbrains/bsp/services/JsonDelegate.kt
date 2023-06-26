package com.jetbrains.bsp.services

/**
 * A method annotated with [JsonDelegate] is treated as a delegate method.
 * As a result jsonrpc methods of the delegate will be considered, too.
 * If an annotated method returns `null`
 * then jsonrpc methods of the delegate are not considered.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class JsonDelegate
