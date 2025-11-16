package com.example.flatmateharmony.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flatmateharmony.navigation.Screen

@Composable
fun ResetPasswordScreen(navController: NavController) {
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ĐỔI MẬT KHẨU", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = newPass, onValueChange = { newPass = it }, label = { Text("Mật khẩu mới") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = confirmPass, onValueChange = { confirmPass = it }, label = { Text("Nhập lại mật khẩu") })
        Spacer(Modifier.height(16.dp))
        Button(onClick = { navController.navigate(Screen.Login.route) }) {
            Text("Xác nhận")
        }
    }
}
