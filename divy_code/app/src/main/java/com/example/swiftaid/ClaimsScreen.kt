package com.example.swiftaid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ClaimsScreen(onBack: () -> Unit) {
    val isDark = LocalIsDark.current
    val sharedState = LocalSharedState.current
    val claims = sharedState.claims
    
    var showNewClaimDialog by remember { mutableStateOf(false) }
    var selectedClaim by remember { mutableStateOf<Claim?>(null) }
    
    val backgroundBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF3683FF), Color(0xFF000000)),
            startY = 0f, endY = 900f
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF3B82F6), Color(0xFFFFFFFF)),
            startY = 0f, endY = 1000f
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) Color.White else Color.Black
                        )
                    }
                    Text(
                        "Insurance Claims",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color.Black
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showNewClaimDialog = true },
                    containerColor = if (isDark) Color.White else Color(0xFF1A1A1A),
                    contentColor = if (isDark) Color.Black else Color.White,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Claim")
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            if (showNewClaimDialog) {
                NewClaimDialog(
                    onDismiss = { showNewClaimDialog = false },
                    onAdd = { newClaim ->
                        sharedState.claims.add(0, newClaim)
                        showNewClaimDialog = false
                    }
                )
            }

            if (selectedClaim != null) {
                ClaimDetailsDialog(
                    claim = selectedClaim!!,
                    onDismiss = { selectedClaim = null }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Stats Row
                val activeCount = claims.count { it.status == "In Review" || it.status == "Approved" }
                val resolvedCount = claims.count { it.status == "Completed" }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ClaimStatCard("Active", activeCount.toString(), Color(0xFF3B82F6), Modifier.weight(1f))
                    ClaimStatCard("Resolved", resolvedCount.toString(), Color(0xFF18A558), Modifier.weight(1f))
                }

                Text(
                    "RECENT CLAIMS",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                    color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF6B7280)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(claims) { claim ->
                        ClaimItemCard(claim, onClick = { selectedClaim = claim })
                    }
                }
            }
        }
    }
}

@Composable
fun ClaimDetailsDialog(claim: Claim, onDismiss: () -> Unit) {
    val isDark = LocalIsDark.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1A1A1A) else Color.White,
        title = {
            Column {
                Text(claim.title, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color.Black)
                Text(claim.id, style = MaterialTheme.typography.bodySmall, color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Status", fontSize = 12.sp, color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray)
                        Text(claim.status, fontWeight = FontWeight.Bold, color = claim.statusColor)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Amount", fontSize = 12.sp, color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray)
                        Text(claim.amount, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color.Black)
                    }
                }
                
                HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                
                Column {
                    Text("Date Submitted", fontSize = 12.sp, color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray)
                    Text(claim.date, color = if (isDark) Color.White else Color.Black)
                }

                if (claim.description.isNotBlank()) {
                    Column {
                        Text("Description", fontSize = 12.sp, color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray)
                        Text(claim.description, color = if (isDark) Color.White else Color.Black)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF3B82F6))
            }
        }
    )
}

@Composable
fun NewClaimDialog(onDismiss: () -> Unit, onAdd: (Claim) -> Unit) {
    val isDark = LocalIsDark.current
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1A1A1A) else Color.White,
        title = { Text("New Insurance Claim", fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Reason / Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = if (isDark) Color.White else Color.Black,
                        unfocusedTextColor = if (isDark) Color.White else Color.Black
                    )
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Estimated Amount ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = if (isDark) Color.White else Color.Black,
                        unfocusedTextColor = if (isDark) Color.White else Color.Black
                    )
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = if (isDark) Color.White else Color.Black,
                        unfocusedTextColor = if (isDark) Color.White else Color.Black
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && amount.isNotBlank()) {
                        val newClaim = Claim(
                            id = "CLM-${(1000..9999).random()}",
                            title = title,
                            date = "Oct 24, 2023", // Simplified for now
                            status = "In Review",
                            amount = if (amount.startsWith("$")) amount else "$$amount",
                            statusColor = Color(0xFFE07000),
                            description = description
                        )
                        onAdd(newClaim)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color.White else Color(0xFF1A1A1A),
                    contentColor = if (isDark) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Submit Claim", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray)
            }
        }
    )
}

@Composable
fun ClaimStatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val isDark = LocalIsDark.current
    Surface(
        modifier = modifier,
        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.03f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, fontSize = 12.sp, color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun ClaimItemCard(claim: Claim, onClick: () -> Unit) {
    val isDark = LocalIsDark.current
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.02f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(claim.statusColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = claim.statusColor, modifier = Modifier.size(20.dp))
            }
            
            Column(modifier = Modifier.padding(horizontal = 12.dp).weight(1f)) {
                Text(claim.title, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color.Black)
                Text("${claim.id} • ${claim.date}", fontSize = 12.sp, color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray)
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(claim.amount, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color.Black)
                Text(claim.status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = claim.statusColor)
            }
            
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.padding(start = 8.dp).size(20.dp))
        }
    }
}
