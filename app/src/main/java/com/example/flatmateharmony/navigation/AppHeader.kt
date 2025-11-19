package com.example.flatmateharmony.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flatmateharmony.data.Home

@Composable
fun AppHeader(homeInfo: Home?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 🌈 Tiêu đề FlatmateHarmony
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)) {
                    append("Flatmate")
                }
                withStyle(style = SpanStyle(color = Color(0xFFE65100), fontWeight = FontWeight.Bold)) {
                    append("Harmony")
                }
            },
            fontSize = 22.sp,
        )

        // 🏠 Địa chỉ
        if (homeInfo != null) {
            Text(
                text = homeInfo.address,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            )
        } else {
            Text(
                "Đang tải thông tin nhà...",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
            )
        }

        // ─────────────── Gạch ngang chia
        Divider(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(0.9f),
            color = Color(0xFFE0E0E0),
            thickness = 1.dp
        )
    }
}

// 💰 Hàm định dạng tiền tệ
fun formatCurrency(value: Long): String {
    return value.toString().reversed().chunked(3).joinToString(".").reversed()
}
