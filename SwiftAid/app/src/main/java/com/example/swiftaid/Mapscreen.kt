package com.example.swiftaid

import androidx.compose.foundation.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.swiftaid.db.PoiEntity
import com.example.swiftaid.db.PoiRepository
import com.example.swiftaid.emergency.ResponderCacheStore
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import java.util.Locale
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text

// ─────────────────────────────────────────────
//  Data Models
// ─────────────────────────────────────────────

data class ServiceCategory(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val count: String? = null
)

data class RoadAlert(
    val title: String,
    val sub: String,
    val time: String,
    val icon: ImageVector,
    val color: Color
)

// ─────────────────────────────────────────────
//  Root Screen
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationScreen(
    hasLocationPermission: Boolean = false,
    onSettings: () -> Unit = {},
    onClaims: () -> Unit = {},
    onExploreClick: (String?) -> Unit = {}
) {
    val viewModel: MapViewModel = viewModel()
    val context = LocalContext.current
    val userLocation by viewModel.userLocation.collectAsState()
    val userAddress by viewModel.userAddress.collectAsState()
    val poiRepository = remember(context) { PoiRepository(context) }
    var nearbyPois by remember { mutableStateOf<List<PoiEntity>>(emptyList()) }
    var isPoiLoading by remember { mutableStateOf(false) }

    val scaffoldState = rememberBottomSheetScaffoldState()

    // ── Camera State ──
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            userLocation ?: LatLng(23.0225, 72.5714), 13f
        )
    }

    // ── Side Effects ──
    LaunchedEffect(hasLocationPermission) {
        nearbyPois = poiRepository.getAllPois(80)
        if (hasLocationPermission) {
            viewModel.fetchLocation(context)
        }
    }

    LaunchedEffect(userLocation) {
        userLocation?.let {
            isPoiLoading = true
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(it, 15f)
            )
            poiRepository.syncFromServer(it.latitude, it.longitude)
            ResponderCacheStore.refreshFromFirestore(context)
            nearbyPois = poiRepository.getAllPois(80)
            isPoiLoading = false
        }
    }

    val isDark = LocalIsDark.current
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
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 120.dp) // Space for floating nav
                ) {
                    SafetyProfileSection()

                    SectionLabel("Services")
                    ServiceGrid(
                        counts = serviceCountsByCategory(nearbyPois),
                        onCategoryClick = onExploreClick
                    )

                    SectionLabel("Nearest to You")
                    NearbyServicesRow(
                        pois = nearbyPois.take(12),
                        isLoading = isPoiLoading,
                        userLocation = userLocation,
                        onItemClick = { poi ->
                            userLocation?.let { location ->
                                showDirectionsDialog(context, poi, location)
                            }
                        }
                    )

                    SectionLabel("Road Alerts Nearby")
                    RoadAlertsList()
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            },
            sheetPeekHeight = 280.dp,
            sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            // Reduced transparency for better readability as requested
            sheetContainerColor = if (isDark) Color(0xFF111827).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f),
            sheetTonalElevation = 8.dp,
            sheetDragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f)
                )
            },
            containerColor = Color.Transparent
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // ✅ MAP — Background Content
                // Added a background placeholder to ensure visibility during load
                Box(
                    modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF0F172A) else Color(0xFFE2E8F0)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Loading Map...", 
                        color = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f),
                        fontWeight = FontWeight.Bold
                    )
                }

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    onMapLoaded = {
                        android.util.Log.d("MapsDebug", "✅ Map successfully loaded!")
                    },
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        myLocationButtonEnabled = false,
                        compassEnabled = true
                    ),
                    properties = MapProperties(
                        isMyLocationEnabled = hasLocationPermission,
                        mapType = MapType.NORMAL
                    )
                ) {
                    userLocation?.let {
                        Marker(
                            state = rememberMarkerState(position = it),
                            title = "RoadSOS",
                            snippet = "You are here"
                        )
                    }
                }

                // Address + Profile overlay at top
                MapOverlayHeader(
                    address = userAddress,
                    onProfileClick = onSettings
                )

                // ── Recenter Button ──
                IconButton(
                    onClick = { viewModel.fetchLocation(context) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1F2937) else Color.White)
                        .shadow(4.dp, CircleShape)
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Recenter",
                        tint = if (isDark) Color.White else Color.DarkGray
                    )
                }

                // ── Custom Zoom Controls ──
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd) // Moved to center end to avoid sheet overlap
                        .padding(end = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF1A1A1A) else Color.White)
                        .border(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.1f)
                            else Color.Black.copy(alpha = 0.1f),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    IconButton(
                        onClick = {
                            val zoom = cameraPositionState.position.zoom
                            cameraPositionState.move(CameraUpdateFactory.zoomTo(zoom + 1f))
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Zoom In",
                            tint = if (isDark) Color.White else Color.DarkGray
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.width(40.dp),
                        thickness = 0.5.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.LightGray
                    )
                    IconButton(
                        onClick = {
                            val zoom = cameraPositionState.position.zoom
                            cameraPositionState.move(CameraUpdateFactory.zoomTo(zoom - 1f))
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Zoom Out",
                            tint = if (isDark) Color.White else Color.DarkGray
                        )
                    }
                }
            }
        }

        // ── Floating Pill Navigation ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            SwiftaidBottomNav(
                onProfileClick = onSettings,
                onClaimsClick = onClaims,
                onExploreClick = onExploreClick
            )
        }

        // ── Floating SOS Button ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp) // Adjusted to sit perfectly in the pill gap
        ) {
            SOSFloatingButton()
        }
    }
}

