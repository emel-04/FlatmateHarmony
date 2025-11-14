package com.example.flatmateharmony.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flatmateharmony.navigation.Screen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.flatmateharmony.utils.AuthManager
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavController) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    var loading by remember { mutableStateOf(false) }
    val authManager = remember { AuthManager(context) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ĐĂNG NHẬP", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Email hoặc số điện thoại") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mật khẩu") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (passwordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu"
                    )
                }
            },
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (phone.isNotEmpty() && password.isNotEmpty()) {
                    loading = true
                    auth.signInWithEmailAndPassword(phone, password)
                        .addOnCompleteListener { task ->
                            loading = false
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                if (user != null) {
                                    // ✅ Lưu session vào SharedPreferences
                                    coroutineScope.launch {
                                        authManager.saveUserSession(user)
                                    }
                                    
                                    // ✅ Lưu email user vào Firestore
                                    val userData = mapOf("email" to (user.email ?: phone))
                                    db.collection("users").document(user.uid).set(userData)
                                        .addOnSuccessListener {
                                            println("Đã lưu email người dùng: ${user.email}")
                                        }
                                        .addOnFailureListener { e ->
                                            println("Lỗi lưu user: $e")
                                        }
                                }

                                Toast.makeText(context, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                                navController.navigate(Screen.Hello.route) {
                                    // Xóa back stack để không quay lại màn hình login
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            } else {
                                Toast.makeText(context, "Sai thông tin đăng nhập", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Toast.makeText(context, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Đăng nhập")
        }

        Spacer(Modifier.height(8.dp))
        if (loading) {
            CircularProgressIndicator()
        }

        TextButton(onClick = { navController.navigate(Screen.ForgotPass.route) }) {
            Text("Quên mật khẩu?")
        }
        TextButton(onClick = { navController.navigate(Screen.SignUp.route) }) {
            Text("Chưa có tài khoản? Đăng ký ngay")
        }
    }
}
