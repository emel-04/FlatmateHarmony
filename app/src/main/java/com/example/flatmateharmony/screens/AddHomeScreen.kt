package com.example.flatmateharmony.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flatmateharmony.navigation.Screen
import com.example.flatmateharmony.utils.rememberCurrencyVisualTransformation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

@Composable
fun AddHomeScreen(navController: NavController) {
    var address by remember { mutableStateOf("") }
    var rent by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val db = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    val coroutineScope = rememberCoroutineScope()

    fun generateHomeCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }

    suspend fun createHomeWithMember() {
        if (user == null) {
            message = "⚠️ Bạn cần đăng nhập trước."
            return
        }

        if (address.isBlank() || rent.isBlank() || ownerName.isBlank()) {
            message = "⚠️ Vui lòng nhập đầy đủ thông tin."
            return
        }

        loading = true

        try {
            val homeCode = generateHomeCode()
            val homeData = hashMapOf(
                "homeCode" to homeCode,
                "ownerId" to user.uid,
                "address" to address,
                "rent" to rent.toDoubleOrNull(),
                "members" to listOf(user.uid),
                "createdAt" to System.currentTimeMillis()
            )

            // Tạo home document
            val homeRef = db.collection("homes").add(homeData).await()
            val homeId = homeRef.id

            // Tạo member document cho chủ nhà
            val memberData = hashMapOf(
                "userId" to user.uid,
                "name" to ownerName.trim(),
                "role" to "owner",
                "joinedAt" to System.currentTimeMillis()
            )

            db.collection("homes")
                .document(homeId)
                .collection("members")
                .add(memberData)
                .await()

            loading = false
            message = "✅ Tạo nhà thành công! Mã nhà của bạn là: $homeCode"

            // Điều hướng sang Dashboard sau khi tạo nhà thành công
            navController.navigate(Screen.Dashboard.route) {
                popUpTo(Screen.AddHome.route) { inclusive = true }
            }
        } catch (e: Exception) {
            loading = false
            message = "❌ Lỗi khi tạo nhà: ${e.message}"
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
            text = "🏠 XÂY NHÀ MỚI",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Điền thông tin để tạo nhà mới",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = ownerName,
            onValueChange = { ownerName = it },
            label = { Text("Tên của bạn *") },
            placeholder = { Text("Ví dụ: Nguyễn Văn A") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Tên này sẽ hiển thị cho các thành viên khác") }
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Địa chỉ *") },
            placeholder = { Text("Ví dụ: 123 Nguyễn Huệ, Q1") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = rent,
            onValueChange = { newValue ->
                // Chỉ cho phép nhập số
                val digitsOnly = newValue.filter { it.isDigit() }
                rent = digitsOnly
            },
            label = { Text("Giá thuê (VND) *") },
            placeholder = { Text("Ví dụ: 5.000.000") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = rememberCurrencyVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            suffix = { Text("đ") }
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
                            createHomeWithMember()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = ownerName.isNotBlank() && address.isNotBlank() && rent.isNotBlank()
                ) {
                    Text("Tạo nhà")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (message.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (message.startsWith("✅"))
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
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