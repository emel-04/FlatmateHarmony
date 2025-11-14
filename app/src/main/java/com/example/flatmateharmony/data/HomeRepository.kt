package com.example.flatmateharmony.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class Home(
    val address: String = "",
    val rent: Long = 0,
    val ownerId: String = "",
    val homeCode: String = "",
    val createdAt: Long = 0L
)

class HomeRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getHomeInfoByUser(userId: String): Home? {
        val homeQuery = db.collection("homes")
            .whereArrayContains("members", userId)
            .get()
            .await()

        if (!homeQuery.isEmpty) {
            val homeDoc = homeQuery.documents.first()
            return Home(
                address = homeDoc.getString("address") ?: "",
                rent = homeDoc.getLong("rent") ?: 0,
                ownerId = homeDoc.getString("ownerId") ?: "",
                homeCode = homeDoc.getString("homeCode") ?: "",
                createdAt = homeDoc.getLong("createdAt") ?: 0L
            )
        }
        return null
    }
}
