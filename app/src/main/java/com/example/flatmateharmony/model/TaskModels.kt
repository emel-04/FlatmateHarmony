package com.example.flatmateharmony.model

/** Toàn bộ model liên quan đến Task/Shopping/Member gộp vào 1 file */

data class Member(
    val id: String = "",
    val name: String = "",
    val userId: String = ""
)

data class Task(
    val name: String = "",
    val icon: String = ""
)

data class TaskAssignment(
    val task: Task,
    val member: Member
)

data class ShoppingItem(
    val id: String = "",
    val name: String = "",
    val note: String = "",
    val addedBy: String = "",
    val isBought: Boolean = false
)

data class TaskHistory(
    val id: String = "",
    val date: String = "", // Format: yyyy-MM-dd
    val assignments: List<TaskAssignment> = emptyList(),
    val timestamp: Long = 0L
)