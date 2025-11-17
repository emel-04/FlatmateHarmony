package com.example.flatmateharmony.model

data class ChatMessage(
    val id: String = "",
    val content: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val timestamp: Long = 0L
)
