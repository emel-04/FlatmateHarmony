package com.example.flatmateharmony.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flatmateharmony.R
import com.example.flatmateharmony.navigation.Screen
import com.example.flatmateharmony.data.HomeRepository
import com.example.flatmateharmony.utils.AuthManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelloScreen(navController: NavController) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    val homeRepo = remember { HomeRepository() }
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    var isCheckingHome by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    // ✅ Hàm logout và quay lại Login
    fun handleBackToLogin() {
        authManager.clearSession() // Xóa session và đăng xuất
        navController.navigate(Screen.Login.route) {
            popUpTo(0) { inclusive = true } // Xóa toàn bộ back stack
        }
    }

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

    Scaffold(
        topBar = {
            if (!isCheckingHome) {
                // Chỉ hiển thị TopBar khi không phải đang loading
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = { handleBackToLogin() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Quay lại đăng nhập",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { paddingValues ->
        if (isCheckingHome) {
            // Hiển thị loading khi đang kiểm tra
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Hiển thị options khi user chưa có nhà
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
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
}
