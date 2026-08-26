package com.example.policeapp.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.livedata.observeAsState
import com.example.policeapp.AppState
import com.example.policeapp.data.model.SosEventData
import com.example.policeapp.data.model.SosServiceResponse
import com.example.policeapp.data.model.SosType
import com.example.policeapp.data.model.toSosRequest
import com.example.policeapp.firebase.FirebasePoliceRepository
import com.example.policeapp.theme.AccentBlue
import com.example.policeapp.theme.AccentGold
import com.example.policeapp.theme.AccentRed
import com.example.policeapp.theme.BackgroundBlack
import com.example.policeapp.theme.CardBackground
import com.example.policeapp.theme.GradientBlueEnd
import com.example.policeapp.theme.GradientBlueStart
import com.example.policeapp.theme.GreenSuccess
import com.example.policeapp.theme.PrimaryBlue
import com.example.policeapp.theme.SurfaceBorder
import com.example.policeapp.theme.TextPrimary
import com.example.policeapp.theme.TextSecondary
import kotlinx.coroutines.launch

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

private fun getRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hrs ago"
        else -> "$days days ago"
    }
}

private fun formatPhoneNumber(phone: String): String {
    val digits = phone.replace(Regex("[^\\d]"), "")
    return when {
        digits.length == 10 -> "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
        digits.length > 10 -> "+${digits.substring(0, digits.length - 10)} (${digits.substring(digits.length - 10, digits.length - 7)}) ${digits.substring(digits.length - 7, digits.length - 4)}-${digits.substring(digits.length - 4)}"
        else -> phone
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosDetailScreen(
    requestId: String,
    onBackClick: () -> Unit,
    onMarkCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val firebaseRepository = remember { FirebasePoliceRepository() }
    val coroutineScope = rememberCoroutineScope()
    var sosEvent by remember(requestId) { mutableStateOf<SosEventData?>(null) }
    var isLoadingFirebaseRequest by remember(requestId) { mutableStateOf(true) }
    var actionMessage by remember(requestId) { mutableStateOf<String?>(null) }
    var isAccepting by remember(requestId) { mutableStateOf(false) }
    val liveSosEvent by firebaseRepository.sosEventUpdates.observeAsState()
    val currentResponder = AppState.currentResponder

    var contentVisible by remember { mutableStateOf(false) }

    DisposableEffect(requestId) {
        val registration = firebaseRepository.listenToSosEvent(requestId)
        onDispose { registration.remove() }
    }

    LaunchedEffect(requestId) {
        contentVisible = true
        isLoadingFirebaseRequest = true
        firebaseRepository.getSosEventById(requestId)
            .onSuccess { firebaseEvent ->
                sosEvent = firebaseEvent
            }
            .onFailure {
                sosEvent = null
            }
        isLoadingFirebaseRequest = false
    }

    LaunchedEffect(liveSosEvent, requestId) {
        if (liveSosEvent?.id == requestId) {
            sosEvent = liveSosEvent
            isLoadingFirebaseRequest = false
        }
    }

    var showRecordSection by remember { mutableStateOf(false) }
    var recordText by remember { mutableStateOf("") }
    val recordPhotos = remember { mutableStateListOf<String>() }
    
    var showConfirmationDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BackgroundBlack,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(GradientBlueStart, GradientBlueEnd)
                        )
                    )
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = "SOS Details",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = TextPrimary
                    )
                )
            }
        }
    ) { paddingValues ->
        val currentEvent = sosEvent
        if (currentEvent == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLoadingFirebaseRequest) "Loading SOS..." else "Request not found",
                    color = TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            val request = currentEvent.toSosRequest()
            val serviceResponse = currentEvent.responseFor(currentResponder?.serviceType)
            val acceptedResponderId = serviceResponse?.responderId.orEmpty()
            val acceptedByCurrentResponder = acceptedResponderId.equals(currentResponder?.id.orEmpty(), ignoreCase = true) &&
                acceptedResponderId.isNotBlank()
            val acceptedByOtherResponder = acceptedResponderId.isNotBlank() && !acceptedByCurrentResponder
            val canRespond = currentResponder != null && acceptedResponderId.isBlank() && !request.isCompleted
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SOS Type Badge
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -40 }
                ) {
                    SosTypeBadge(sosType = request.sosType)
                }

                // Person Name
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(500, delayMillis = 100)) + slideInVertically(tween(500, delayMillis = 100)) { -30 }
                ) {
                    Text(
                        text = request.personName,
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Contact Information Card
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(500, delayMillis = 200)) + slideInVertically(tween(500, delayMillis = 200)) { 40 }
                ) {
                    InfoCard(title = "CONTACT INFORMATION") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Phone Number",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = formatPhoneNumber(request.phoneNumber),
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${request.phoneNumber}")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Call Now",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                // Location Card
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(500, delayMillis = 300)) + slideInVertically(tween(500, delayMillis = 300)) { 40 }
                ) {
                    InfoCard(title = "LOCATION") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CoordinateChip(
                                label = "Latitude",
                                value = String.format("%.6f", request.latitude),
                                modifier = Modifier.weight(1f)
                            )
                            CoordinateChip(
                                label = "Longitude",
                                value = String.format("%.6f", request.longitude),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = AccentRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = request.address,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Static map preview placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardBackground.copy(alpha = 0.8f))
                                .border(1.dp, SurfaceBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = AccentRed,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${String.format("%.4f", request.latitude)}, ${String.format("%.4f", request.longitude)}",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Map Preview",
                                    color = TextSecondary.copy(alpha = 0.6f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val uri = Uri.parse("https://www.google.com/maps?q=${request.latitude},${request.longitude}")
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Open in Google Maps",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                // Timing Card
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(500, delayMillis = 400)) + slideInVertically(tween(500, delayMillis = 400)) { 40 }
                ) {
                    InfoCard(title = "TIMING") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Requested: ${formatTimestamp(request.timestamp)}",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = getRelativeTime(request.timestamp),
                                    color = AccentGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Completion Card (if resolved)
                if (request.isCompleted && request.completedTimestamp != null) {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(500, delayMillis = 420)) + slideInVertically(tween(500, delayMillis = 420)) { 40 }
                    ) {
                        InfoCard(title = "COMPLETION") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = GreenSuccess,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Resolved: ${formatTimestamp(request.completedTimestamp)}",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Completed successfully",
                                        color = GreenSuccess,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Add Record Section
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(500, delayMillis = 450)) + slideInVertically(tween(500, delayMillis = 450)) { 40 }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardBackground.copy(alpha = 0.8f))
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CASE RECORD",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp
                            )
                            if (!showRecordSection) {
                                IconButton(
                                    onClick = { showRecordSection = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Record",
                                        tint = PrimaryBlue
                                    )
                                }
                            }
                        }

                        if (showRecordSection) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = recordText,
                                onValueChange = { recordText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Enter details about this SOS...") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = PrimaryBlue,
                                    unfocusedBorderColor = SurfaceBorder,
                                    cursorColor = PrimaryBlue
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Photos (Max 5)",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                repeat(5) { index ->
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (index < recordPhotos.size) PrimaryBlue else BackgroundBlack)
                                            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                                            .clickable { 
                                                if (index == recordPhotos.size && recordPhotos.size < 5) {
                                                    recordPhotos.add("photo_${recordPhotos.size + 1}")
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (index < recordPhotos.size) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                                        } else if (index == recordPhotos.size) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = TextSecondary)
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = { 
                                    // Logic to save record
                                    showRecordSection = false 
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Save Record", fontWeight = FontWeight.Bold)
                            }
                        } else if (recordText.isNotEmpty()) {
                             Spacer(modifier = Modifier.height(8.dp))
                             Text(text = recordText, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }

                // Actions / Resolved Banner
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(500, delayMillis = 500)) + slideInVertically(tween(500, delayMillis = 500)) { 40 }
                ) {
                    if (request.isCompleted) {
                        // Resolved Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(GreenSuccess.copy(alpha = 0.1f))
                                .border(1.dp, GreenSuccess.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = GreenSuccess,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Resolved",
                                    color = GreenSuccess,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // Action Buttons
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            when {
                                acceptedByOtherResponder -> {
                                    AlreadyHandledBanner(serviceResponse = serviceResponse)
                                }
                                acceptedByCurrentResponder -> {
                                    ResponseStatusBanner(
                                        title = "You are responding",
                                        subtitle = "This ${currentResponder?.serviceType?.lowercase().orEmpty()} response is assigned to your service.",
                                        color = GreenSuccess
                                    )

                                    Button(
                                        onClick = { showConfirmationDialog = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Mark as Completed",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                                canRespond -> {
                                    Button(
                                        onClick = {
                                            val responder = currentResponder
                                            coroutineScope.launch {
                                                isAccepting = true
                                                actionMessage = null
                                                firebaseRepository.acceptSosEvent(
                                                    sosId = requestId,
                                                    responderId = responder.id,
                                                    responderRole = responder.serviceType,
                                                    responderName = responder.name,
                                                    responderPhone = responder.phoneNumber
                                                ).onFailure { error ->
                                                    actionMessage = error.message ?: "Could not accept this SOS request."
                                                }
                                                isAccepting = false
                                            }
                                        },
                                        enabled = !isAccepting,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isAccepting) "Accepting..." else "Respond to SOS",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                                else -> {
                                    ResponseStatusBanner(
                                        title = "Responder unavailable",
                                        subtitle = "Login or register this service before responding.",
                                        color = AccentGold
                                    )
                                }
                            }

                            actionMessage?.let { message ->
                                Text(
                                    text = message,
                                    color = AccentGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${request.phoneNumber}")
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp,
                                    PrimaryBlue
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Call Back",
                                    color = PrimaryBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showConfirmationDialog) {
        BasicAlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(CardBackground)
                .border(1.dp, SurfaceBorder, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(GreenSuccess.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = GreenSuccess,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Confirm Resolution",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Are you sure this SOS request has been successfully resolved?",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showConfirmationDialog = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
                    ) {
                        Text("Cancel", color = TextPrimary)
                    }
                    
                    Button(
                        onClick = {
                            showConfirmationDialog = false
                            coroutineScope.launch {
                                firebaseRepository.completeSosEvent(requestId)
                                onMarkCompleted()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Confirm", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun SosEventData.responseFor(serviceType: String?): SosServiceResponse? {
    return when (serviceType?.uppercase()) {
        "POLICE" -> policeResponse
        "HOSPITAL" -> hospitalResponse
        else -> null
    }
}

@Composable
private fun AlreadyHandledBanner(serviceResponse: SosServiceResponse?) {
    val responderName = serviceResponse?.responderName.orEmpty().ifBlank { "another service" }
    ResponseStatusBanner(
        title = "Already handled",
        subtitle = "This response was accepted by $responderName. No action is needed from your service.",
        color = AccentGold
    )
}

@Composable
private fun ResponseStatusBanner(
    title: String,
    subtitle: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = color,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SosTypeBadge(sosType: SosType) {
    val (badgeText, badgeColor) = when (sosType) {
        SosType.SELF -> "Self Called" to AccentRed
        SosType.OTHER -> "Called for Other" to AccentGold
        SosType.APP -> "App Called" to AccentBlue
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(badgeColor.copy(alpha = 0.1f))
            .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = badgeText,
            color = badgeColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground.copy(alpha = 0.8f))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun CoordinateChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BackgroundBlack)
            .border(1.dp, SurfaceBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
