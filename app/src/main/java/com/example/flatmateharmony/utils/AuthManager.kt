package com.example.flatmateharmony.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Quản lý session và authentication state
 * Lưu thông tin user vào SharedPreferences để persist qua các lần mở app
 */
class AuthManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "auth_prefs",
        Context.MODE_PRIVATE
    )
    
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_ID_TOKEN = "id_token"
        private const val KEY_LOGIN_TIMESTAMP = "login_timestamp"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }
    
    /**
     * Lưu thông tin user sau khi login thành công
     */
    suspend fun saveUserSession(user: FirebaseUser) {
        try {
            // Lấy ID token
            val idToken = user.getIdToken(false).await().token
            
            prefs.edit().apply {
                putString(KEY_USER_ID, user.uid)
                putString(KEY_USER_EMAIL, user.email ?: "")
                putString(KEY_ID_TOKEN, idToken)
                putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
                putBoolean(KEY_IS_LOGGED_IN, true)
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Kiểm tra xem user đã login chưa
     */
    fun isLoggedIn(): Boolean {
        // Kiểm tra cả SharedPreferences và Firebase Auth
        val savedLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val firebaseUser = auth.currentUser
        
        // Nếu Firebase Auth có user nhưng SharedPreferences không có -> sync lại
        if (firebaseUser != null && !savedLoggedIn) {
            // Sync lại session (async, không block)
            syncSessionFromFirebase(firebaseUser)
        }
        
        return savedLoggedIn && firebaseUser != null
    }
    
    /**
     * Lấy userId đã lưu
     */
    fun getSavedUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }
    
    /**
     * Lấy email đã lưu
     */
    fun getSavedEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }
    
    /**
     * Lấy ID token đã lưu
     */
    fun getSavedIdToken(): String? {
        return prefs.getString(KEY_ID_TOKEN, null)
    }
    
    /**
     * Refresh ID token (khi cần)
     */
    suspend fun refreshIdToken(): String? {
        return try {
            val user = auth.currentUser
            if (user != null) {
                val tokenResult = user.getIdToken(true).await()
                val newToken = tokenResult.token
                
                // Lưu token mới
                prefs.edit()
                    .putString(KEY_ID_TOKEN, newToken)
                    .apply()
                
                newToken
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Xóa session khi logout
     */
    fun clearSession() {
        prefs.edit().clear().apply()
        auth.signOut()
    }
    
    /**
     * Sync session từ Firebase Auth (khi Firebase có user nhưng SharedPreferences chưa có)
     */
    private fun syncSessionFromFirebase(user: FirebaseUser) {
        // Chạy async để không block UI
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val idToken = user.getIdToken(false).await().token
                
                prefs.edit().apply {
                    putString(KEY_USER_ID, user.uid)
                    putString(KEY_USER_EMAIL, user.email ?: "")
                    putString(KEY_ID_TOKEN, idToken)
                    putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
                    putBoolean(KEY_IS_LOGGED_IN, true)
                    apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Lấy Firebase User hiện tại
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }
}