// ─────────────────────────────────────────────
//  Map Overlay Header
// ─────────────────────────────────────────────

@Composable
fun MapOverlayHeader(
    address: String = "Locating...",
    onProfileClick: () -> Unit = {}
) {
    val isDark = LocalIsDark.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.White,
                shadowElevation = if (isDark) 0.dp else 4.dp,
                modifier = Modifier.padding(end = 8.dp),
                border = if (isDark) BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF18A558))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        address,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color.Black
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3B82F6))
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("AK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color.Black else Color.White)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF18A558))
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Safety Profile Section
// ─────────────────────────────────────────────

@Composable
fun SafetyProfileSection() {
    val isDark = LocalIsDark.current
    val sharedState = LocalSharedState.current
    val runtimeState = LocalSwiftAidRuntimeState.current
    val profileProgress = if (sharedState.isSafetyProfileComplete) 0.6f else 0.25f
    val contactsProgress = if (runtimeState.emergencyContactCount > 0) 0.2f else 0f
    val monitoringProgress = if (runtimeState.isMonitoring) 0.2f else 0f
    val progress = (profileProgress + contactsProgress + monitoringProgress).coerceAtMost(1f)
    val progressText = "${(progress * 100).toInt()}%"
    
    Surface(
        onClick = { },
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth(),
        color = if (isDark) Color(0xFF1F2937) else Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
        ),
        shadowElevation = if (isDark) 0.dp else 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                color = Color(0xFF3B82F6).copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.2f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = "Safety Shield",
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (progress >= 1f) "Safety Profile Complete" else "Complete Safety Profile",
                        color = if (isDark) Color.White else Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            progressText,
                            color = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "View Profile",
                            tint = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    "${runtimeState.emergencyContactCount}/5 contacts - ${runtimeState.statusMessage}",
                    color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = Color(0xFF18A558),
                    trackColor = if (isDark) Color.White.copy(alpha = 0.1f)
                    else Color.Black.copy(alpha = 0.05f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Section Label
// ─────────────────────────────────────────────

@Composable
fun SectionLabel(text: String) {
    val isDark = LocalIsDark.current
    Text(
        text = text.uppercase(),
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.5.sp,
        color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF6B7280)
    )
}

// ─────────────────────────────────────────────
//  Service Grid
// ─────────────────────────────────────────────

private fun serviceCategories(counts: Map<String, Int> = emptyMap()): List<ServiceCategory> {
    fun countText(type: String): String? = counts[type]?.let { count ->
        if (count == 1) "1 near" else "$count near"
    }

    return listOf(
        ServiceCategory("Police", Icons.Default.LocalPolice, Color(0xFF2563EB), countText("police")),
        ServiceCategory("Mechanic", Icons.Default.Build, Color(0xFF7C3AED), countText("mechanic")),
        ServiceCategory("Fuel", Icons.Default.LocalGasStation, Color(0xFFE07000), countText("fuel")),
        ServiceCategory("Hospitals", Icons.Default.LocalHospital, Color(0xFFE11D48), countText("hospital")),
        ServiceCategory("Pharmacy", Icons.Default.MedicalServices, Color(0xFF18A558), countText("pharmacy")),
        ServiceCategory("Fire", Icons.Default.LocalFireDepartment, Color(0xFFDC2626), countText("fire_station")),
        ServiceCategory("ATM", Icons.Default.LocalAtm, Color(0xFF0891B2), countText("atm")),
        ServiceCategory("Banks", Icons.Default.AccountBalance, Color(0xFF1A1814), countText("bank")),
        ServiceCategory("Post Office", Icons.Default.LocalPostOffice, Color(0xFF7C2D12), countText("post_office"))
    )
}

private fun serviceCountsByCategory(pois: List<PoiEntity>): Map<String, Int> {
    return pois.groupingBy { it.type }.eachCount()
}

@Composable
fun ServiceGrid(
    onCategoryClick: (String) -> Unit = {},
    counts: Map<String, Int> = emptyMap()
) {
    val services = serviceCategories(counts)

    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        services.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { svc ->
                    ServiceCard(
                        svc = svc,
                        onClick = { onCategoryClick(svc.label) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ServiceCard(svc: ServiceCategory, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isDark = LocalIsDark.current
    Surface(
        onClick = onClick,
        modifier = modifier.height(102.dp),
        color = if (isDark) Color(0xFF1F2937) else Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
        ),
        shadowElevation = if (isDark) 0.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(svc.color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    svc.icon,
                    contentDescription = svc.label,
                    tint = svc.color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                svc.label,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (isDark) Color.White else Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = svc.count ?: "",
                fontSize = 8.5.sp,
                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Nearby Services Row
// ─────────────────────────────────────────────

@Composable
fun NearbyServicesRow(
    pois: List<PoiEntity> = emptyList(),
    isLoading: Boolean = false,
    userLocation: LatLng? = null,
    onItemClick: (PoiEntity) -> Unit = {}
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when {
            isLoading -> item {
                NearbyItemCard(
                    title = "Refreshing nearby services",
                    sub = "Fetching current locations",
                    distance = "",
                    onClick = {}
                )
            }
            pois.isEmpty() -> item {
                NearbyItemCard(
                    title = "No nearby services cached",
                    sub = "Try again after location sync",
                    distance = "",
                    onClick = {}
                )
            }
            else -> items(pois) { item ->
                NearbyItemCard(
                    title = item.name,
                    sub = serviceTypeLabel(item.type),
                    distance = item.distanceLabel(userLocation),
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
fun NearbyItemCard(
    title: String,
    sub: String,
    distance: String,
    onClick: () -> Unit
) {
    val isDark = LocalIsDark.current
    Surface(
        onClick = onClick,
        modifier = Modifier.width(150.dp),
        color = if (isDark) Color(0xFF1F2937) else Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
        ),
        shadowElevation = if (isDark) 0.dp else 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A6EFF).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = "Location",
                        tint = Color(0xFF1A6EFF),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    distance,
                    fontSize = 10.sp,
                    color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                sub,
                fontSize = 10.sp,
                color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Gray
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Live", color = Color(0xFFE07000), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF18A558).copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Open",
                        color = Color(0xFF18A558),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun PoiEntity.matchesCategory(category: String): Boolean {
    return type in categoryTypes(category)
}

private fun categoryTypes(category: String): Set<String> = when (category.lowercase(Locale.US)) {
    "police" -> setOf("police")
    "mechanic", "repair" -> setOf("mechanic", "repair")
    "fuel" -> setOf("fuel")
    "hospitals", "hospital" -> setOf("hospital")
    "pharmacy" -> setOf("pharmacy")
    "fire" -> setOf("fire_station")
    "atm" -> setOf("atm")
    "banks", "bank" -> setOf("bank")
    "post office", "post_office" -> setOf("post_office")
    "parking" -> setOf("parking")
    "ambulance" -> setOf("ambulance")
    else -> setOf(category.lowercase(Locale.US))
}

private fun serviceTypeLabel(type: String): String = when (type) {
    "police" -> "Police station"
    "mechanic", "repair" -> "Mechanic"
    "fuel" -> "Fuel station"
    "hospital" -> "Hospital"
    "pharmacy" -> "Pharmacy"
    "fire_station" -> "Fire station"
    "atm" -> "ATM"
    "bank" -> "Bank"
    "post_office" -> "Post office"
    "parking" -> "Parking"
    "ambulance" -> "Ambulance"
    else -> type.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.US) }
}

// Calculate distance between two coordinates using Haversine formula (in meters)
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
    val earthRadiusM = 6371000.0 // Earth's radius in meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.asin(Math.sqrt(a))
    return (earthRadiusM * c).toInt()
}

private fun PoiEntity.distanceLabel(userLocation: LatLng?): String {
    val distanceMeters = route_distance_m
        ?: distance_m
        ?: if (userLocation != null) {
            calculateDistance(userLocation.latitude, userLocation.longitude, lat, lon)
        } else {
            0
        }
    
    val distance = when {
        distanceMeters <= 0 -> ""
        distanceMeters < 1000 -> "$distanceMeters m"
        else -> String.format(Locale.US, "%.1f km", distanceMeters / 1000.0)
    }
    val eta = eta_seconds?.takeIf { it > 0 }?.let { seconds ->
        val minutes = (seconds + 59) / 60
        "$minutes min"
    }
    return listOfNotNull(eta, distance.takeIf { it.isNotBlank() }).joinToString(" - ")
}

// Open Google Maps directions to a location
private fun openDirections(context: android.content.Context, poi: PoiEntity, userLat: Double, userLon: Double) {
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=$userLat,$userLon&destination=${poi.lat},${poi.lon}&travelmode=driving")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.setPackage("com.google.android.apps.maps")
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to web if Google Maps not installed
        val webUri = Uri.parse("https://maps.google.com/maps?saddr=$userLat,$userLon&daddr=${poi.lat},${poi.lon}")
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
    }
}

// Show a dialog for directions
fun showDirectionsDialog(context: android.content.Context, poi: PoiEntity, userLocation: LatLng) {
    val alertDialog = android.app.AlertDialog.Builder(context)
        .setTitle("Get Directions")
        .setMessage("Navigate to ${poi.name}?")
        .setPositiveButton("Yes") { dialog, _ ->
            openDirections(context, poi, userLocation.latitude, userLocation.longitude)
            dialog.dismiss()
        }
        .setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        .create()
    alertDialog.show()
}

// ─────────────────────────────────────────────
//  Road Alerts List
// ─────────────────────────────────────────────

@Composable
fun RoadAlertsList() {
    val isDark = LocalIsDark.current
    val alerts = listOf(
        RoadAlert(
            "Road work on NH-48",
            "Single lane · 0.3 km away",
            "8 min ago",
            Icons.Default.Warning,
            Color(0xFFE07000)
        ),
        RoadAlert(
            "Minor accident reported",
            "Sector 14 crossing · 1.0 km",
            "22 min ago",
            Icons.Default.DirectionsCar,
            Color(0xFFFF2D1A)
        )
    )

    Column(
        modifier = Modifier.padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        alerts.forEach { alert ->
            Surface(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                color = if (isDark) Color(0xFF1F2937) else Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
                ),
                shadowElevation = if (isDark) 0.dp else 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(alert.color.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            alert.icon,
                            contentDescription = alert.title,
                            tint = alert.color,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .weight(1f)
                    ) {
                        Text(
                            alert.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color.Black
                        )
                        Text(
                            alert.sub,
                            fontSize = 11.sp,
                            color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Gray
                        )
                    }
                    Text(
                        alert.time,
                        fontSize = 10.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.4f) else Color.Gray
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  SOS Floating Button
// ─────────────────────────────────────────────

@Composable
fun SOSFloatingButton() {
    val haptic = LocalHapticFeedback.current
    val runtimeState = LocalSwiftAidRuntimeState.current
    val actions = LocalSwiftAidRuntimeActions.current

    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text("Emergency SOS", fontWeight = FontWeight.Bold, color = Color(0xFFFFE5E5))
            },
            containerColor = Color(0xFF7F1D1D),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (runtimeState.emergencyContactCount > 0) {
                            "SwiftAid will dispatch online first and use saved contacts as SMS relay backup."
                        } else {
                            "SwiftAid will dispatch online first. Add contacts for SMS relay backup."
                        },
                        color = Color(0xFFFFD6D6),
                        fontSize = 13.sp
                    )
                    EmergencyOptionButton("Start SOS Countdown") {
                        showDialog = false
                        actions.startManualSos()
                    }

                    EmergencyOptionButton("Send SOS to Saved Contacts") {
                        showDialog = false
                        actions.startManualSos()
                    }

                    OutlinedButton(
                        onClick = {
                            showDialog = false
                            actions.manageEmergencyContacts()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD6D6)),
                        border = BorderStroke(1.dp, Color(0xFFFFD6D6).copy(alpha = 0.45f))
                    ) {
                        Text("Manage Emergency Contacts")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = Color(0xFFFFD6D6))
                }
            }
        )
    }

    Box(
        modifier = Modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color(0xFFFF2D1A).copy(alpha = 0.2f))
        )

        Surface(
            modifier = Modifier
                .size(64.dp)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDialog = true
                },
            color = Color(0xFFFF2D1A),
            shape = CircleShape,
            shadowElevation = 8.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "SOS",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun EmergencyOptionButton(
    title: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFB91C1C),
            contentColor = Color.White
        )
    ) {
        Text(title)
    }
}

// ─────────────────────────────────────────────
//  Bottom Navigation
// ─────────────────────────────────────────────

@Composable
fun SwiftaidBottomNav(
    onProfileClick: () -> Unit = {},
    onClaimsClick: () -> Unit = {},
    onExploreClick: (String?) -> Unit = {}
) {
    val isDark = LocalIsDark.current
    
    Surface(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .height(64.dp)
            .fillMaxWidth(),
        shape = CircleShape,
        color = if (isDark) Color(0xFF1A1A1A).copy(alpha = 0.98f) else Color.White.copy(alpha = 0.98f),
        shadowElevation = 8.dp,
        border = BorderStroke(
            1.dp,
            if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavPillItem(
                selected = true,
                onClick = {},
                icon = Icons.Default.Map,
                label = "Map"
            )
            NavPillItem(
                selected = false,
                onClick = { onExploreClick(null) },
                icon = Icons.Default.Explore,
                label = "Explore"
            )
            
            // Spacer for SOS button overlap
            Spacer(Modifier.width(72.dp))

            NavPillItem(
                selected = false,
                onClick = { onClaimsClick() },
                icon = Icons.AutoMirrored.Filled.Assignment,
                label = "Claims"
            )
            NavPillItem(
                selected = false,
                onClick = { onProfileClick() },
                icon = Icons.Default.Settings,
                label = "Settings"
            )
        }
    }
}

@Composable
fun NavPillItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    val isDark = LocalIsDark.current
    val contentColor = if (selected) Color(0xFF3B82F6) 
                      else (if (isDark) Color.White.copy(alpha = 0.4f) else Color.Gray)

    Column(
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = contentColor
        )
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
    }
}

