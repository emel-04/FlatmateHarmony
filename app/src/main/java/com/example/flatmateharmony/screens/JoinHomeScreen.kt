package com.example.flatmateharmony.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flatmateharmony.navigation.Screen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun JoinHomeScreen(navController: NavController) {
    var homeCode by remember { mutableStateOf("") }
    var memberName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val db = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    val coroutineScope = rememberCoroutineScope()

    suspend fun joinHomeWithMember() {
        if (user == null) {
            message = "⚠️ Vui lòng đăng nhập trước khi tham gia nhà."
            return
        }

        if (homeCode.isBlank() || memberName.isBlank()) {
            message = "⚠️ Vui lòng nhập đầy đủ thông tin."
            return
        }

        loading = true

        try {
            // Tìm nhà theo mã
            val query = db.collection("homes")
                .whereEqualTo("homeCode", homeCode)
                .get()
                .await()

            if (query.isEmpty) {
                loading = false
                message = "❌ Mã nhà không tồn tại."
                return
            }

            val homeDoc = query.documents.first()
            val homeId = homeDoc.id
            val members = homeDoc.get("members") as? MutableList<String> ?: mutableListOf()

            // Kiểm tra xem user đã là thành viên chưa
            if (members.contains(user.uid)) {
                loading = false
                message = "⚠️ Bạn đã là thành viên của nhà này rồi."
                return
            }

            // Thêm userId vào danh sách members
            members.add(user.uid)

            // Cập nhật danh sách members
            db.collection("homes")
                .document(homeId)
                .update("members", members)
                .await()

            // Tạo member document với tên
            val memberData = hashMapOf(
                "userId" to user.uid,
                "name" to memberName.trim(),
                "role" to "member",
                "joinedAt" to System.currentTimeMillis()
            )

            db.collection("homes")
                .document(homeId)
                .collection("members")
                .add(memberData)
                .await()

            loading = false
            message = "✅ Tham gia thành công!"

            // Điều hướng sang Dashboard
            navController.navigate(Screen.Dashboard.route) {
                popUpTo(Screen.JoinHome.route) { inclusive = true }
            }

        } catch (e: Exception) {
            loading = false
            message = "❌ Lỗi: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🏡 THAM GIA NGÔI NHÀ",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Nhập mã nhà và tên của bạn",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = homeCode,
            onValueChange = { homeCode = it.uppercase() },
            label = { Text("Mã nhà *") },
            placeholder = { Text("Ví dụ: ABC123") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Nhập mã nhà 6 ký tự do chủ nhà cung cấp") }
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = memberName,
            onValueChange = { memberName = it },
            label = { Text("Tên của bạn *") },
            placeholder = { Text("Ví dụ: Nguyễn Văn B") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Tên này sẽ hiển thị cho các thành viên khác") }
        )

        Spacer(Modifier.height(24.dp))

        if (loading) {
            CircularProgressIndicator()
        } else {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Hủy")
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = {
                        coroutineScope.launch {
                            joinHomeWithMember()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = homeCode.isNotBlank() && memberName.isNotBlank()
                ) {
                    Text("Tham gia")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (message.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        message.startsWith("✅") -> MaterialTheme.colorScheme.primaryContainer
                        message.startsWith("⚠️") -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}