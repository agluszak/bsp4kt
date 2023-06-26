package com.jetbrains.bsp

import com.jetbrains.bsp.messages.Message
import com.jetbrains.bsp.messages.MessageIssue
import java.util.*
import java.util.stream.Collectors

/**
 * An exception thrown to notify the caller of a [MessageConsumer] that one or more issues were
 * found while parsing or validating a message. This information can be passed to a [MessageIssueHandler]
 * in order to construct a proper response.
 */
class MessageIssueException(val rpcMessage: Message, val issues: List<MessageIssue>) : RuntimeException() {
    override val message: String?
        get() = issues.stream().map {it.text }.collect(Collectors.joining("\n"))


}
