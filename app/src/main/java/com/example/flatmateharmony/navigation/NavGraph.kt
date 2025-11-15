package com.example.flatmateharmony.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.flatmateharmony.screens.*
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.flatmateharmony.utils.AuthManager
import com.example.flatmateharmony.data.HomeRepository


sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object ForgotPass : Screen("forgot_pass")
    object ResetPass : Screen("reset_pass")
    object SignUp : Screen("signup")
    object OTPVerify : Screen("otp_verify")
    object Hello : Screen("hello")
    object AddHome : Screen("add_home")
    object JoinHome : Screen("join_home")
    object Dashboard : Screen("dashboard")
    object Finance : Screen("finance")
    object Tasks : Screen("tasks")
    object Chat : Screen("chat")
    object Profile : Screen("profile")
}

@Composable
fun NavGraph(navController: NavHostController) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    val homeRepo = remember { HomeRepository() }
    var hasCheckedSession by remember { mutableStateOf(false) }
    
    // Kiểm tra session khi app start (chỉ chạy 1 lần)
    LaunchedEffect(Unit) {
        if (!hasCheckedSession) {
            hasCheckedSession = true
            if (authManager.isLoggedIn()) {
                val userId = authManager.getCurrentUser()?.uid
                if (userId != null) {
                    // Kiểm tra xem user đã có nhà chưa
                    val homeInfo = homeRepo.getHomeInfoByUser(userId)
                    if (homeInfo != null) {
                        // User đã có nhà → Tự động chuyển đến Dashboard
                        navController.navigate(Screen.Dashboard.route) {
                            // Xóa toàn bộ back stack
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        // User chưa có nhà → Chuyển đến Hello screen
                        navController.navigate(Screen.Hello.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }
        }
    }
    
    NavHost(navController = navController, startDestination = Screen.Onboarding.route) {
        composable(Screen.Onboarding.route) { OnboardingScreen(navController) }
        composable(Screen.Login.route) { LoginScreen(navController) }
        composable(Screen.ForgotPass.route) { ForgotPasswordScreen(navController) }
        composable(Screen.ResetPass.route) { ResetPasswordScreen(navController) }
        composable(Screen.SignUp.route) { SignUpScreen(navController) }
        composable(Screen.OTPVerify.route) { OTPVerificationScreen(navController) }
        composable(Screen.Hello.route) { HelloScreen(navController) }
        composable(Screen.AddHome.route) { AddHomeScreen(navController) }
        composable(Screen.JoinHome.route) { JoinHomeScreen(navController) }
        composable(Screen.Dashboard.route) { DashboardScreen(navController) }
        composable(
            route = Screen.Finance.route + "/{homeCode}",
            arguments = listOf(navArgument("homeCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val homeCode = backStackEntry.arguments?.getString("homeCode") ?: ""
            FinanceScreen(navController, homeCode)
        }

        composable(
            route = Screen.Tasks.route + "/{homeCode}",
            arguments = listOf(navArgument("homeCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val homeCode = backStackEntry.arguments?.getString("homeCode") ?: ""
            NotedScreen(navController,homeCode)   // ✅ TRUYỀN VÀO
        }

        composable(
            route = Screen.Chat.route + "/{homeCode}",
            arguments = listOf(navArgument("homeCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val homeCode = backStackEntry.arguments?.getString("homeCode") ?: ""
            VerifyScreen(navController, homeCode)
        }

        composable(route = Screen.Profile.route + "/{homeCode}",
            arguments = listOf(navArgument("homeCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val homeCode = backStackEntry.arguments?.getString("homeCode") ?: ""
            CreateTransactionScreen(navController, homeCode)
        }

    }




}
