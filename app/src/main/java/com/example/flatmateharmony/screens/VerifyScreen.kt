package com.example.flatmateharmony.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.flatmateharmony.data.Home
import com.example.flatmateharmony.navigation.AppHeader
import com.example.flatmateharmony.navigation.AppNavigationBar
import com.example.flatmateharmony.viewmodel.ChatViewModel
import com.example.flatmateharmony.model.ChatMessage
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyScreen(
    navController: NavController,
    homeCode: String,
    viewModel: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: return
    val uiState by viewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }

    // chỉ load một lần cho homeCode + userId
    LaunchedEffect(homeCode, currentUserId) {
        viewModel.loadChat(homeCode, currentUserId)
    }

    val listState = rememberLazyListState()
    
    // Auto scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            AppHeader(uiState.home)
        },
        bottomBar = {
            AppNavigationBar(navController, uiState.home)
        },
        containerColor = Color(0xFFF5F7FA)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF6366F1))
                }
            } else {
                // Chat messages area
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFF5F7FA)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.messages) { msg ->
                        MessageBubble(
                            message = msg,
                            isCurrentUser = msg.senderId == currentUserId
                        )
                    }
                }

                // Input field and send button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { 
                                Text(
                                    "Nhập tin nhắn...",
                                    color = Color(0xFF9CA3AF)
                                ) 
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color(0xFFE5E7EB),
                                focusedBorderColor = Color(0xFF6366F1)
                            ),
                            singleLine = true
                        )
                        
                        // Green send button with checkmark
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                                .clickable {
                                    if (messageText.isNotBlank()) {
                                        viewModel.sendMessage(messageText.trim(), currentUserId)
                                        messageText = ""
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Gửi",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, isCurrentUser: Boolean) {
    val timeText = formatMessageTime(message.timestamp)
    val senderName = if (message.senderName.isNotEmpty()) message.senderName else "Ẩn danh"
    val displayName = if (isCurrentUser) "Bạn" else senderName
    val firstLetter = senderName.firstOrNull()?.uppercaseChar() ?: '?'

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isCurrentUser) {
            // Avatar for other users (left side)
            Avatar(firstLetter, modifier = Modifier.padding(end = 8.dp))
        }
        
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
        ) {
            // Sender name above message
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color(0xFF64748B),
                modifier = Modifier.padding(
                    bottom = 4.dp,
                    start = if (isCurrentUser) 0.dp else 0.dp,
                    end = if (isCurrentUser) 0.dp else 0.dp
                )
            )
            
            // Message bubble with timestamp
            Row(
                horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                if (isCurrentUser) {
                    // Timestamp on left for current user
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color(0xFF9CA3AF),
                        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp)
                    )
                }
                
                // Message bubble
                Surface(
                    modifier = Modifier.widthIn(max = 280.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isCurrentUser) Color.White else Color(0xFFE5E7EB),
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        color = Color(0xFF1A1F36)
                    )
                }
                
                if (!isCurrentUser) {
                    // Timestamp on right for other users
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color(0xFF9CA3AF),
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                }
            }
        }
        
        if (isCurrentUser) {
            // Avatar for current user (right side)
            Avatar(firstLetter, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun Avatar(firstLetter: Char, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFF8B9DC3)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = firstLetter.toString(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            color = Color.White
        )
    }
}

fun formatMessageTime(timestamp: Long): String {
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(date)
}
