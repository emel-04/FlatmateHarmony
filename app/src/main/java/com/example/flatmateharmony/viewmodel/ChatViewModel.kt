package com.example.flatmateharmony.viewmodel

import com.example.flatmateharmony.data.Home
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flatmateharmony.data.ChatRepository
import com.example.flatmateharmony.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val currentUserName: String = "",
    val homeId: String = "",
    val home: Home? = null,
    val isLoading: Boolean = true
)

class ChatViewModel(
    private val repo: ChatRepository = ChatRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState
    private var membersCache: Map<String, String> = emptyMap()

    /**
     * 🔹 Load dữ liệu chat theo homeCode & userId
     */
    fun loadChat(homeCode: String, currentUserId: String) {
        viewModelScope.launch {
        val homeId = repo.getHomeIdByCode(homeCode) ?: return@launch

        // ✅ Lấy dữ liệu home từ Firestore
        val homeData = repo.getHomeInfo(homeId)
        val name = repo.getMemberName(homeId, currentUserId) ?: ""
        
        // Cache tất cả members để tránh query nhiều lần
        membersCache = repo.getAllMembers(homeId)

        _uiState.value = _uiState.value.copy(
            homeId = homeId,
            currentUserName = name,
            home = homeData,
            isLoading = false
        )

        repo.listenForMessages(homeId) { msgs ->
            // Map senderName cho mỗi message từ cache
            val messagesWithNames = msgs.map { msg ->
                val senderName = membersCache[msg.senderId] ?: "Ẩn danh"
                msg.copy(senderName = senderName)
            }
            _uiState.value = _uiState.value.copy(messages = messagesWithNames)
        }
    }
}

    /**
     * 🔹 Gửi tin nhắn
     */
    fun sendMessage(content: String, senderId: String) {
        val homeId = _uiState.value.homeId
        if (homeId.isNotEmpty() && content.isNotBlank()) {
            viewModelScope.launch {
                repo.sendMessage(homeId, senderId, content)
            }
        }
    }

    /**
     * 🔹 Lưu hoặc cập nhật tên thành viên
     */
    fun saveName(name: String, userId: String) {
        val homeId = _uiState.value.homeId
        if (homeId.isNotEmpty()) {
            viewModelScope.launch {
                repo.saveMemberName(homeId, userId, name)
                _uiState.value = _uiState.value.copy(currentUserName = name)
            }
        }
    }

    /**
     * 🔹 Hủy listener Firestore khi ViewModel bị huỷ (ngăn leak dữ liệu)
     */
    override fun onCleared() {
        super.onCleared()
        repo.stopListening()
    }
}
