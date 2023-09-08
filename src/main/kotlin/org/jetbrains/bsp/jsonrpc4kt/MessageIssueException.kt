package org.jetbrains.jsonrpc4kt

class MessageIssueException(val issue: MessageIssue) : RuntimeException(issue.message)