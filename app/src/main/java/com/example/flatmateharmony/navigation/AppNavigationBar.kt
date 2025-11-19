package com.example.flatmateharmony.navigation

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.flatmateharmony.data.Home

data class BottomNavItem(val label: String, val icon: ImageVector, val route: String)

@Composable
fun AppNavigationBar(navController: NavController, homeInfo: Home?) {
    val items = listOf(
        BottomNavItem("Trang Chủ", Icons.Default.Home, Screen.Dashboard.route),
        BottomNavItem("Tài Chính", Icons.Default.AttachMoney, Screen.Finance.route),
        BottomNavItem("Công Việc", Icons.Default.List, Screen.Tasks.route),
        BottomNavItem("Chat", Icons.Default.Chat, Screen.Chat.route),
        BottomNavItem("Tôi", Icons.Default.Person, Screen.Profile.route)
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current

    // màu chính (cam nhạt như hình bạn gửi)
    val activeColor = Color(0xFFDD5E20)
    val backgroundColor = Color(0xFFFFE4C9)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        // Container bo tròn + đổ nền
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(backgroundColor)
                .fillMaxWidth()
                .height(70.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = currentRoute?.startsWith(item.route) == true

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            // Nếu cần homeCode mà chưa có thì báo
                            if (item.route != Screen.Dashboard.route && homeInfo == null) {
                                Toast.makeText(context, "Đang tải thông tin nhà...", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }

                            // Tạo route đích (có/không có homeCode)
                            val target = when (item.route) {
                                Screen.Finance.route -> "${Screen.Finance.route}/${homeInfo!!.homeCode}"
                                Screen.Tasks.route   -> "${Screen.Tasks.route}/${homeInfo!!.homeCode}"
                                Screen.Chat.route    -> "${Screen.Chat.route}/${homeInfo!!.homeCode}"
                                Screen.Profile.route -> "${Screen.Profile.route}/${homeInfo!!.homeCode}"
                                else -> Screen.Dashboard.route
                            }

                            // Kiểm tra nếu đã ở route đó rồi thì không navigate
                            if (currentRoute == target) {
                                return@clickable
                            }

                            // Điều hướng bottom navigation - đơn giản nhất
                            // Chỉ navigate, không pop gì cả để tránh lỗi
                            navController.navigate(target) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected) activeColor else Color.Black,
                        modifier = Modifier.size(26.dp)
                    )

                    // underline nhỏ dưới icon nếu được chọn
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .width(20.dp)
                                .height(2.dp)
                                .background(activeColor)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) activeColor else Color.Black
                    )
                }
            }
        }
    }
}
