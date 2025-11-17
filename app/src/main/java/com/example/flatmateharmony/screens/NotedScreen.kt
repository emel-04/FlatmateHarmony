package com.example.flatmateharmony.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flatmateharmony.model.Member
import com.example.flatmateharmony.model.ShoppingItem
import com.example.flatmateharmony.model.TaskAssignment
import com.example.flatmateharmony.model.TaskHistory
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import com.example.flatmateharmony.navigation.AppHeader
import com.example.flatmateharmony.navigation.AppNavigationBar
import com.example.flatmateharmony.viewmodel.NotedUiState
import com.example.flatmateharmony.viewmodel.NotedViewModel
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotedScreen(
    navController: NavController,
    homeCode: String,
    vm: NotedViewModel = viewModel()
) {
    val ui = vm.uiState.collectAsStateWithLifecycle().value
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    var showAddShoppingDialog by remember { mutableStateOf(false) }

    LaunchedEffect(homeCode) { vm.load(homeCode) }

    Scaffold(
        topBar = {
            AppHeader(ui.home)
        },
        bottomBar = { AppNavigationBar(navController, ui.home) },
        floatingActionButton = {
            ModernFABGroup(
                canRandomize = ui.canRandomize,
                onAddShopping = { showAddShoppingDialog = true },
                onRandomize = { vm.randomizeAssignments() }
            )
        },
        containerColor = Color(0xFFF5F7FA)
    ) { padding ->
        ScreenBody(
            padding = padding,
            ui = ui,
            onToggleBought = { vm.toggleShoppingBought(it) },
            onDeleteShopping = { vm.deleteShoppingItem(it) },
            onDeleteMember = { vm.deleteMember(it) }
        )
    }

    if (showAddShoppingDialog) {
        AddShoppingDialog(
            onDismiss = { showAddShoppingDialog = false },
            onAdd = { name, note ->
                vm.addShoppingItem(name, note, currentUserId)
                showAddShoppingDialog = false
            }
        )
    }
}

@Composable
private fun ModernTopBar() {
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
                text = "Công Việc",
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
private fun ModernFABGroup(
    canRandomize: Boolean,
    onAddShopping: () -> Unit,
    onRandomize: () -> Unit
) {
    // Chỉ hiển thị nút Random
    FloatingActionButton(
        onClick = onRandomize,
        containerColor = if (canRandomize) Color(0xFF6366F1) else Color.Gray,
        contentColor = Color.White,
        modifier = Modifier.size(64.dp)
    ) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = "Random công việc",
            modifier = Modifier.size(28.dp)
        )
    }
}


@Composable
private fun ScreenBody(
    padding: PaddingValues,
    ui: NotedUiState,
    onToggleBought: (ShoppingItem) -> Unit,
    onDeleteShopping: (ShoppingItem) -> Unit,
    onDeleteMember: (Member) -> Unit
) {
    if (ui.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF6366F1))
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(20.dp)
    ) {
        // Progress Card - Task History Stats
        item {
            val totalTasks = ui.taskHistory.sumOf { it.assignments.size }
            val uniqueMembers = ui.taskHistory.flatMap { history ->
                history.assignments.map { it.member.name }
            }.distinct().size
            
            TaskStatsCard(
                historyCount = ui.taskHistory.size,
                totalTasks = totalTasks,
                uniqueMembers = uniqueMembers
            )
        }

        // Công việc hôm nay
        if (ui.assignments.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Công việc hôm nay",
                    icon = "📋",
                    count = null
                )
            }
            item {
                TodayTasksGrid(assignments = ui.assignments)
            }
        } else {
            item {
                EmptyStateCard(
                    icon = "🎯",
                    message = "Nhấn nút + để phân công công việc",
                    color = Color(0xFFEEF2FF)
                )
            }
        }

        // Lịch sử phân công
        item {
            SectionHeader(
                title = "Lịch sử phân công",
                icon = "📜",
                count = ui.taskHistory.size
            )
        }
        if (ui.taskHistory.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = "📋",
                    message = "Chưa có lịch sử phân công",
                    color = Color(0xFFF0FDF4)
                )
            }
        } else {
            items(ui.taskHistory.size) { index ->
                TaskHistoryCard(history = ui.taskHistory[index])
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun TaskStatsCard(historyCount: Int, totalTasks: Int, uniqueMembers: Int) {
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
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "📊 Thống kê",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                
                Spacer(Modifier.height(20.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        value = historyCount.toString(),
                        label = "Lần phân công",
                        icon = "📋"
                    )
                    StatItem(
                        value = totalTasks.toString(),
                        label = "Công việc",
                        icon = "✅"
                    )
                    StatItem(
                        value = uniqueMembers.toString(),
                        label = "Thành viên",
                        icon = "👥"
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, icon: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            ),
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: String, count: Int?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = Color(0xFF1A1F36)
            )
        }
        
        if (count != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF6366F1).copy(alpha = 0.1f)
            ) {
                Text(
                    text = count.toString(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF6366F1)
                )
            }
        }
    }
}

