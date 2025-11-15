package com.example.flatmateharmony

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.example.flatmateharmony.navigation.NavGraph
import com.example.flatmateharmony.ui.theme.FlatmateHarmonyTheme
import com.google.firebase.FirebaseApp   // ✅ thêm dòng này

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Khởi tạo Firebase (bắt buộc, nếu không sẽ bị lỗi "FirebaseApp not initialized")
        FirebaseApp.initializeApp(this)

        setContent {
            FlatmateHarmonyTheme {
                val navController = rememberNavController()
                NavGraph(navController)
            }
        }
    }
}
