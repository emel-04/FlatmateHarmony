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
fun OTPVerificationScreen(navController: NavController) {
    var otp by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("XÁC THỰC OTP", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = otp, onValueChange = { otp = it }, label = { Text("Nhập OTP") })
        Spacer(Modifier.height(16.dp))
        Button(onClick = { navController.navigate(Screen.Login.route) }) {
            Text("Xác thực")
        }
    }
}
