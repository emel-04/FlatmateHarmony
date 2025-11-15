package com.example.flatmateharmony.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flatmateharmony.R
import com.example.flatmateharmony.navigation.Screen
import com.example.flatmateharmony.data.HomeRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun HelloScreen(navController: NavController) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    val homeRepo = remember { HomeRepository() }
    var isCheckingHome by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    // ✅ Kiểm tra xem user đã có nhà chưa
    LaunchedEffect(userId) {
        if (userId != null) {
            coroutineScope.launch {
                val homeInfo = homeRepo.getHomeInfoByUser(userId)
                if (homeInfo != null) {
                    // User đã có nhà → Tự động chuyển đến Dashboard
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Hello.route) { inclusive = true }
                    }
                } else {
                    // User chưa có nhà → Hiển thị các options
                    isCheckingHome = false
                }
            }
        } else {
            isCheckingHome = false
        }
    }

    if (isCheckingHome) {
        // Hiển thị loading khi đang kiểm tra
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        // Hiển thị options khi user chưa có nhà
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_home_logo),
                contentDescription = null,
                modifier = Modifier.size(150.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Flatmate Harmony",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Biến ngôi nhà chung thành không gian sống hài hòa.\nQuản lý chi phí, công việc và giao tiếp dễ dàng.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { navController.navigate(Screen.AddHome.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🏠 Xây một ngôi nhà")
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { navController.navigate(Screen.JoinHome.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⬆️ Trở thành thành viên")
            }
        }
    }
}
