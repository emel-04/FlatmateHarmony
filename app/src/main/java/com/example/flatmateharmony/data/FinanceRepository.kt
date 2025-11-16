package com.example.flatmateharmony.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

data class Transaction(
    val id: String = "",
    val description: String = "",
    val amount: Long = 0L,
    val payerId: String = "",
    val createdAt: Long = 0L,
    val imageUrl: String = "" // URL ảnh đại diện cho giao dịch
)

class FinanceRepository {
    private val db = FirebaseFirestore.getInstance()

    // Lấy danh sách thành viên trong nhà dựa theo homeCode
    suspend fun getHomeMembers(homeCode: String): List<String> {
        val snap = db.collection("homes")
            .whereEqualTo("homeCode", homeCode)
            .limit(1)
            .get()
            .await()

        val doc = snap.documents.firstOrNull()
        if (doc == null || !doc.exists()) {
            return emptyList()
        }

        val members = doc.get("members")
        return (members as? List<*>)?.map { it.toString() } ?: emptyList()
    }

    // Lấy danh sách giao dịch theo tháng
    suspend fun getTransactionsForMonth(homeCode: String, year: Int, month: Int): List<Transaction> {
        // Tìm document tương ứng homeCode
        val homeSnap = db.collection("homes")
            .whereEqualTo("homeCode", homeCode)
            .limit(1)
            .get()
            .await()

        val homeDoc = homeSnap.documents.firstOrNull()
        if (homeDoc == null || !homeDoc.exists()) {
            return emptyList()
        }

        // Xác định khoảng thời gian trong tháng
        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val end = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month) // next month
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Lấy các giao dịch trong tháng
        val snap = homeDoc.reference.collection("transactions")
            .whereGreaterThanOrEqualTo("createdAt", start)
            .whereLessThan("createdAt", end)
            .get()
            .await()

        return snap.documents.map { d ->
            val data = d.data ?: emptyMap<String, Any>()
            Transaction(
                id = d.id,
                description = data["description"]?.toString() ?: "",
                amount = (data["amount"] as? Number)?.toLong() ?: 0L,
                payerId = data["payerId"]?.toString() ?: "",
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                imageUrl = data["imageUrl"]?.toString() ?: ""
            )
        }
    }

    // Tạo giao dịch mới
    suspend fun createTransaction(homeCode: String, description: String, amount: Long, payerId: String, imageUrl: String = "") {
        // Tìm document nhà tương ứng
        val homeSnap = db.collection("homes")
            .whereEqualTo("homeCode", homeCode)
            .limit(1)
            .get()
            .await()

        val homeDoc = homeSnap.documents.firstOrNull()
            ?: throw Exception("Không tìm thấy nhà có mã $homeCode")

        val txRef = homeDoc.reference.collection("transactions").document()
        txRef.set(
            mapOf(
                "description" to description,
                "amount" to amount,
                "payerId" to payerId,
                "createdAt" to System.currentTimeMillis(),  // Thêm thời gian tạo giao dịch
                "imageUrl" to imageUrl  // Lưu URL ảnh
            )
        ).await()
    }

    // Cập nhật giao dịch
    suspend fun updateTransaction(homeCode: String, transactionId: String, description: String, amount: Long, imageUrl: String = "") {
        val homeSnap = db.collection("homes")
            .whereEqualTo("homeCode", homeCode)
            .limit(1)
            .get()
            .await()

        val homeDoc = homeSnap.documents.firstOrNull()
            ?: throw Exception("Không tìm thấy nhà có mã $homeCode")

        val txRef = homeDoc.reference.collection("transactions").document(transactionId)
        txRef.update(
            mapOf(
                "description" to description,
                "amount" to amount,
                "imageUrl" to imageUrl
            )
        ).await()
    }

    // Xóa giao dịch
    suspend fun deleteTransaction(homeCode: String, transactionId: String) {
        val homeSnap = db.collection("homes")
            .whereEqualTo("homeCode", homeCode)
            .limit(1)
            .get()
            .await()

        val homeDoc = homeSnap.documents.firstOrNull()
            ?: throw Exception("Không tìm thấy nhà có mã $homeCode")

        val txRef = homeDoc.reference.collection("transactions").document(transactionId)
        txRef.delete().await()
    }
}
