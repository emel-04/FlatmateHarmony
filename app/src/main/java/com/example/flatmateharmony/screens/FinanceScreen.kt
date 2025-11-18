package com.example.flatmateharmony.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.example.flatmateharmony.data.Home
import com.example.flatmateharmony.model.Settlement
import com.example.flatmateharmony.navigation.AppHeader
import com.example.flatmateharmony.navigation.AppNavigationBar
import com.example.flatmateharmony.viewmodel.FinanceViewModel
import com.example.flatmateharmony.viewmodel.FinanceViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.flatmateharmony.utils.formatCurrency
import com.example.flatmateharmony.utils.parseCurrency
import com.example.flatmateharmony.utils.rememberCurrencyVisualTransformation
import com.example.flatmateharmony.utils.uriToBase64
import com.example.flatmateharmony.utils.base64ToBitmap
import java.text.NumberFormat
import java.util.*
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import android.util.Log


/**
 * Helper function for currency formatting - Using VND format with dot separators
 */
private fun formatCurrencyDisplay(amount: Long): String {
    return "${formatCurrency(amount)}đ"
}

enum class TimeFilter {
    ALL, TODAY, THIS_WEEK, THIS_MONTH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(navController: NavController, homeCode: String) {
    val factory = FinanceViewModelFactory(homeCode)
    val vm: FinanceViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
    val state by vm.uiState.collectAsState()

    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val uid = currentUser?.uid
    val context = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedSettlement by remember { mutableStateOf<Settlement?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }
    var tempNameInput by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var userNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var currentUserName by remember { mutableStateOf("") }
    var homeId by remember { mutableStateOf("") }

    val homeInfo = remember { mutableStateOf<Home?>(null) }

    var showAllTransactions by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTimeFilter by remember { mutableStateOf(TimeFilter.ALL) }
    var selectedTransactionForEdit by remember { mutableStateOf<com.example.flatmateharmony.data.Transaction?>(null) }
    var selectedTransactionForDelete by remember { mutableStateOf<com.example.flatmateharmony.data.Transaction?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }


    // ===== Helper functions =====
    suspend fun checkAndGetCurrentUserName(): Boolean {
        return try {
            val homeDocs = db.collection("homes")
                .whereEqualTo("homeCode", homeCode)
                .get()
                .await()
            if (homeDocs.documents.isNotEmpty()) {
                homeId = homeDocs.documents.first().id
                val userDoc = db.collection("homes")
                    .document(homeId)
                    .collection("members")
                    .whereEqualTo("userId", uid)
                    .get()
                    .await()
                if (userDoc.documents.isNotEmpty()) {
                    val existingName = userDoc.documents.first().getString("name")
                    if (!existingName.isNullOrBlank()) {
                        currentUserName = existingName
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) { false }
    }

    suspend fun saveCurrentUserName(name: String): Boolean {
        return try {
            val userDoc = db.collection("homes")
                .document(homeId)
                .collection("members")
                .whereEqualTo("userId", uid)
                .get()
                .await()
            if (userDoc.documents.isNotEmpty()) {
                db.collection("homes")
                    .document(homeId)
                    .collection("members")
                    .document(userDoc.documents.first().id)
                    .update("name", name)
                    .await()
            } else {
                db.collection("homes")
                    .document(homeId)
                    .collection("members")
                    .add(
                        mapOf(
                            "userId" to uid,
                            "name" to name,
                            "joinedAt" to System.currentTimeMillis()
                        )
                    )
                    .await()
            }
            currentUserName = name
            true
        } catch (_: Exception) { false }
    }

    // ===== Load Home + user =====
    LaunchedEffect(homeCode) {
        checkAndGetCurrentUserName()
        try {
            val docs = db.collection("homes")
                .whereEqualTo("homeCode", homeCode)
                .get()
                .await()
            if (docs.documents.isNotEmpty()) {
                val doc = docs.documents.first()
                homeId = doc.id
                homeInfo.value = Home(
                    homeCode = doc.getString("homeCode") ?: "",
                    address = doc.getString("address") ?: "",
                    rent = (doc.getLong("rent") ?: 0L),
                    ownerId = doc.getString("ownerId") ?: "",
                    createdAt = doc.getLong("createdAt") ?: 0L
                )
                val membersSnapshot = db.collection("homes")
                    .document(homeId)
                    .collection("members")
                    .get()
                    .await()
                userNames = membersSnapshot.documents.associate { d ->
                    (d.getString("userId") ?: "") to (d.getString("name") ?: "Ẩn danh")
                }
                uid?.let { u -> userNames[u]?.let { currentUserName = it } }
            }
        } catch (_: Exception) {}
    }

    // ===== UI =====
    Scaffold(
        topBar = {
            AppHeader(homeInfo.value)
        },
        bottomBar = { AppNavigationBar(navController, homeInfo.value) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (currentUserName.isBlank()) showNameDialog = true
                    else showCreateDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Thêm giao dịch")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                ErrorCard(message = state.error!!)
            } else {
                // === Tổng chi tiêu với gradient card ===
                TotalExpenseCard(
                    totalAmount = state.totalAmount,
                    youBalance = state.balances[uid] ?: 0L
                )

                Spacer(modifier = Modifier.height(24.dp))

                // === Tìm kiếm và Lọc ===
                SearchAndFilterSection(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    selectedFilter = selectedTimeFilter,
                    onFilterChange = { selectedTimeFilter = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // === Giao dịch gần đây ===
                val filteredTransactions = remember(state.transactions, searchQuery, selectedTimeFilter) {
                    filterTransactions(state.transactions, searchQuery, selectedTimeFilter)
                }
                
                TransactionsSection(
                    transactions = filteredTransactions,
                    showAll = showAllTransactions,
                    onToggleShowAll = { showAllTransactions = !showAllTransactions },
                    uid = uid,
                    currentUserName = currentUserName,
                    userNames = userNames,
                    onEditTransaction = { transaction ->
                        selectedTransactionForEdit = transaction
                        showEditDialog = true
                    },
                    onDeleteTransaction = { transaction ->
                        selectedTransactionForDelete = transaction
                        showDeleteDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // === Nợ hiện tại ===
                val myDebts = state.suggestedSettlements.filter { it.from == uid && it.amount > 0 }
                if (myDebts.isNotEmpty()) {
                    DebtsSection(
                        debts = myDebts,
                        userNames = userNames,
                        onDebtClick = { selectedSettlement = it }
                    )
                }

                Spacer(modifier = Modifier.height(50.dp)) // khoang cach
            }
        }
    }

    // === Dialog nhập tên ===
    if (showNameDialog) {
        NameInputDialog(
            tempNameInput = tempNameInput,
            currentUserName = currentUserName, // Thêm currentUserName để không bị lỗi build trong NameInputDialog
            onNameChange = { tempNameInput = it },
            onDismiss = { showNameDialog = false }, // Thêm onDismiss: set state về false
            onConfirm = {
                if (tempNameInput.isNotBlank()) {
                    coroutineScope.launch {
                        val success = saveCurrentUserName(tempNameInput.trim())
                        if (success) {
                            showNameDialog = false
                            tempNameInput = ""
                            showCreateDialog = true
                        } else {
                            // Handle failure if needed
                        }
                    }
                }
            }
        )
    }

    // === Dialog tạo giao dịch ===
    if (showCreateDialog) {
        CreateTransactionDialog(
            currentUserName = currentUserName,
            onDismiss = { showCreateDialog = false },
            onConfirm = { desc: String, amtStr: String, imageUri: Uri? ->
                val amt = amtStr.toLongOrNull() ?: 0L
                val uidNow = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                if (amt > 0 && uidNow.isNotEmpty()) {
                    coroutineScope.launch {
                        var imageBase64 = ""
                        var convertSuccess = true
                        
                        // Chuyển ảnh sang Base64 nếu có
                        if (imageUri != null) {
                            try {
                                Log.d("FinanceScreen", "Bắt đầu chuyển đổi ảnh sang Base64: $imageUri")
                                
                                // Chuyển Uri sang Base64 (ảnh sẽ được nén và resize tự động)
                                imageBase64 = uriToBase64(context, imageUri) ?: ""
                                
                                if (imageBase64.isNotEmpty()) {
                                    Log.d("FinanceScreen", "Chuyển đổi ảnh thành công! Kích thước: ${imageBase64.length} ký tự")
                                } else {
                                    convertSuccess = false
                                    Log.e("FinanceScreen", "Chuyển đổi ảnh thất bại")
                                }
                            } catch (e: Exception) {
                                Log.e("FinanceScreen", "Lỗi khi chuyển đổi ảnh", e)
                                convertSuccess = false
                                snackbarHostState.showSnackbar("⚠️ Không thể xử lý ảnh: ${e.message}")
                            }
                        }
                        
                        // Tạo giao dịch (có hoặc không có ảnh)
                        // Lưu Base64 vào trường imageUrl
                        vm.createTransaction(desc, amt, uidNow, imageBase64) { success, _ ->
                            if (success) {
                                coroutineScope.launch {
                                    if (imageUri != null && convertSuccess && imageBase64.isNotEmpty()) {
                                        snackbarHostState.showSnackbar("✅ Tạo giao dịch kèm ảnh thành công!")
                                    } else if (imageUri != null && !convertSuccess) {
                                        snackbarHostState.showSnackbar("✅ Tạo giao dịch thành công (không có ảnh)")
                                    } else {
                                        snackbarHostState.showSnackbar("✅ Tạo giao dịch thành công!")
                                    }
                                }
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("❌ Lỗi khi tạo giao dịch")
                                }
                            }
                            showCreateDialog = false
                        }
                    }
                } else showCreateDialog = false
            }
        )
    }

    // === Dialog xác nhận thanh toán ===
    if (selectedSettlement != null) {
        val settlement = selectedSettlement!!
        val toName = userNames[settlement.to] ?: "Ẩn danh"
        ConfirmPaymentDialog(
            amount = settlement.amount,
            toName = toName,
            onDismiss = { selectedSettlement = null },
            onConfirm = {
                vm.confirmPayment(settlement) { success ->
                    coroutineScope.launch {
                        if (success) snackbarHostState.showSnackbar("✅ Thanh toán thành công!")
                        else snackbarHostState.showSnackbar("❌ Thanh toán thất bại, thử lại sau.")
                    }
                    selectedSettlement = null
                }
            }
        )
    }

    // === Dialog chỉnh sửa giao dịch ===
    if (showEditDialog && selectedTransactionForEdit != null) {
        EditTransactionDialog(
            transaction = selectedTransactionForEdit!!,
            currentUserName = currentUserName,
            onDismiss = { 
                showEditDialog = false
                selectedTransactionForEdit = null
            },
            onConfirm = { desc: String, amtStr: String, imageUri: Uri? ->
                val amt = amtStr.toLongOrNull() ?: 0L
                if (amt > 0) {
                    coroutineScope.launch {
                        var imageBase64 = selectedTransactionForEdit!!.imageUrl
                        var convertSuccess = true
                        
                        if (imageUri != null) {
                            try {
                                imageBase64 = uriToBase64(context, imageUri) ?: selectedTransactionForEdit!!.imageUrl
                                if (imageBase64.isEmpty()) {
                                    convertSuccess = false
                                }
                            } catch (e: Exception) {
                                convertSuccess = false
                                snackbarHostState.showSnackbar("⚠️ Không thể xử lý ảnh: ${e.message}")
                            }
                        }
                        
                        vm.updateTransaction(
                            selectedTransactionForEdit!!.id,
                            desc,
                            amt,
                            imageBase64
                        ) { success, error ->
                            coroutineScope.launch {
                                if (success) {
                                    snackbarHostState.showSnackbar("✅ Cập nhật giao dịch thành công!")
                                } else {
                                    snackbarHostState.showSnackbar("❌ Lỗi: ${error ?: "Không xác định"}")
                                }
                            }
                            showEditDialog = false
                            selectedTransactionForEdit = null
                        }
                    }
                }
            }
        )
    }

    // === Dialog xác nhận xóa giao dịch ===
    if (showDeleteDialog && selectedTransactionForDelete != null) {
        DeleteTransactionDialog(
            transaction = selectedTransactionForDelete!!,
            onDismiss = { 
                showDeleteDialog = false
                selectedTransactionForDelete = null
            },
            onConfirm = {
                vm.deleteTransaction(selectedTransactionForDelete!!.id) { success, error ->
                    coroutineScope.launch {
                        if (success) {
                            snackbarHostState.showSnackbar("✅ Xóa giao dịch thành công!")
                        } else {
                            snackbarHostState.showSnackbar("❌ Lỗi: ${error ?: "Không xác định"}")
                        }
                    }
                    showDeleteDialog = false
                    selectedTransactionForDelete = null
                }
            }
        )
    }
}

// Helper function để lọc giao dịch
private fun filterTransactions(
    transactions: List<com.example.flatmateharmony.data.Transaction>,
    searchQuery: String,
    timeFilter: TimeFilter
): List<com.example.flatmateharmony.data.Transaction> {
    var filtered = transactions

    // Lọc theo tìm kiếm
    if (searchQuery.isNotBlank()) {
        filtered = filtered.filter { 
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    // Lọc theo thời gian (ViewModel đã lọc theo tháng rồi, nên chỉ lọc thêm theo ngày/tuần)
    val calendar = java.util.Calendar.getInstance()
    
    when (timeFilter) {
        TimeFilter.TODAY -> {
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startOfDay = calendar.timeInMillis
            filtered = filtered.filter { it.createdAt >= startOfDay }
        }
        TimeFilter.THIS_WEEK -> {
            val today = java.util.Calendar.getInstance()
            val dayOfWeek = today.get(java.util.Calendar.DAY_OF_WEEK)
            val daysFromMonday = if (dayOfWeek == java.util.Calendar.SUNDAY) 6 else dayOfWeek - java.util.Calendar.MONDAY
            
            calendar.add(java.util.Calendar.DAY_OF_MONTH, -daysFromMonday)
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startOfWeek = calendar.timeInMillis
            filtered = filtered.filter { it.createdAt >= startOfWeek }
        }
        TimeFilter.THIS_MONTH -> {
            // ViewModel đã lọc theo tháng rồi, không cần lọc thêm
            // Giữ nguyên danh sách
        }
        TimeFilter.ALL -> {
            // Không lọc
        }
    }

    return filtered.sortedByDescending { it.createdAt }
}

/* ================= UI Components ================= */

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("Lỗi: $message", color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun TotalExpenseCard(totalAmount: Long, youBalance: Long) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF6366F1),
                            Color(0xFF8B5CF6)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "💰",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Tổng chi tiêu",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Total Amount
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        formatCurrencyDisplay(totalAmount),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 36.sp
                        ),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Balance Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BalanceItem(
                        icon = Icons.Default.ArrowDownward,
                        label = "Cần thu",
                        amount = if (youBalance > 0) youBalance else 0,
                        isPositive = true,
                        modifier = Modifier.weight(1f)
                    )

                    BalanceItem(
                        icon = Icons.Default.ArrowUpward,
                        label = "Cần trả",
                        amount = if (youBalance < 0) -youBalance else 0,
                        isPositive = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    amount: Long,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Label
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Amount
            Text(
                formatCurrencyDisplay(amount),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = Color.White
            )
        }
    }
}

@Composable
private fun SearchAndFilterSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: TimeFilter,
    onFilterChange: (TimeFilter) -> Unit
) {
    Column {
        // Thanh tìm kiếm
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Tìm kiếm giao dịch...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Tìm kiếm")
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Xóa")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Bộ lọc thời gian
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == TimeFilter.ALL,
                onClick = { onFilterChange(TimeFilter.ALL) },
                label = { Text("Tất cả") }
            )
            FilterChip(
                selected = selectedFilter == TimeFilter.TODAY,
                onClick = { onFilterChange(TimeFilter.TODAY) },
                label = { Text("Hôm nay") }
            )
            FilterChip(
                selected = selectedFilter == TimeFilter.THIS_WEEK,
                onClick = { onFilterChange(TimeFilter.THIS_WEEK) },
                label = { Text("Tuần này") }
            )
            FilterChip(
                selected = selectedFilter == TimeFilter.THIS_MONTH,
                onClick = { onFilterChange(TimeFilter.THIS_MONTH) },
                label = { Text("Tháng này") }
            )
        }
    }
}

@Composable
private fun TransactionsSection(
    transactions: List<com.example.flatmateharmony.data.Transaction>,
    showAll: Boolean,
    onToggleShowAll: () -> Unit,
    uid: String?,
    currentUserName: String,
    userNames: Map<String, String>,
    onEditTransaction: (com.example.flatmateharmony.data.Transaction) -> Unit,
    onDeleteTransaction: (com.example.flatmateharmony.data.Transaction) -> Unit
) {
    Column {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📋",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Giao dịch gần đây",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = Color(0xFF1A1F36)
            )
        }

        Spacer(Modifier.height(16.dp))

        val recentTx = if (showAll) transactions else transactions.take(4)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (recentTx.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "💸",
                                style = MaterialTheme.typography.displayMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Chưa có giao dịch nào",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    recentTx.forEachIndexed { index, t ->
                        val payerName = if (t.payerId == uid)
                            if (currentUserName.isNotBlank()) currentUserName else "Bạn"
                        else userNames[t.payerId] ?: "Ẩn danh"

                        val isYouPayer = t.payerId == uid
                        val displayAmount = if (isYouPayer) -t.amount else t.amount

                        TransactionRow(
                            transaction = t,
                            title = t.description,
                            subtitle = payerName,
                            amount = displayAmount,
                            transactionDate = t.createdAt,
                            imageUrl = t.imageUrl,
                            onEdit = { onEditTransaction(t) },
                            onDelete = { onDeleteTransaction(t) }
                        )

                        if (index != recentTx.lastIndex) {
                            Divider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 1.dp,
                                color = Color(0xFFF0F0F0)
                            )
                        }
                    }