// ─────────────────────────────────────────────
//  Explore Screen
// ─────────────────────────────────────────────

@Composable
fun ExploreScreen(initialCategory: String? = null, onBack: () -> Unit) {
    val isDark = LocalIsDark.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val poiRepository = remember(context) { PoiRepository(context) }
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var cachedPois by remember { mutableStateOf<List<PoiEntity>>(emptyList()) }
    var visiblePois by remember { mutableStateOf<List<PoiEntity>>(emptyList()) }
    var isPoiLoading by remember { mutableStateOf(true) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }

    fun cachedSelection(category: String?): List<PoiEntity> {
        return category
            ?.let { selected -> cachedPois.filter { it.matchesCategory(selected) } }
            ?: cachedPois.take(12)
    }

    fun loadCategoryFromBackend(category: String, location: LatLng) {
        isPoiLoading = true
        scope.launch {
            val fetched = poiRepository.fetchCategoryPois(location.latitude, location.longitude, category)
            visiblePois = fetched.ifEmpty { cachedSelection(category) }
            isPoiLoading = false
        }
    }

    LaunchedEffect(Unit) {
        cachedPois = poiRepository.getAllPois(120)
        visiblePois = cachedSelection(selectedCategory)
        isPoiLoading = false
        val hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasLocationPermission) {
            isPoiLoading = true
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { location ->
                    if (location == null) {
                        isPoiLoading = false
                        return@addOnSuccessListener
                    }
                    userLocation = LatLng(location.latitude, location.longitude)
                    scope.launch {
                        poiRepository.syncFromServer(location.latitude, location.longitude)
                        cachedPois = poiRepository.getAllPois(120)
                        selectedCategory?.let { category ->
                            visiblePois = poiRepository.fetchCategoryPois(location.latitude, location.longitude, category)
                                .ifEmpty { cachedSelection(category) }
                        } ?: run {
                            visiblePois = cachedSelection(null)
                        }
                        isPoiLoading = false
                    }
                }
                .addOnFailureListener {
                    isPoiLoading = false
                }
        }
    }
    
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
                        "Explore Nearby",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color.Black
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                SectionLabel("Categories")
                ServiceGrid(
                    counts = serviceCountsByCategory(cachedPois),
                    onCategoryClick = { category ->
                        selectedCategory = category
                        userLocation?.let { location ->
                            loadCategoryFromBackend(category, location)
                        } ?: run {
                            visiblePois = cachedSelection(category)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                SectionLabel(
                    if (selectedCategory != null) "Nearby $selectedCategory"
                    else "All Nearby Services"
                )

                if (isPoiLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = if (isDark) Color.White else Color(0xFF1A1A1A))
                    }
                } else if (visiblePois.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No services found in this category", color = Color.Gray)
                    }
                }

                visiblePois.forEach { service ->
                    val currentLocation = userLocation
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                            .clickable {
                                currentLocation?.let { location ->
                                    showDirectionsDialog(context, service, location)
                                }
                            },
                        color = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.1f)
                            else Color.Black.copy(alpha = 0.05f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .weight(1f)
                            ) {
                                Text(
                                    service.name,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color.Black
                                )
                                Text(
                                    service.address.ifBlank { serviceTypeLabel(service.type) },
                                    fontSize = 12.sp,
                                    color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray
                                )
                            }
                            Text(
                                service.distanceLabel(currentLocation),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3B82F6)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Previews
