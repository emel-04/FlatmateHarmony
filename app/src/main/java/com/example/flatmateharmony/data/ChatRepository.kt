package com.example.flatmateharmony.data

import com.example.flatmateharmony.model.ChatMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class ChatRepository {
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null   // ✅ Thêm biến giữ listener

    suspend fun getHomeIdByCode(homeCode: String): String? {
        val snap = db.collection("homes")
            .whereEqualTo("homeCode", homeCode)
            .limit(1)
            .get()
            .await()
        return snap.documents.firstOrNull()?.id
    }

    suspend fun getMemberName(homeId: String, userId: String): String? {
        val memberSnap = db.collection("homes")
            .document(homeId)
            .collection("members")
            .whereEqualTo("userId", userId)
            .get()
            .await()

        return memberSnap.documents.firstOrNull()?.getString("name")
    }

    suspend fun getAllMembers(homeId: String): Map<String, String> {
        val membersSnap = db.collection("homes")
            .document(homeId)
            .collection("members")
            .get()
            .await()

        return membersSnap.documents.associate {
            val userId = it.getString("userId") ?: ""
            val name = it.getString("name") ?: "Ẩn danh"
            userId to name
        }
    }

    suspend fun saveMemberName(homeId: String, userId: String, name: String) {
        val memberSnap = db.collection("homes")
            .document(homeId)
            .collection("members")
            .whereEqualTo("userId", userId)
            .get()
            .await()

        if (memberSnap.isEmpty) {
            db.collection("homes").document(homeId)
                .collection("members").add(
                    mapOf(
                        "userId" to userId,
                        "name" to name,
                        "joinedAt" to System.currentTimeMillis()
                    )
                ).await()
        } else {
            val docId = memberSnap.documents.first().id
            db.collection("homes").document(homeId)
                .collection("members").document(docId)
                .update("name", name)
                .await()
        }
    }

    suspend fun sendMessage(homeId: String, senderId: String, content: String) {
        db.collection("homes").document(homeId)
            .collection("messages")
            .add(
                mapOf(
                    "senderId" to senderId,
                    "content" to content,
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
    }

    fun listenForMessages(homeId: String, onMessages: (List<ChatMessage>) -> Unit) {
    val threeDaysAgo = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000

    // Lọc 3 ngày gần đây + sắp xếp TĂNG DẦN => cũ lên trên, mới xuống dưới
    listener = db.collection("homes").document(homeId)
        .collection("messages")
        .whereGreaterThanOrEqualTo("timestamp", threeDaysAgo)
        .orderBy("timestamp", Query.Direction.ASCENDING)
        .limit(200)
        .addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener

            val msgs = snapshot?.documents?.mapNotNull { doc ->
                val ts = doc.getLong("timestamp") ?: return@mapNotNull null
                ChatMessage(
                    id = doc.id,
                    content = doc.getString("content") ?: "",
                    senderId = doc.getString("senderId") ?: "",
                    senderName = "", // ViewModel sẽ map
                    timestamp = ts
                )
            } ?: emptyList()

            onMessages(msgs)
        }
}

    // ✅ Hàm hủy listener khi rời màn hình
    fun stopListening() {
        listener?.remove()
        listener = null
    }

    /// ✅ Lấy thông tin nhà
    suspend fun getHomeInfo(homeId: String): Home? {
    val doc = db.collection("homes").document(homeId).get().await()
    return if (doc.exists()) {
        Home(
            address = doc.getString("address") ?: "",
            rent = doc.getLong("rent") ?: 0L,
            ownerId = doc.getString("ownerId") ?: "",
            homeCode = doc.getString("homeCode") ?: "",
            createdAt = doc.getLong("createdAt") ?: 0L
        )
    } else null
}
}