                    if (transactions.size > 4) {
                        Divider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 1.dp,
                            color = Color(0xFFF0F0F0)
                        )
                        TextButton(
                            onClick = onToggleShowAll,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                if (showAll) "Thu gọn" else "Xem thêm ${transactions.size - 4} giao dịch",
                                color = Color(0xFF6366F1),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = if (showAll) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionRow(
    transaction: com.example.flatmateharmony.data.Transaction,
    title: String,
    subtitle: String,
    amount: Long,
    transactionDate: Long,
    imageUrl: String = "",
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isPositive = amount >= 0
    val amountText = (if (isPositive) "+" else "-") + formatCurrencyDisplay(kotlin.math.abs(amount))

    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val currentDate = System.currentTimeMillis()
    val dateDiffInMillis = currentDate - transactionDate
    val daysAgo = TimeUnit.MILLISECONDS.toDays(dateDiffInMillis)

    val displayDate = when {
        daysAgo < 1 -> "Hôm nay"
        daysAgo == 1L -> "Hôm qua"
        daysAgo < 7 -> "$daysAgo ngày trước"
        else -> dateFormat.format(Date(transactionDate))
    }

    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { },
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (imageUrl.isNotEmpty()) {
                        val bitmap = remember(imageUrl) { base64ToBitmap(imageUrl) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Ảnh giao dịch",
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            GradientIcon(isPositive)
                        }
                    } else {
                        GradientIcon(isPositive)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = Color(0xFF1A1F36),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF64748B),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = displayDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = amountText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        ),
                        color = if (isPositive) Color(0xFF4CAF50) else Color(0xFFFF5252)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier.wrapContentSize(Alignment.TopStart)
                    ) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Tùy chọn",
                                tint = Color(0xFF64748B)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier
                                .width(170.dp)
                                .shadow(8.dp, RoundedCornerShape(16.dp))
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(16.dp)
                                )
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Sửa",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Xóa",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFFF5252)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradientIcon(isPositive: Boolean) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isPositive)
                        listOf(Color(0xFF4CAF50), Color(0xFF45A049))
                    else
                        listOf(Color(0xFFFF5252), Color(0xFFD32F2F))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun DebtsSection(
    debts: List<Settlement>,
    userNames: Map<String, String>,
    onDebtClick: (Settlement) -> Unit
) {
    Column {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "💳",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Nợ hiện tại",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = Color(0xFF1A1F36)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        debts.forEach { s ->
            val toName = userNames[s.to] ?: "Ẩn danh"
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .shadow(4.dp, RoundedCornerShape(20.dp))
                    .clickable { onDebtClick(s) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFFF5F5),
                                    Color(0xFFFFEBEE)
                                )
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Avatar với gradient
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFFFF5252),
                                                Color(0xFFD32F2F)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(
                                    "Mình nợ $toName",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    color = Color(0xFF1A1F36)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    formatCurrencyDisplay(s.amount),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    ),
                                    color = Color(0xFFFF5252)
                                )
                            }
                        }

                        // Button thanh toán
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF6366F1),
                                            Color(0xFF8B5CF6)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Thanh toán",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ================ Dialogs ================ */

