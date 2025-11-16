package com.example.flatmateharmony.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flatmateharmony.data.FinanceRepository
import com.example.flatmateharmony.data.Transaction
import com.example.flatmateharmony.model.Settlement
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.ceil
import kotlin.math.floor

data class FinanceUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val totalAmount: Long = 0L,
    val perMemberShare: Long = 0L,
    val members: List<String> = emptyList(),
    val perMemberPaid: Map<String, Long> = emptyMap(),
    val balances: Map<String, Long> = emptyMap(),
    val suggestedSettlements: List<Settlement> = emptyList(),
    val transactions: List<Transaction> = emptyList()
)

class FinanceViewModel(
    private val homeCode: String,
    private val repo: FinanceRepository = FinanceRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinanceUiState())
    val uiState: StateFlow<FinanceUiState> = _uiState

    private val db = FirebaseFirestore.getInstance()

    init {
        refreshForCurrentMonth()
    }

    fun refreshForCurrentMonth() {
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH) + 1
        refresh(homeCode, year, month)
    }

    fun refresh(homeCode: String, year: Int, month: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                // --- Lấy danh sách thành viên ---
                val members = repo.getHomeMembers(homeCode)
                if (members.isEmpty()) {
                    _uiState.value = FinanceUiState(
                        loading = false,
                        error = "Không có thành viên nào trong nhà!"
                    )
                    return@launch
                }

                // --- Lấy giao dịch ---
                val txs = repo.getTransactionsForMonth(homeCode, year, month)
                val total = txs.sumOf { it.amount }

                // Tính chia trung bình chính xác (tránh lệch do làm tròn)
                val avgExact = total.toDouble() / members.size
                val shares = members.mapIndexed { index, _ ->
                    if (index == 0) ceil(avgExact).toLong() // Người đầu tiên chịu phần dư
                    else floor(avgExact).toLong()
                }

                val perMemberMap = members.zip(shares).toMap()
                val perMemberShare = shares.average().toLong() // dùng để hiển thị chung

                // Tính tiền mỗi người đã chi
                val paidMap = members.associateWith { 0L }.toMutableMap()
                txs.forEach { t ->
                    if (t.payerId.isNotEmpty()) {
                        paidMap[t.payerId] = (paidMap[t.payerId] ?: 0L) + t.amount
                    }
                }

                // Tính số dư (đã trả - phần phải trả)
                val balances = members.associateWith { m ->
                    (paidMap[m] ?: 0L) - (perMemberMap[m] ?: 0L)
                }.toMutableMap()

                // Lấy các khoản thanh toán trong tháng
                val start = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, month - 1)
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis

                val end = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, month)
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis

                val paymentsSnap = db.collection("homes")
                    .document(homeCode)
                    .collection("payments")
                    .whereGreaterThanOrEqualTo("timestamp", start)
                    .whereLessThan("timestamp", end)
                    .get()
                    .await()

                // Cập nhật balances theo thanh toán
                for (doc in paymentsSnap.documents) {
                    val from = doc.getString("from") ?: continue
                    val to = doc.getString("to") ?: continue
                    val amount = (doc.getLong("amount") ?: 0L)
                    balances[from] = (balances[from] ?: 0L) + amount
                    balances[to] = (balances[to] ?: 0L) - amount
                }

                // Gợi ý thanh toán (settlement)
                val creditors = balances.filterValues { it > 0 }.toMutableMap()
                val debtors = balances.filterValues { it < 0 }.mapValues { -it.value }.toMutableMap()

                val settlements = mutableListOf<Settlement>()
                for ((debtor, oweAmount) in debtors) {
                    var remainingDebt = oweAmount
                    for ((creditor, owedAmount) in creditors.toMap()) {
                        if (remainingDebt <= 0) break
                        if (owedAmount <= 0) continue
                        val pay = minOf(remainingDebt, owedAmount)
                        settlements.add(Settlement(from = debtor, to = creditor, amount = pay))
                        remainingDebt -= pay
                        creditors[creditor] = owedAmount - pay
                    }
                }

                // Cập nhật UI state
                _uiState.value = FinanceUiState(
                    loading = false,
                    totalAmount = total,
                    perMemberShare = perMemberShare,
                    members = members,
                    perMemberPaid = paidMap,
                    balances = balances,
                    suggestedSettlements = settlements,
                    transactions = txs
                )

            } catch (ex: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = ex.message ?: "Lỗi không xác định"
                )
                Log.e("FinanceViewModel", "Error in refresh", ex)
            }
        }
    }

    // Tạo giao dịch
    fun createTransaction(
        description: String,
        amount: Long,
        payerId: String,
        imageUrl: String = "",
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repo.createTransaction(homeCode, description, amount, payerId, imageUrl)
                refreshForCurrentMonth()
                onComplete(true, null)
            } catch (ex: Exception) {
                onComplete(false, ex.message)
                Log.e("FinanceViewModel", "Error creating transaction", ex)
            }
        }
    }

    // Cập nhật giao dịch
    fun updateTransaction(
        transactionId: String,
        description: String,
        amount: Long,
        imageUrl: String = "",
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repo.updateTransaction(homeCode, transactionId, description, amount, imageUrl)
                refreshForCurrentMonth()
                onComplete(true, null)
            } catch (ex: Exception) {
                onComplete(false, ex.message)
                Log.e("FinanceViewModel", "Error updating transaction", ex)
            }
        }
    }

    // Xóa giao dịch
    fun deleteTransaction(
        transactionId: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repo.deleteTransaction(homeCode, transactionId)
                refreshForCurrentMonth()
                onComplete(true, null)
            } catch (ex: Exception) {
                onComplete(false, ex.message)
                Log.e("FinanceViewModel", "Error deleting transaction", ex)
            }
        }
    }

    // ✅ Khi người nợ bấm xác nhận thanh toán
    fun confirmPayment(settlement: Settlement, onComplete: (Boolean) -> Unit) {
    viewModelScope.launch {
        try {
            val current = _uiState.value
            val newBalances = current.balances.toMutableMap()

            // Cập nhật số dư mới cho người nợ (từ) và người nhận (tới)
            newBalances[settlement.from] =
                (newBalances[settlement.from] ?: 0L) + settlement.amount
            newBalances[settlement.to] =
                (newBalances[settlement.to] ?: 0L) - settlement.amount

            val homeRef = db.collection("homes").document(homeCode)
            val paymentRef = homeRef.collection("payments").document()

            // Kiểm tra xem có document của nhà không, nếu không sẽ tạo một document với các balances ban đầu
            val homeSnap = homeRef.get().await()
            if (!homeSnap.exists()) {
                val initialBalances = mapOf(
                    settlement.from to 0L,
                    settlement.to to 0L
                )
                homeRef.set(mapOf("balances" to initialBalances)).await()
            }

            // Dùng batch để cập nhật giao dịch và số dư trong một lần
            db.runBatch { batch ->
                // Tạo một giao dịch thanh toán mới trong collection "payments"
                batch.set(paymentRef, mapOf(
                    "from" to settlement.from,
                    "to" to settlement.to,
                    "amount" to settlement.amount,
                    "timestamp" to System.currentTimeMillis() // Thêm thời gian giao dịch
                ))

                // Cập nhật số dư cho người trả và người nhận trong "balances"
                batch.update(homeRef, "balances.${settlement.from}", newBalances[settlement.from])
                batch.update(homeRef, "balances.${settlement.to}", newBalances[settlement.to])
            }.await()

            // Làm mới dữ liệu tài chính cho tháng hiện tại
            refreshForCurrentMonth()
            onComplete(true) // Thành công

        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(false) // Thất bại
            }
        }
    }
}
