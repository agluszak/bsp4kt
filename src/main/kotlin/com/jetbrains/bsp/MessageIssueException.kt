package com.jetbrains.bsp

class MessageIssueException(val issue: MessageIssue) : RuntimeException(issue.message)