@Composable
fun NameInputDialog(
    tempNameInput: String,
    currentUserName: String, // Thêm param bị thiếu để không bị lỗi build
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.CenterHorizontally)
                )

                Text(
                    "Nhập tên của bạn",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Text(
                    "Để mọi người biết ai là người chi trả, vui lòng nhập tên của bạn",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = tempNameInput,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tên của bạn") },
                    placeholder = { Text("Ví dụ: Nguyễn Văn A") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    }
                )

                Button(
                    onClick = onConfirm,
                    enabled = tempNameInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Xác nhận", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun CreateTransactionDialog(
    currentUserName: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Uri?) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.CenterHorizontally)
                )

                Text(
                    "Tạo giao dịch mới",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                if (currentUserName.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Người chi trả: $currentUserName",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mô tả") },
                    placeholder = { Text("VD: Tiền điện tháng 10") },
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Description, contentDescription = null)
                    }
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { newValue ->
                        // Chỉ cho phép nhập số
                        val digitsOnly = newValue.filter { it.isDigit() }
                        amount = digitsOnly
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Số tiền (VND)") },
                    placeholder = { Text("VD: 250.000") },
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = rememberCurrencyVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = {
                        Icon(Icons.Default.AttachMoney, contentDescription = null)
                    },
                    suffix = { Text("đ") }
                )

                // Phần upload ảnh
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Ảnh đại diện (tùy chọn)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (selectedImageUri != null) {
                        // Hiển thị ảnh đã chọn
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Ảnh đại diện giao dịch",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            
                            // Nút xóa ảnh
                            IconButton(
                                onClick = { selectedImageUri = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.errorContainer,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Xóa ảnh",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else {
                        // Nút chọn ảnh
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    "Chọn ảnh từ thư viện",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Hủy", style = MaterialTheme.typography.titleMedium)
                    }

                    Button(
                        onClick = { onConfirm(description, amount, selectedImageUri) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = description.isNotBlank() && amount.isNotBlank()
                    ) {
                        Text("Lưu", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}


@Composable
fun ConfirmPaymentDialog(
    amount: Long,
    toName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Paid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    "Xác nhận thanh toán",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Bạn xác nhận đã thanh toán ${formatCurrencyDisplay(amount)} cho $toName?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Hủy", style = MaterialTheme.typography.titleMedium)
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("thanh toán", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun EditTransactionDialog(
    transaction: com.example.flatmateharmony.data.Transaction,
    currentUserName: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Uri?) -> Unit
) {
    var description by remember { mutableStateOf(transaction.description) }
    var amount by remember { mutableStateOf(transaction.amount.toString()) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.CenterHorizontally)
                )

                Text(
                    "Chỉnh sửa giao dịch",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mô tả") },
                    placeholder = { Text("VD: Tiền điện tháng 10") },
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Description, contentDescription = null)
                    }
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { newValue ->
                        val digitsOnly = newValue.filter { it.isDigit() }
                        amount = digitsOnly
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Số tiền (VND)") },
                    placeholder = { Text("VD: 250.000") },
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = rememberCurrencyVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = {
                        Icon(Icons.Default.AttachMoney, contentDescription = null)
                    },
                    suffix = { Text("đ") }
                )

                // Phần upload ảnh
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Ảnh đại diện (tùy chọn)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (transaction.imageUrl.isNotEmpty()) {
                        val context = LocalContext.current
                        val bitmap = remember(transaction.imageUrl) { base64ToBitmap(transaction.imageUrl) }
                        
                        if (bitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Ảnh hiện tại",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                
                                IconButton(
                                    onClick = { selectedImageUri = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .background(
                                            MaterialTheme.colorScheme.errorContainer,
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Xóa ảnh",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    
                    if (selectedImageUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Ảnh mới",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            
                            IconButton(
                                onClick = { selectedImageUri = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.errorContainer,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Xóa ảnh",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else if (transaction.imageUrl.isEmpty()) {
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    "Chọn ảnh từ thư viện",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Thay đổi ảnh")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Hủy", style = MaterialTheme.typography.titleMedium)
                    }

                    Button(
                        onClick = { onConfirm(description, amount, selectedImageUri) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = description.isNotBlank() && amount.isNotBlank()
                    ) {
                        Text("Lưu", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteTransactionDialog(
    transaction: com.example.flatmateharmony.data.Transaction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                "Xóa giao dịch",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Bạn có chắc chắn muốn xóa giao dịch này không?",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "\"${transaction.description}\"",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    formatCurrencyDisplay(transaction.amount),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Hành động này không thể hoàn tác!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5252)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Xóa")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Hủy")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}