package com.example.flatmateharmony.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.flatmateharmony.data.Home
import com.example.flatmateharmony.data.HomeRepository
import com.example.flatmateharmony.navigation.AppHeader
import com.example.flatmateharmony.navigation.AppNavigationBar
import com.example.flatmateharmony.navigation.Screen
import com.example.flatmateharmony.navigation.formatCurrency
import com.example.flatmateharmony.viewmodel.FinanceViewModel
import com.example.flatmateharmony.viewmodel.FinanceViewModelFactory
import com.example.flatmateharmony.viewmodel.ChatViewModel
import com.example.flatmateharmony.viewmodel.NotedViewModel
import com.example.flatmateharmony.model.ChatMessage
import com.example.flatmateharmony.model.TaskAssignment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class DashboardMember(
    val userId: String = "",
    val name: String = "",
    val role: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController) {
    val firestore = FirebaseFirestore.getInstance()
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    val homeRepo = remember { HomeRepository() }

    var homeInfo by remember { mutableStateOf<Home?>(null) }
    var members by remember { mutableStateOf<List<DashboardMember>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()

    // 🔹 Lấy dữ liệu nhà, thành viên
    LaunchedEffect(userId) {
        if (userId != null) {
            coroutineScope.launch {
                homeInfo = homeRepo.getHomeInfoByUser(userId)
                homeInfo?.let { home ->
                    try {
                        val homeDocs = firestore.collection("homes")
                            .whereEqualTo("homeCode", home.homeCode)
                            .get()
                            .await()

                        if (homeDocs.documents.isNotEmpty()) {
                            val homeId = homeDocs.documents.first().id

                            // Thành viên
                            val memberSnapshot = firestore.collection("homes")
                                .document(homeId)
                                .collection("members")
                                .get()
                                .await()

                            members = memberSnapshot.documents.map {
                                DashboardMember(
                                    userId = it.getString("userId") ?: "",
                                    name = it.getString("name") ?: "Ẩn danh",
                                    role = it.getString("role") ?: "member"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }
        }
    }

    // 🧹 ViewModel Công việc (dùng chung với NotedScreen)
    val notedVM: NotedViewModel = viewModel()
    val notedUi = notedVM.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(homeInfo?.homeCode) {
        homeInfo?.homeCode?.let { code ->
            notedVM.load(code)
        }
    }

    // 💰 ViewModel Tài chính
    val financeVM = homeInfo?.let {
        val factory = FinanceViewModelFactory(it.homeCode)
        androidx.lifecycle.viewmodel.compose.viewModel<FinanceViewModel>(factory = factory)
    }
    val state by financeVM?.uiState?.collectAsState() ?: remember { mutableStateOf(null) }

    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    val debt = state?.balances?.get(currentUid) ?: 0L
    val debtToPay = if (debt < 0) -debt else 0L
    val debtToReceive = if (debt > 0) debt else 0L

    // 💬 Chat: tái dùng ChatViewModel sẵn có
    val chatVM: ChatViewModel = viewModel()
    val chatUi by chatVM.uiState.collectAsState()

    LaunchedEffect(homeInfo?.homeCode, currentUid) {
        val code = homeInfo?.homeCode
        if (!code.isNullOrEmpty() && !currentUid.isNullOrEmpty()) {
            chatVM.loadChat(code, currentUid!!)
        }
    }

    Scaffold(
        topBar = {
            AppHeader(homeInfo)
        },
        bottomBar = { AppNavigationBar(navController, homeInfo) },
        containerColor = Color(0xFFF5F7FA)
    ) { padding ->
        if (isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF6366F1))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 👥 Thành viên
                if (members.isNotEmpty()) {
                    item {
                        MembersCard(members = members, currentUid = currentUid)
                    }
                }

                // 🧹 Công việc hôm nay
                item {
                    TasksCard(
                        assignments = notedUi.assignments,
                        currentUid = currentUid,
                        onViewDetails = {
                            homeInfo?.homeCode?.let { code ->
                                navController.navigate("${Screen.Tasks.route}/$code") {
                                    launchSingleTop = true
                                    restoreState = true
                                    // Không pop Dashboard, giữ Dashboard trong back stack
                                }
                            }
                        }
                    )
                }

                // 💰 Tài chính
                item {
                    FinanceCard(
                        debtToPay = debtToPay,
                        debtToReceive = debtToReceive,
                        onNavigate = {
                            homeInfo?.homeCode?.let { code ->
                                navController.navigate("${Screen.Finance.route}/$code") {
                                    launchSingleTop = true
                                    restoreState = true
                                    // Không pop Dashboard, giữ Dashboard trong back stack
                                }
                            }
                        }
                    )
                }

                // 💬 Tin nhắn
                item {
                    MessagesCard(
                        messages = chatUi.messages,
                        onNavigate = {
                            homeInfo?.let {
                                navController.navigate("${Screen.Chat.route}/${it.homeCode}") {
                                    launchSingleTop = true
                                    restoreState = true
                                    // Không pop Dashboard, giữ Dashboard trong back stack
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernDashboardTopBar() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trang Chủ",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                color = Color(0xFF1A1F36)
            )
        }
    }
}

@Composable
private fun MembersCard(members: List<DashboardMember>, currentUid: String?) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = Color.White
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "👥 Thành viên",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = Color.White
                )
                
                Spacer(Modifier.height(20.dp))
                
                // Hiển thị danh sách members theo hàng ngang, canh giữa
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    items(members) { member ->
                        val isCurrent = member.userId == currentUid
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCurrent) Color(0xFF4CAF50)
                                        else Color.White.copy(alpha = 0.9f)
                                    )
                                    .border(
                                        width = 3.dp,
                                        color = if (isCurrent) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = member.name.firstOrNull()?.uppercase() ?: "?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = if (isCurrent) Color.White else Color(0xFF6366F1)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (isCurrent) "Bạn" else member.name,
                                fontSize = 14.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksCard(
    assignments: List<TaskAssignment>,
    currentUid: String?,
    onViewDetails: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = Color.White
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🧹",
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        "Công việc hôm nay",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        ),
                        color = Color(0xFF1A1F36)
                    )
                }
                IconButton(
                    onClick = onViewDetails,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                            ),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Xem chi tiết",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            val myTasks = assignments.filter { it.member.userId == currentUid }
            val otherTasks = assignments.filter { it.member.userId != currentUid }

            if (myTasks.isEmpty() && otherTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFF8F9FA), Color(0xFFE9ECEF))
                            )
                        )
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "✨",
                            style = MaterialTheme.typography.displaySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Chưa có công việc nào",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color(0xFF64748B)
                        )
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Công việc của bạn
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .shadow(2.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE8F5E9)
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Bạn",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            if (myTasks.isEmpty()) {
                                Text(
                                    "Không có",
                                    fontSize = 14.sp,
                                    color = Color(0xFF66BB6A),
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                myTasks.forEach { assignment ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.6f))
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = assignment.task.icon,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            assignment.task.name,
                                            fontSize = 14.sp,
                                            color = Color(0xFF1A1F36),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Công việc của người khác
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .shadow(2.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF3E0)
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = "Mọi người",
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            if (otherTasks.isEmpty()) {
                                Text(
                                    "Không có",
                                    fontSize = 14.sp,
                                    color = Color(0xFFFF9800),
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                otherTasks.take(3).forEach { assignment ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.6f))
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = assignment.task.icon,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            "${assignment.member.name}",
                                            fontSize = 14.sp,
                                            color = Color(0xFF1A1F36),
                                            maxLines = 1,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                if (otherTasks.size > 3) {
                                    Text(
                                        "...",
                                        fontSize = 14.sp,
                                        color = Color(0xFF64748B),
                                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinanceCard(
    debtToPay: Long,
    debtToReceive: Long,
    onNavigate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "💸",
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        "Tài chính",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        ),
                        color = Color(0xFF1A1F36)
                    )
                }
                IconButton(
                    onClick = onNavigate,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                            ),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Chi tiết",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Nợ cần trả
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(2.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEBEE)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(0xFFFF5252), Color(0xFFD32F2F))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📤", fontSize = 28.sp)
                        }
                        Text(
                            "Cần trả",
                            fontSize = 14.sp,
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${formatCurrency(debtToPay)}đ",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            ),
                            color = Color(0xFFD32F2F)
                        )
                    }
                }

                // Nợ cần thu
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(2.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E9)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(0xFF4CAF50), Color(0xFF45A049))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📥", fontSize = 28.sp)
                        }
                        Text(
                            "Cần thu",
                            fontSize = 14.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${formatCurrency(debtToReceive)}đ",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            ),
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagesCard(
    messages: List<ChatMessage>,
    onNavigate: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = Color.White
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "💬",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        "Tin nhắn",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = Color(0xFF1A1F36)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val recentMessages = remember(messages) {
                messages
                    .sortedBy { it.timestamp }
                    .takeLast(3)
            }

            if (recentMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F7FA), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Chưa có tin nhắn nào",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                }
            } else {
                val latest = recentMessages.last()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(12.dp))
                        .clickable { onNavigate() },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEEF2FF)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Tin mới nhất",
                                fontSize = 13.sp,
                                color = Color(0xFF6366F1),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = latest.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF1A1F36),
                                maxLines = 2
                            )
                        }
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF6366F1)
                        )
                    }
                }
            }
        }
    }
}