package com.jetbrains.bsp.messages

data class MessageIssue(val text: String, val code: Int = 0, val cause: Exception? = null)