// ─────────────────────────────────────────────

@Preview(showBackground = true, name = "Light Mode")
@Composable
fun NavigationScreenLightPreview() {
    val sharedState = remember { AppSharedState() }
    CompositionLocalProvider(
        LocalIsDark provides false,
        LocalSharedState provides sharedState
    ) {
        MaterialTheme {
            NavigationScreen()
        }
    }
}

@Preview(showBackground = true, name = "Dark Mode")
@Composable
fun NavigationScreenDarkPreview() {
    val sharedState = remember { AppSharedState() }
    CompositionLocalProvider(
        LocalIsDark provides true,
        LocalSharedState provides sharedState
    ) {
        MaterialTheme {
            NavigationScreen()
        }
    }
}

@Composable
fun MapWithPermission(
    onSettings: () -> Unit = {},
    onClaims: () -> Unit = {},
    onExploreClick: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    val runtimeState = LocalSwiftAidRuntimeState.current

    var hasLocationPermission by remember {
        mutableStateOf(hasMapLocationPermission(context))
    }

    LaunchedEffect(runtimeState.requiredPermissionsReady) {
        hasLocationPermission = hasMapLocationPermission(context)
    }

    NavigationScreen(
        hasLocationPermission = hasLocationPermission,
        onSettings = onSettings,
        onClaims = onClaims,
        onExploreClick = onExploreClick
    )
}

private fun hasMapLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}
