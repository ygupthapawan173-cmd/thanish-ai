package com.example.data

import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatMessageDao: ChatMessageDao) {
    val messages: Flow<List<ChatMessageEntity>> = chatMessageDao.observeAllMessages()

    suspend fun saveUserMessage(text: String, isVoiceInput: Boolean): Long {
        return chatMessageDao.insertMessage(
            ChatMessageEntity(
                role = ChatMessageEntity.ROLE_USER,
                text = text,
                isVoiceInput = isVoiceInput
            )
        )
    }

    suspend fun saveAssistantMessage(text: String): Long {
        return chatMessageDao.insertMessage(
            ChatMessageEntity(
                role = ChatMessageEntity.ROLE_ASSISTANT,
                text = text,
                isVoiceInput = false
            )
        )
    }

    suspend fun clearChat() {
        chatMessageDao.clearAllMessages()
    }
}
