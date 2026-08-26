package com.example.swiftaid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimsScreen(
    onBack: () -> Unit,
    api: AuthApi? = null,
    tokenStorage: TokenStorage? = null,
    onMessage: (String) -> Unit = {}
) {
    val isDark = LocalIsDark.current
    val sharedState = LocalSharedState.current
    val scope = rememberCoroutineScope()
    val claims = sharedState.claims

    var showNewClaimDialog by remember { mutableStateOf(false) }
    var selectedClaim by remember { mutableStateOf<Claim?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun replaceClaims(remoteClaims: List<InsuranceClaimResponse>) {
        sharedState.claims.clear()
        sharedState.claims.addAll(remoteClaims.map { it.toClaim() })
    }

    suspend fun refreshSession(): Boolean {
        val currentApi = api ?: return false
        val refreshToken = sharedState.refreshToken.ifBlank { tokenStorage?.getRefreshToken().orEmpty() }
        if (refreshToken.isBlank()) return false
        val response = currentApi.refreshTokens(refreshToken)
        if (response.status != HttpStatusCode.OK) return false
        val body = response.body<AuthResponse>()
        sharedState.accessToken = body.accessToken
        sharedState.refreshToken = body.refreshToken
        sharedState.userId = extractUserIdFromJwt(body.accessToken)
        tokenStorage?.saveTokens(body.accessToken, body.refreshToken)
        return true
    }

    suspend fun <T> authorizedClaimsRequest(
        request: suspend (String) -> HttpResponse,
        parse: suspend (HttpResponse) -> T
    ): T {
        val currentAccessToken = sharedState.accessToken
        if (currentAccessToken.isBlank()) throw IllegalStateException("Please sign in again.")

        val response = request(currentAccessToken)
        val finalResponse = if (response.status == HttpStatusCode.Unauthorized && refreshSession()) {
            request(sharedState.accessToken)
        } else {
            response
        }

        if (finalResponse.status.value !in 200..299) {
            throw IllegalStateException(finalResponse.claimsErrorMessage())
        }
        return parse(finalResponse)
    }

    LaunchedEffect(api, sharedState.accessToken) {
        val currentApi = api ?: return@LaunchedEffect
        if (sharedState.accessToken.isBlank()) return@LaunchedEffect
        isLoading = true
        errorMessage = null
        runCatching {
            authorizedClaimsRequest(
                request = { token -> currentApi.loadInsuranceClaims(token) },
                parse = { response -> response.body<List<InsuranceClaimResponse>>() }
            )
        }
            .onSuccess { replaceClaims(it) }
            .onFailure { errorMessage = it.message ?: "Could not load insurance claims." }
        isLoading = false
    }

    val backgroundBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF3683FF), Color(0xFF000000)),
            startY = 0f,
            endY = 900f
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF3B82F6), Color(0xFFFFFFFF)),
            startY = 0f,
            endY = 1000f
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                    isSubmitting = isSubmitting,
                    onSubmit = { title, amount, description ->
                        val currentApi = api
                        if (currentApi == null || sharedState.accessToken.isBlank()) {
                            sharedState.claims.add(0, createLocalClaim(title, amount, description))
                            showNewClaimDialog = false
                            return@NewClaimDialog
                        }

                        scope.launch {
                            isSubmitting = true
                            errorMessage = null
                            runCatching {
                                authorizedClaimsRequest(
                                    request = { token ->
                                        currentApi.createInsuranceClaim(
                                            token,
                                            InsuranceClaimRequest(
                                                title = title,
                                                amount = amount,
                                                description = description.takeIf { it.isNotBlank() }
                                            )
                                        )
                                    },
                                    parse = { response -> response.body<InsuranceClaimResponse>() }
                                )
                            }.onSuccess { savedClaim ->
                                sharedState.claims.add(0, savedClaim.toClaim())
                                showNewClaimDialog = false
                                onMessage("Claim submitted.")
                            }.onFailure { error ->
                                errorMessage = error.message ?: "Could not submit claim."
                            }
                            isSubmitting = false
                        }
                    }
                )
            }

            selectedClaim?.let { claim ->
                ClaimDetailsDialog(claim = claim, onDismiss = { selectedClaim = null })
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
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

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                        color = Color(0xFFE74C3C),
                        fontWeight = FontWeight.Medium
                    )
                }

                when {
                    isLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = if (isDark) Color.White else Color(0xFF1A1A1A))
                    }
                    claims.isEmpty() -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (sharedState.insurances.isEmpty()) {
                                "Connect your insurance and add the required policy details to create claims."
                            } else {
                                "Nothing to show here"
                            },
                            color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    else -> LazyColumn(
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
}

private suspend fun HttpResponse.claimsErrorMessage(): String {
    val detail = runCatching { body<ErrorResponse>().detail }.getOrNull()
    if (!detail.isNullOrBlank()) return detail
    val rawBody = runCatching { bodyAsText() }.getOrDefault("")
    return rawBody.ifBlank { "Request failed with status ${status.value}." }
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
fun NewClaimDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit,
    isSubmitting: Boolean = false
) {
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
                    label = { Text("Estimated Amount") },
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
                        onSubmit(title.trim(), amount.trim().normalizedClaimAmount(), description.trim())
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color.White else Color(0xFF1A1A1A),
                    contentColor = if (isDark) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isSubmitting) "Submitting..." else "Submit Claim", fontWeight = FontWeight.Bold)
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

private fun InsuranceClaimResponse.toClaim(): Claim {
    return Claim(
        id = "CLM-${claimId.take(8).uppercase(Locale.US)}",
        title = title,
        date = submittedAt.toClaimDate(),
        status = status,
        amount = amount.normalizedClaimAmount(),
        statusColor = status.claimStatusColor(),
        description = description.orEmpty()
    )
}

private fun createLocalClaim(title: String, amount: String, description: String): Claim {
    return Claim(
        id = "LOCAL-${System.currentTimeMillis().toString().takeLast(6)}",
        title = title,
        date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date()),
        status = "In Review",
        amount = amount.normalizedClaimAmount(),
        statusColor = "In Review".claimStatusColor(),
        description = description
    )
}

private fun String.normalizedClaimAmount(): String {
    val trimmed = trim()
    return if (trimmed.startsWith("$") || trimmed.startsWith("Rs.", ignoreCase = true)) {
        trimmed
    } else {
        "Rs. $trimmed"
    }
}

private fun String.claimStatusColor(): Color = when (lowercase(Locale.US)) {
    "approved", "completed" -> Color(0xFF18A558)
    "rejected", "denied" -> Color(0xFFE74C3C)
    else -> Color(0xFFE07000)
}

private fun String.toClaimDate(): String {
    val datePart = substringBefore("T").takeIf { it.length == 10 } ?: return this
    return runCatching {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        outputFormat.format(inputFormat.parse(datePart) ?: return this)
    }.getOrDefault(this)
}