@Composable
private fun TodayTasksGrid(assignments: List<TaskAssignment>) {
    val rowCount = (assignments.size + 1) / 2
    val gridHeight = (rowCount * 140).dp
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.height(gridHeight)
    ) {
        items(assignments.size) { index ->
            TaskGridItem(assignment = assignments[index])
        }
    }
}

@Composable
private fun TaskGridItem(assignment: TaskAssignment) {
    val colors = listOf(
        Color(0xFFFFEBEE) to Color(0xFFEF5350),
        Color(0xFFE8F5E9) to Color(0xFF66BB6A),
        Color(0xFFFFF3E0) to Color(0xFFFF9800),
        Color(0xFFE3F2FD) to Color(0xFF42A5F5)
    )
    val colorIndex = kotlin.math.abs(assignment.task.name.hashCode()) % colors.size
    val colorPair = colors[colorIndex]
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = colorPair.first
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = assignment.task.icon,
                style = MaterialTheme.typography.displaySmall
            )
            
            Column {
                Text(
                    text = assignment.task.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF1A1F36),
                    maxLines = 1
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(colorPair.second),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = assignment.member.name.first().toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Text(
                        text = assignment.member.name,
                        modifier = Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernShoppingCard(
    item: ShoppingItem,
    onToggleBought: (ShoppingItem) -> Unit,
    onDelete: (ShoppingItem) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clickable { onToggleBought(item) },
        shape = RoundedCornerShape(16.dp),
        color = if (item.isBought) Color(0xFFF1F5F9) else Color.White
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.isBought,
                    onCheckedChange = { onToggleBought(item) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF4CAF50)
                    )
                )
                
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (item.isBought) Color(0xFF94A3B8) else Color(0xFF1A1F36),
                        textDecoration = if (item.isBought) TextDecoration.LineThrough else null
                    )
                    if (item.note.isNotBlank()) {
                        Text(
                            text = item.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
            
            IconButton(onClick = { onDelete(item) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Xóa",
                    tint = Color(0xFFEF4444)
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(icon: String, message: String, color: Color) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = color
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun TaskHistoryCard(history: TaskHistory) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val date = try {
        val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(history.date)
        if (parsedDate != null) dateFormat.format(parsedDate) else history.date
    } catch (e: Exception) {
        history.date
    }
    
    // Tính khoảng cách thời gian
    val daysAgo = try {
        val historyDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(history.date)
        val today = Date()
        val diff = today.time - (historyDate?.time ?: 0)
        val days = diff / (1000 * 60 * 60 * 24)
        when {
            days == 0L -> "Hôm nay"
            days == 1L -> "Hôm qua"
            days < 7L -> "$days ngày trước"
            else -> date
        }
    } catch (e: Exception) {
        date
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.White
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📅 $daysAgo",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF6366F1)
                )
                Text(
                    text = "${history.assignments.size} công việc",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Hiển thị danh sách công việc
            history.assignments.forEach { assignment ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = assignment.task.icon,
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = assignment.task.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color(0xFF1A1F36)
                        )
                    }
                    
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF6366F1).copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = assignment.member.name,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFF6366F1)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddMemberDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var memberName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Thêm thành viên",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            OutlinedTextField(
                value = memberName,
                onValueChange = { memberName = it },
                label = { Text("Tên thành viên") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = { if (memberName.isNotBlank()) onAdd(memberName) },
                enabled = memberName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) { Text("Thêm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun AddShoppingDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var itemName by remember { mutableStateOf("") }
    var itemNote by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Thêm đồ cần mua",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("Tên đồ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = itemNote,
                    onValueChange = { itemNote = it },
                    label = { Text("Ghi chú (tùy chọn)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (itemName.isNotBlank()) onAdd(itemName, itemNote) },
                enabled = itemName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) { Text("Thêm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        },
        shape = RoundedCornerShape(20.dp)
    )
}