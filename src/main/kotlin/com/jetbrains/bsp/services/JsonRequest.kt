package com.jetbrains.bsp.services

import java.util.concurrent.CompletableFuture

/**
 * Annotation to mark a request method on an interface or class.
 *
 *
 * A request method must have the return type [CompletableFuture] with an
 * object parameter type or Void and have zero or one argument.
 *
 *
 * According to jsonrpc an argument must be an 'object' (a java bean, not e,g.
 * String).
 *
 *
 * The name of the jsonrpc request will be the optional segment, followed by the
 * name of the Java method that is annotated with JsonRequest. The name of the
 * jsonrpc request can be customized by using the [.value] field of this
 * annotation. To specify the whole name, including the segment, in the value,
 * set [.useSegment] to false.
 *
 * @see JsonSegment
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.CLASS
)
annotation class JsonRequest(
    /**
     * The name of the the jsonrpc request method. If empty, uses the name of the
     * annotated method.
     */
    val value: String = "",
    /**
     * When using segments, useSegment will be true to prepend the segment name to
     * the name of the request.
     *
     * @see JsonSegment
     */
    val useSegment: Boolean = true
)
