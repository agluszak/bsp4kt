package org.jetbrains.jsonrpc4kt.messages

/**
 * A number indicating the error type that occurred.
 * [-32099..-32000]: JSON-RPC reserved error codes
 * [-32899..-32800]: LSP reserved error codes
 */
enum class ResponseErrorCode(val code: Int) {


    /**
     * Invalid JSON was received by the server. An error occurred on
     * the server while parsing the JSON text.
     */
    ParseError(-32700),

    /**
     * The JSON sent is not a valid Request object.
     */
    InvalidRequest(-32600),

    /**
     * The method does not exist / is not available.
     */
    MethodNotFound(-32601),

    /**
     * Invalid method parameter(s).
     */
    InvalidParams(-32602),

    /**
     * Internal JSON-RPC error.
     */
    InternalError(-32603),

    /**
     * Error code indicating that a server received a notification or
     * request before the server has received the `initialize` request.
     */
    ServerNotInitialized(-32002),
    UnknownErrorCode(-32001),


    /**
     * A request failed but it was syntactically correct, e.g the
     * method name was known and the parameters were valid. The error
     * message should contain human readable information about why
     * the request failed.
     *
     *
     * Since 3.17.0
     */
    RequestFailed(-32803),

    /**
     * The server cancelled the request. This error code should
     * only be used for requests that explicitly support being
     * server cancellable.
     *
     *
     * Since 3.17.0
     */
    ServerCancelled(-32802),

    /**
     * The server detected that the content of a document got
     * modified outside normal conditions. A server should
     * NOT send this error code if it detects a content change
     * in it unprocessed messages. The result even computed
     * on an older state might still be useful for the client.
     *
     *
     * If a client decides that a result is not of any use anymore
     * the client should cancel the request.
     */
    ContentModified(-32801),

    /**
     * The client has canceled a request and a server as detected
     * the cancel.
     */
    RequestCancelled(-32800),

}
