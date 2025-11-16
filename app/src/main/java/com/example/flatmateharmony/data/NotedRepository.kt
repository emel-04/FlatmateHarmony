package com.example.flatmateharmony.data

import com.example.flatmateharmony.model.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class NotedRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    fun today(): String = dateFmt.format(Date())

    /** Tìm home theo code, trả về Pair(homeId, Home) nếu có (Home là data class trong package data của bạn) */
    suspend fun findHomeByCode(homeCode: String): Pair<String, Home>? {
        val snap = db.collection("homes").whereEqualTo("homeCode", homeCode).get().await()
        if (snap.isEmpty) return null
        val doc = snap.documents.first()
        val home = Home(
            address = doc.getString("address") ?: "",
            rent = doc.getLong("rent") ?: 0L,
            ownerId = doc.getString("ownerId") ?: "",
            homeCode = doc.getString("homeCode") ?: "",
            createdAt = doc.getLong("createdAt") ?: 0L
        )
        return doc.id to home
    }

    suspend fun getMembers(homeId: String): List<Member> {
        val snap = db.collection("homes").document(homeId)
            .collection("members").get().await()
        return snap.documents.map {
            Member(
                id = it.id,
                name = it.getString("name") ?: "",
                userId = it.getString("userId") ?: ""
            )
        }
    }

    suspend fun getAssignments(homeId: String): List<TaskAssignment> {
        val snap = db.collection("homes").document(homeId)
            .collection("tasks").get().await()
        return snap.documents.map { doc ->
            TaskAssignment(
                task = Task(
                    name = doc.getString("name") ?: "",
                    icon = doc.getString("icon") ?: ""
                ),
                member = Member(
                    name = doc.getString("assignedTo") ?: "",
                    userId = doc.getString("assignedUserId") ?: ""
                )
            )
        }
    }

    suspend fun replaceAssignments(homeId: String, assignments: List<TaskAssignment>) {
        val ref = db.collection("homes").document(homeId).collection("tasks")
        val old = ref.get().await()
        old.documents.forEach { it.reference.delete() }
        assignments.forEach { a ->
            ref.add(
                mapOf(
                    "name" to a.task.name,
                    "icon" to a.task.icon,
                    "assignedTo" to a.member.name,
                    "assignedUserId" to a.member.userId,
                    "status" to "Chờ",
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
        }
    }

    suspend fun getShoppingList(homeId: String): List<ShoppingItem> {
        val snap = db.collection("homes").document(homeId)
            .collection("shoppingList").get().await()
        return snap.documents.map {
            ShoppingItem(
                id = it.id,
                name = it.getString("name") ?: "",
                note = it.getString("note") ?: "",
                addedBy = it.getString("addedBy") ?: "",
                isBought = it.getBoolean("isBought") ?: false
            )
        }
    }

    suspend fun addMember(homeId: String, name: String): Member {
        val ref = db.collection("homes").document(homeId)
            .collection("members")
            .add(
                mapOf(
                    "name" to name,
                    "userId" to "",
                    "joinedAt" to System.currentTimeMillis()
                )
            ).await()
        return Member(id = ref.id, name = name, userId = "")
    }

    suspend fun deleteMember(homeId: String, memberId: String) {
        db.collection("homes").document(homeId)
            .collection("members").document(memberId)
            .delete().await()
    }

    suspend fun addShoppingItem(homeId: String, name: String, note: String, addedBy: String): ShoppingItem {
        val ref = db.collection("homes").document(homeId)
            .collection("shoppingList")
            .add(
                mapOf(
                    "name" to name,
                    "note" to note,
                    "addedBy" to addedBy,
                    "isBought" to false,
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
        return ShoppingItem(id = ref.id, name = name, note = note, addedBy = addedBy, isBought = false)
    }

    suspend fun toggleShoppingBought(homeId: String, item: ShoppingItem) {
        db.collection("homes").document(homeId)
            .collection("shoppingList").document(item.id)
            .update("isBought", !item.isBought).await()
    }

    suspend fun deleteShoppingItem(homeId: String, itemId: String) {
        db.collection("homes").document(homeId)
            .collection("shoppingList").document(itemId)
            .delete().await()
    }

    suspend fun getLastRandomDate(homeId: String): String {
        val doc = db.collection("homes").document(homeId).get().await()
        return doc.getString("lastRandomDate") ?: ""
    }

    suspend fun setLastRandomDate(homeId: String, date: String) {
        db.collection("homes").document(homeId).update("lastRandomDate", date).await()
    }

    /** Lấy lịch sử phân công (7 ngày gần nhất, loại bỏ hôm nay) */
    suspend fun getTaskHistory(homeId: String): List<TaskHistory> {
        val today = today()
        val snap = db.collection("homes").document(homeId)
            .collection("taskHistory")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(7)
            .get()
            .await()
        
        val histories = snap.documents.mapNotNull { doc ->
            val date = doc.getString("date") ?: return@mapNotNull null
            
            // Loại bỏ lịch sử của hôm nay
            if (date == today) return@mapNotNull null
            
            val timestamp = doc.getLong("timestamp") ?: 0L
            
            // Parse assignments
            val assignmentsData = doc.get("assignments") as? List<Map<String, Any>> ?: emptyList()
            val assignments = assignmentsData.mapNotNull { assignmentMap ->
                val taskMap = assignmentMap["task"] as? Map<String, Any>
                val memberMap = assignmentMap["member"] as? Map<String, Any>
                
                if (taskMap != null && memberMap != null) {
                    TaskAssignment(
                        task = Task(
                            name = taskMap["name"] as? String ?: "",
                            icon = taskMap["icon"] as? String ?: ""
                        ),
                        member = Member(
                            id = memberMap["id"] as? String ?: "",
                            name = memberMap["name"] as? String ?: "",
                            userId = memberMap["userId"] as? String ?: ""
                        )
                    )
                } else null
            }
            
            TaskHistory(
                id = doc.id,
                date = date,
                assignments = assignments,
                timestamp = timestamp
            )
        }

        // Loại bỏ các ngày bị trùng (giữ bản ghi mới nhất cho mỗi ngày)
        val uniqueHistories = mutableListOf<TaskHistory>()
        histories.forEach { history ->
            if (uniqueHistories.none { it.date == history.date }) {
                uniqueHistories.add(history)
            }
        }

        return uniqueHistories
    }
    
    /** Lưu lịch sử phân công khi randomize */
    suspend fun saveTaskHistory(homeId: String, assignments: List<TaskAssignment>) {
        val date = today()
        val timestamp = System.currentTimeMillis()
        
        val assignmentsData = assignments.map { assignment ->
            mapOf(
                "task" to mapOf(
                    "name" to assignment.task.name,
                    "icon" to assignment.task.icon
                ),
                "member" to mapOf(
                    "id" to assignment.member.id,
                    "name" to assignment.member.name,
                    "userId" to assignment.member.userId
                )
            )
        }
        
        db.collection("homes").document(homeId)
            .collection("taskHistory")
            .add(
                mapOf(
                    "date" to date,
                    "assignments" to assignmentsData,
                    "timestamp" to timestamp
                )
            ).await()
    }
}
