package com.example.flatmateharmony.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flatmateharmony.data.Home
import com.example.flatmateharmony.data.NotedRepository
import com.example.flatmateharmony.model.Member
import com.example.flatmateharmony.model.ShoppingItem
import com.example.flatmateharmony.model.Task
import com.example.flatmateharmony.model.TaskAssignment
import com.example.flatmateharmony.model.TaskHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class NotedUiState(
    val isLoading: Boolean = true,
    val homeId: String = "",
    val home: Home? = null,
    val canRandomize: Boolean = true,
    val members: List<Member> = emptyList(),
    val assignments: List<TaskAssignment> = emptyList(),
    val shoppingList: List<ShoppingItem> = emptyList(),
    val taskHistory: List<TaskHistory> = emptyList()
)

class NotedViewModel(
    private val repo: NotedRepository = NotedRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotedUiState())
    val uiState: StateFlow<NotedUiState> = _uiState

    // Task mặc định
    private val defaultTasks = listOf(
        Task("Dọn nhà", "🧹"),
        Task("Nấu ăn", "👨‍🍳"),
        Task("Đi chợ", "🛒"),
        Task("Phơi đồ", "👔")
    )

    fun load(homeCode: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val homePair = repo.findHomeByCode(homeCode)
            if (homePair == null) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }
            val (homeId, home) = homePair
            val canRandomize = repo.getLastRandomDate(homeId) != repo.today()

            val members = repo.getMembers(homeId)
            val allAssignments = repo.getAssignments(homeId)
            // Lọc để chỉ lấy 4 công việc duy nhất (không trùng tên)
            val uniqueAssignments = allAssignments
                .distinctBy { it.task.name }
                .take(4)
            val shopping = repo.getShoppingList(homeId)
            val history = repo.getTaskHistory(homeId)

            _uiState.value = NotedUiState(
                isLoading = false,
                homeId = homeId,
                home = home,
                canRandomize = canRandomize,
                members = members,
                assignments = uniqueAssignments,
                shoppingList = shopping,
                taskHistory = history
            )
        }
    }

    fun randomizeAssignments() {
        val s = _uiState.value
        if (!s.canRandomize || s.members.isEmpty() || s.homeId.isEmpty()) return

        viewModelScope.launch {
            val shuffled = s.members.shuffled()
            // Đảm bảo chỉ tạo đúng 4 công việc (không lặp)
            val newAssignments = defaultTasks.take(4).mapIndexed { i, t ->
                TaskAssignment(task = t, member = shuffled[i % shuffled.size])
            }
            repo.replaceAssignments(s.homeId, newAssignments)
            repo.setLastRandomDate(s.homeId, repo.today())
            
            // Lưu lịch sử
            repo.saveTaskHistory(s.homeId, newAssignments)
            
            // Reload history (sẽ tự động loại bỏ hôm nay)
            val updatedHistory = repo.getTaskHistory(s.homeId)

            _uiState.value = _uiState.value.copy(
                assignments = newAssignments,
                canRandomize = false,
                taskHistory = updatedHistory
            )
        }
    }

    fun addMember(name: String) {
        val homeId = _uiState.value.homeId
        if (homeId.isEmpty()) return

        viewModelScope.launch {
            val created = repo.addMember(homeId, name)
            _uiState.value = _uiState.value.copy(
                members = _uiState.value.members + created
            )
        }
    }

    fun deleteMember(member: Member) {
        val homeId = _uiState.value.homeId
        if (homeId.isEmpty() || member.id.isEmpty()) return

        viewModelScope.launch {
            repo.deleteMember(homeId, member.id)
            _uiState.value = _uiState.value.copy(
                members = _uiState.value.members.filter { it.id != member.id }
            )
        }
    }

    fun addShoppingItem(name: String, note: String, addedBy: String) {
        val homeId = _uiState.value.homeId
        if (homeId.isEmpty()) return

        viewModelScope.launch {
            val item = repo.addShoppingItem(homeId, name, note, addedBy)
            _uiState.value = _uiState.value.copy(
                shoppingList = _uiState.value.shoppingList + item
            )
        }
    }

    fun toggleShoppingBought(item: ShoppingItem) {
        val homeId = _uiState.value.homeId
        if (homeId.isEmpty() || item.id.isEmpty()) return

        viewModelScope.launch {
            repo.toggleShoppingBought(homeId, item)
            _uiState.value = _uiState.value.copy(
                shoppingList = _uiState.value.shoppingList.map {
                    if (it.id == item.id) it.copy(isBought = !it.isBought) else it
                }
            )
        }
    }

    fun deleteShoppingItem(item: ShoppingItem) {
        val homeId = _uiState.value.homeId
        if (homeId.isEmpty() || item.id.isEmpty()) return

        viewModelScope.launch {
            repo.deleteShoppingItem(homeId, item.id)
            _uiState.value = _uiState.value.copy(
                shoppingList = _uiState.value.shoppingList.filter { it.id != item.id }
            )
        }
    }
}
