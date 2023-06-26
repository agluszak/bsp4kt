package com.jetbrains.bsp

import com.jetbrains.bsp.messages.Message
import com.jetbrains.bsp.messages.MessageIssue


interface MessageIssueHandler {
    /**
     * Handle issues found while parsing or validating a message. The list of issues must not be empty.
     */
    fun handle(message: Message, issues: List<MessageIssue>)
}
