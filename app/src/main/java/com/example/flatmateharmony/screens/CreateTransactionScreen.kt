package com.example.flatmateharmony.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.example.flatmateharmony.navigation.Screen
import com.example.flatmateharmony.navigation.AppHeader
import com.example.flatmateharmony.navigation.AppNavigationBar
import com.example.flatmateharmony.data.Home
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import com.example.flatmateharmony.utils.AuthManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import com.example.flatmateharmony.utils.formatCurrency
import com.example.flatmateharmony.utils.parseCurrency
import com.example.flatmateharmony.utils.rememberCurrencyVisualTransformation
import com.example.flatmateharmony.utils.uriToBase64
import com.example.flatmateharmony.utils.base64ToBitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import android.util.Log
import coil.compose.AsyncImage

data class HomeInfo(
    val homeCode: String = "",
    val address: String = "",
    val rent: Double = 0.0,
    val ownerId: String = "",
    val createdAt: Long = 0L
)

data class MemberInfo(
    val userId: String = "",
    val name: String = "",
    val role: String = "",
    val joinedAt: Long = 0L,
    val avatarUrl: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTransactionScreen(navController: NavController, homeCode: String) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }

    var homeInfo by remember { mutableStateOf<HomeInfo?>(null) }
    var members by remember { mutableStateOf<List<MemberInfo>>(emptyList()) }
    var currentUserInfo by remember { mutableStateOf<MemberInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var homeId by remember { mutableStateOf("") }
    var memberToRemove by remember { mutableStateOf<MemberInfo?>(null) }
    var showRemoveMemberDialog by remember { mutableStateOf(false) }
    var showEditHomeDialog by remember { mutableStateOf(false) }
    var editAddress by remember { mutableStateOf("") }
    var editRent by remember { mutableStateOf("") }
    var memberToTransferOwnership by remember { mutableStateOf<MemberInfo?>(null) }
    var showTransferOwnershipDialog by remember { mutableStateOf(false) }
    var currentUserAvatarUrl by remember { mutableStateOf("") }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var showAvatarPickerDialog by remember { mutableStateOf(false) }

    // ✅ Load thông tin nhà và members
    LaunchedEffect(homeCode) {
        try {
            val homeDocs = db.collection("homes")
                .whereEqualTo("homeCode", homeCode)
                .get()
                .await()

            if (homeDocs.documents.isNotEmpty()) {
                val doc = homeDocs.documents.first()
                homeId = doc.id

                homeInfo = HomeInfo(
                    homeCode = doc.getString("homeCode") ?: "",
                    address = doc.getString("address") ?: "",
                    rent = doc.getDouble("rent") ?: 0.0,
                    ownerId = doc.getString("ownerId") ?: "",
                    createdAt = doc.getLong("createdAt") ?: 0L
                )

                val membersSnapshot = db.collection("homes")
                    .document(homeId)
                    .collection("members")
                    .get()
                    .await()

                val membersList = membersSnapshot.documents.mapNotNull { memberDoc ->
                    MemberInfo(
                        userId = memberDoc.getString("userId") ?: "",
                        name = memberDoc.getString("name") ?: "Ẩn danh",
                        role = memberDoc.getString("role") ?: "member",
                        joinedAt = memberDoc.getLong("joinedAt") ?: 0L,
                        avatarUrl = memberDoc.getString("avatarUrl") ?: ""
                    )
                }.sortedByDescending { it.joinedAt }

                members = membersList
                currentUserInfo = membersList.find { it.userId == currentUserId }
                currentUserAvatarUrl = currentUserInfo?.avatarUrl ?: ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    // ✅ Hàm chuyển quyền chủ nhà cho member khác
    suspend fun transferOwnershipToNewOwner(): String? {
        if (homeId.isEmpty()) return null
        try {
            // Lấy tất cả members còn lại (không phải owner hiện tại)
            val allMembersSnapshot = db.collection("homes")
                .document(homeId)
                .collection("members")
                .get()
                .await()

            if (allMembersSnapshot.documents.isEmpty()) {
                // Không còn member nào, không thể chuyển quyền
                return null
            }

            // Tìm member cũ nhất (joinedAt nhỏ nhất) để làm chủ nhà mới
            val allMembers = allMembersSnapshot.documents.mapNotNull { doc ->
                MemberInfo(
                    userId = doc.getString("userId") ?: "",
                    name = doc.getString("name") ?: "Ẩn danh",
                    role = doc.getString("role") ?: "member",
                    joinedAt = doc.getLong("joinedAt") ?: 0L,
                    avatarUrl = doc.getString("avatarUrl") ?: ""
                )
            }

            val newOwner = allMembers.minByOrNull { it.joinedAt }
            if (newOwner == null) return null

            // Cập nhật role của member mới thành owner
            val newOwnerDoc = allMembersSnapshot.documents.find { 
                it.getString("userId") == newOwner.userId 
            }
            
            if (newOwnerDoc != null) {
                newOwnerDoc.reference.update("role", "owner").await()
            }

            // Cập nhật ownerId trong document homes
            db.collection("homes")
                .document(homeId)
                .update("ownerId", newOwner.userId)
                .await()

            return newOwner.userId
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // ✅ Hàm reload dữ liệu
    suspend fun reloadData() {
        if (homeId.isEmpty()) return
        try {
            val homeDoc = db.collection("homes").document(homeId).get().await()
            homeInfo = HomeInfo(
                homeCode = homeDoc.getString("homeCode") ?: "",
                address = homeDoc.getString("address") ?: "",
                rent = homeDoc.getDouble("rent") ?: 0.0,
                ownerId = homeDoc.getString("ownerId") ?: "",
                createdAt = homeDoc.getLong("createdAt") ?: 0L
            )

            val membersSnapshot = db.collection("homes")
                .document(homeId)
                .collection("members")
                .get()
                .await()

            val membersList = membersSnapshot.documents.mapNotNull { memberDoc ->
                MemberInfo(
                    userId = memberDoc.getString("userId") ?: "",
                    name = memberDoc.getString("name") ?: "Ẩn danh",
                    role = memberDoc.getString("role") ?: "member",
                    joinedAt = memberDoc.getLong("joinedAt") ?: 0L,
                    avatarUrl = memberDoc.getString("avatarUrl") ?: ""
                )
            }.sortedByDescending { it.joinedAt }

            members = membersList
            currentUserInfo = membersList.find { it.userId == currentUserId }
            currentUserAvatarUrl = currentUserInfo?.avatarUrl ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ✅ Hàm chuyển quyền chủ nhà cho member cụ thể (thủ công)
    suspend fun transferOwnershipToMember(newOwnerUserId: String): Boolean {
        if (homeId.isEmpty() || currentUserInfo?.role != "owner") return false
        try {
            // Tìm member document của chủ nhà cũ và chủ nhà mới
            val oldOwnerDoc = db.collection("homes")
                .document(homeId)
                .collection("members")
                .whereEqualTo("userId", currentUserId)
                .get()
                .await()
            
            val newOwnerDoc = db.collection("homes")
                .document(homeId)
                .collection("members")
                .whereEqualTo("userId", newOwnerUserId)
                .get()
                .await()

            if (newOwnerDoc.documents.isEmpty()) return false

            // Cập nhật role: chủ nhà cũ -> member, chủ nhà mới -> owner
            oldOwnerDoc.documents.forEach { it.reference.update("role", "member").await() }
            newOwnerDoc.documents.forEach { it.reference.update("role", "owner").await() }

            // Cập nhật ownerId trong document homes
            db.collection("homes")
                .document(homeId)
                .update("ownerId", newOwnerUserId)
                .await()

            // Reload dữ liệu
            reloadData()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    // ✅ Hàm rời khỏi nhà
    suspend fun leaveHome() {
        if (homeId.isEmpty() || currentUserId == null) return
        try {
            val homeDoc = db.collection("homes").document(homeId).get().await()
            val membersList = homeDoc.get("members") as? MutableList<String> ?: mutableListOf()
            
            // Kiểm tra xem user đang rời có phải là chủ nhà không
            val isOwner = currentUserInfo?.role == "owner"
            
            // Nếu user đang rời là chủ nhà, chuyển quyền TRƯỚC khi xóa
            if (isOwner && membersList.size > 1) {
                transferOwnershipToNewOwner()
            }
            
            // Xóa user khỏi danh sách members
            membersList.remove(currentUserId)

            // Xóa member document
            val memberDocs = db.collection("homes")
                .document(homeId)
                .collection("members")
                .whereEqualTo("userId", currentUserId)
                .get()
                .await()

            memberDocs.documents.forEach { it.reference.delete().await() }

            // Cập nhật danh sách members trong document homes
            db.collection("homes")
                .document(homeId)
                .update("members", membersList)
                .await()

            navController.navigate(Screen.Hello.route) {
                popUpTo(0) { inclusive = true }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ✅ Hàm xóa thành viên (chỉ chủ nhà)
    suspend fun removeMember(member: MemberInfo) {
        if (homeId.isEmpty() || currentUserInfo?.role != "owner") return
        try {
            val homeDoc = db.collection("homes").document(homeId).get().await()
            val membersList = homeDoc.get("members") as? MutableList<String> ?: mutableListOf()
            
            // Kiểm tra xem member bị xóa có phải là chủ nhà không
            val isRemovedMemberOwner = member.role == "owner"
            
            // Nếu member bị xóa là chủ nhà, chuyển quyền TRƯỚC khi xóa
            if (isRemovedMemberOwner && membersList.size > 1) {
                transferOwnershipToNewOwner()
            }
            
            // Xóa member khỏi danh sách
            membersList.remove(member.userId)

            // Xóa member document
            val memberDocs = db.collection("homes")
                .document(homeId)
                .collection("members")
                .whereEqualTo("userId", member.userId)
                .get()
                .await()

            memberDocs.documents.forEach { it.reference.delete().await() }

            // Cập nhật danh sách members trong document homes
            db.collection("homes")
                .document(homeId)
                .update("members", membersList)
                .await()

            // Reload dữ liệu
            reloadData()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ✅ Hàm cập nhật thông tin nhà
    suspend fun updateHomeInfo() {
        if (homeId.isEmpty() || currentUserInfo?.role != "owner") return
        try {
            val updates = mutableMapOf<String, Any>()
            
            if (editAddress.isNotBlank()) {
                updates["address"] = editAddress
            }
            
            if (editRent.isNotBlank()) {
                val rentValue = editRent.toDoubleOrNull()
                if (rentValue != null) {
                    updates["rent"] = rentValue
                }
            }
            
            if (updates.isNotEmpty()) {
                db.collection("homes")
                    .document(homeId)
                    .update(updates)
                    .await()
                
                // Reload home info
                val doc = db.collection("homes").document(homeId).get().await()
                homeInfo = HomeInfo(
                    homeCode = doc.getString("homeCode") ?: "",
                    address = doc.getString("address") ?: "",
                    rent = doc.getDouble("rent") ?: 0.0,
                    ownerId = doc.getString("ownerId") ?: "",
                    createdAt = doc.getLong("createdAt") ?: 0L
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ✅ Hàm lưu avatar
    suspend fun saveAvatar(avatarBase64: String): Boolean {
        if (homeId.isEmpty() || currentUserId == null) return false
        try {
            val memberDocs = db.collection("homes")
                .document(homeId)
                .collection("members")
                .whereEqualTo("userId", currentUserId)
                .get()
                .await()

            if (memberDocs.documents.isNotEmpty()) {
                memberDocs.documents.first().reference.update("avatarUrl", avatarBase64).await()
                
                // Reload dữ liệu
                reloadData()
                return true
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    // ✅ Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAvatarUri = uri
            showAvatarPickerDialog = true
        }
    }

    // ✅ Hàm đăng xuất
    fun logout() {
        authManager.clearSession() // Xóa session trong SharedPreferences
        navController.navigate(Screen.Login.route) {
            popUpTo(0) { inclusive = true }
        }
    }

    // ✅ Giao diện chính
    Scaffold(
        topBar = {
            // Chuyển đổi HomeInfo → Home để AppHeader nhận đúng kiểu
            AppHeader(
                homeInfo?.let {
                    com.example.flatmateharmony.data.Home(
                        homeCode = it.homeCode,
                        address = it.address,
                        rent = it.rent.toLong(),
                        ownerId = it.ownerId,
                        createdAt = it.createdAt
                    )
                }
            )
        },
        bottomBar = {
            AppNavigationBar(
                navController,
                homeInfo?.let {
                    Home(
                        homeCode = it.homeCode,
                        address = it.address,
                        rent = it.rent.toLong(),
                        ownerId = it.ownerId,
                        createdAt = it.createdAt
                    )
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF5F5F5)),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Thông tin cá nhân với màu xanh nhạt
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE8F5E9)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .clickable { imagePickerLauncher.launch("image/*") }
                                    .background(Color(0xFF4CAF50)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentUserAvatarUrl.isNotEmpty()) {
                                    val bitmap = remember(currentUserAvatarUrl) { base64ToBitmap(currentUserAvatarUrl) }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = currentUserInfo?.name?.firstOrNull()?.uppercase() ?: "P",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                } else {
                                    Text(
                                        text = currentUserInfo?.name?.firstOrNull()?.uppercase() ?: "P",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                
                                // Icon camera nhỏ ở góc dưới bên phải
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF6366F1)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Đổi avatar",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentUserInfo?.name ?: "Phương Nguyễn",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20)
                                )
                            }

                            IconButton(
                                onClick = { showLeaveDialog = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ExitToApp,
                                    contentDescription = "Rời khỏi nhà",
                                    tint = Color(0xFFFF5722),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Thông tin nhà
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Thông tin nhà",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                                
                                // Chỉ hiển thị icon settings nếu là chủ nhà
                                if (currentUserInfo?.role == "owner") {
                                    IconButton(
                                        onClick = {
                                            editAddress = homeInfo?.address ?: ""
                                            editRent = homeInfo?.rent?.toLong()?.toString() ?: ""
                                            showEditHomeDialog = true
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Chỉnh sửa thông tin nhà",
                                            tint = Color(0xFFFF5722),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            // Chủ nhà
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Chủ nhà",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                                Text(
                                    text = members.find { it.role == "owner" }?.name ?: "Lan Trần",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }

                            // Mã nhà
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Mã nhà",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                                Text(
                                    text = homeInfo?.homeCode ?: "N/A",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2196F3)
                                )
                            }

                            // Địa chỉ
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Địa chỉ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                                Text(
                                    text = homeInfo?.address ?: "123 Nguyễn Văn Linh, Q.7",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }

                            // Giá thuê
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Giá thuê",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "${formatCurrency(homeInfo?.rent?.toLong() ?: 0)}đ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }

                            // Ngày tạo nhà
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Ngày tạo",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                                Text(
                                    text = homeInfo?.createdAt?.let { timestamp ->
                                        if (timestamp > 0) {
                                            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                                .format(Date(timestamp))
                                        } else {
                                            "N/A"
                                        }
                                    } ?: "N/A",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }

                // Danh sách bạn cùng nhà
                item {
                    Text(
                        text = "Bạn cùng nhà",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                items(members) { member ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            member.role == "owner" -> Color(0xFF2196F3)
                                            member.name.contains("Minh") -> Color(0xFF4CAF50)
                                            member.name.contains("Hải") -> Color(0xFFFF5722)
                                            else -> Color(0xFF9C27B0)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (member.avatarUrl.isNotEmpty()) {
                                    val bitmap = remember(member.avatarUrl) { base64ToBitmap(member.avatarUrl) }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = member.name.firstOrNull()?.uppercase() ?: "A",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                } else {
                                    Text(
                                        text = member.name.firstOrNull()?.uppercase() ?: "A",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = member.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                if (member.role == "owner") {
                                    Text(
                                        text = "Chủ nhà",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF2196F3),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }

                            // Hiển thị nút chuyển chủ nhà nếu là chủ nhà và không phải chính mình
                            if (currentUserInfo?.role == "owner" && member.userId != currentUserId && member.role != "owner") {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            memberToTransferOwnership = member
                                            showTransferOwnershipDialog = true
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AdminPanelSettings,
                                            contentDescription = "Chuyển chủ nhà",
                                            tint = Color(0xFF2196F3),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            memberToRemove = member
                                            showRemoveMemberDialog = true
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Xóa thành viên",
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Nút đăng xuất
                item {
                    OutlinedButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFF5722)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5722))
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Đăng xuất", fontWeight = FontWeight.Bold)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Dialog xác nhận xóa thành viên
    if (showRemoveMemberDialog && memberToRemove != null) {
        AlertDialog(
            onDismissRequest = { 
                showRemoveMemberDialog = false
                memberToRemove = null
            },
            icon = { 
                Icon(
                    Icons.Default.Warning, 
                    contentDescription = null,
                    tint = Color(0xFFFF5252)
                ) 
            },
            title = { 
                Text(
                    "Xóa thành viên?",
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text("Bạn có chắc chắn muốn xóa ${memberToRemove?.name} khỏi nhà? Họ sẽ mất quyền truy cập vào tất cả dữ liệu của nhà.") 
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            memberToRemove?.let { removeMember(it) }
                            showRemoveMemberDialog = false
                            memberToRemove = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) { Text("Xóa") }
            },
            dismissButton = { 
                OutlinedButton(onClick = { 
                    showRemoveMemberDialog = false
                    memberToRemove = null
                }) { 
                    Text("Hủy") 
                } 
            }
        )
    }

    // Dialog rời khỏi nhà
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5722)) },
            title = { Text("Rời khỏi nhà?", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc chắn muốn rời khỏi nhà này? Bạn sẽ mất quyền truy cập vào tất cả dữ liệu của nhà.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveDialog = false
                        coroutineScope.launch { leaveHome() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                ) { Text("Rời khỏi") }
            },
            dismissButton = { OutlinedButton(onClick = { showLeaveDialog = false }) { Text("Hủy") } }
        )
    }

    // Dialog đăng xuất
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFFF5722)) },
            title = { Text("Đăng xuất?", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                ) { Text("Đăng xuất") }
            },
            dismissButton = { OutlinedButton(onClick = { showLogoutDialog = false }) { Text("Hủy") } }
        )
    }

    // Dialog chuyển chủ nhà
    if (showTransferOwnershipDialog && memberToTransferOwnership != null) {
        AlertDialog(
            onDismissRequest = { 
                showTransferOwnershipDialog = false
                memberToTransferOwnership = null
            },
            icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color(0xFF2196F3)) },
            title = { Text("Chuyển chủ nhà?", fontWeight = FontWeight.Bold) },
            text = { 
                Text("Bạn có chắc chắn muốn chuyển quyền chủ nhà cho ${memberToTransferOwnership?.name}? Bạn sẽ trở thành thành viên thường.") 
            },
            confirmButton = {
                Button(
                    onClick = {
                        memberToTransferOwnership?.let { member ->
                            coroutineScope.launch {
                                val success = transferOwnershipToMember(member.userId)
                                if (success) {
                                    showTransferOwnershipDialog = false
                                    memberToTransferOwnership = null
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) { Text("Chuyển quyền") }
            },
            dismissButton = { 
                OutlinedButton(onClick = { 
                    showTransferOwnershipDialog = false
                    memberToTransferOwnership = null
                }) { 
                    Text("Hủy") 
                } 
            }
        )
    }

    // Dialog xác nhận cập nhật avatar
    if (showAvatarPickerDialog && selectedAvatarUri != null) {
        Dialog(onDismissRequest = { 
            showAvatarPickerDialog = false
            selectedAvatarUri = null
        }) {
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
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )

                    Text(
                        "Cập nhật avatar",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Preview ảnh
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = selectedAvatarUri,
                            contentDescription = "Avatar preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Text(
                        "Bạn có muốn sử dụng ảnh này làm avatar không?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = { 
                                showAvatarPickerDialog = false
                                selectedAvatarUri = null
                            },
                            modifier = Modifier
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Hủy", style = MaterialTheme.typography.titleMedium)
                        }

                        Button(
                            onClick = {
                                selectedAvatarUri?.let { uri ->
                                    coroutineScope.launch {
                                        try {
                                            Log.d("CreateTransactionScreen", "Bắt đầu chuyển đổi avatar sang Base64: $uri")
                                            val avatarBase64 = uriToBase64(context, uri) ?: ""
                                            
                                            if (avatarBase64.isNotEmpty()) {
                                                Log.d("CreateTransactionScreen", "Chuyển đổi avatar thành công! Kích thước: ${avatarBase64.length} ký tự")
                                                val success = saveAvatar(avatarBase64)
                                                if (success) {
                                                    showAvatarPickerDialog = false
                                                    selectedAvatarUri = null
                                                } else {
                                                    Log.e("CreateTransactionScreen", "Lỗi khi lưu avatar")
                                                }
                                            } else {
                                                Log.e("CreateTransactionScreen", "Chuyển đổi avatar thất bại")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("CreateTransactionScreen", "Lỗi khi xử lý avatar", e)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Xác nhận", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }

    // Dialog chỉnh sửa thông tin nhà
    if (showEditHomeDialog) {
        AlertDialog(
            onDismissRequest = { showEditHomeDialog = false },
            icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF4CAF50)) },
            title = { Text("Chỉnh sửa thông tin nhà", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editAddress,
                        onValueChange = { editAddress = it },
                        label = { Text("Địa chỉ") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2
                    )
                    OutlinedTextField(
                        value = editRent,
                        onValueChange = { newValue ->
                            // Chỉ cho phép nhập số
                            val digitsOnly = newValue.filter { it.isDigit() }
                            editRent = digitsOnly
                        },
                        label = { Text("Giá thuê (VND)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = rememberCurrencyVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        suffix = { Text("đ") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            updateHomeInfo()
                            showEditHomeDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) { Text("Lưu") }
            },
            dismissButton = { 
                OutlinedButton(onClick = { showEditHomeDialog = false }) { 
                    Text("Hủy") 
                } 
            }
        )
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
