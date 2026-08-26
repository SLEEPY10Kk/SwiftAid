package com.example.policeapp.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.policeapp.data.model.ResponderProfile
import com.example.policeapp.theme.CardBackground
import com.example.policeapp.theme.GreenSuccess
import com.example.policeapp.theme.PrimaryBlue
import com.example.policeapp.theme.SurfaceBorder
import com.example.policeapp.theme.TextPrimary
import com.example.policeapp.theme.TextSecondary

@Composable
fun HospitalInfoScreen(service: ResponderProfile?) {
    val serviceName = service?.name?.takeIf { it.isNotBlank() } ?: "Unregistered hospital service"
    val servicePhone = service?.phoneNumber?.takeIf { it.isNotBlank() } ?: "Not registered"
    val serviceAddress = service?.address?.takeIf { it.isNotBlank() } ?: "Register service location"
    val serviceCode = service?.id?.takeIf { it.isNotBlank() } ?: "NO-SERVICE"
    val coordinates = service?.let {
        "${String.format("%.6f", it.latitude)}, ${String.format("%.6f", it.longitude)}"
    } ?: "No coordinates saved"
    val activeText = if (service?.active == true) "Live" else "Inactive"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD32F2F).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocalHospital,
                    contentDescription = null,
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = serviceName,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 28.sp
                )
                Text(
                    text = "24/7 EMERGENCY & TRIAGE",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        HospitalInfoCard(title = "EMERGENCY UNIT", icon = Icons.Default.LocalHospital) {
            InfoRow(label = "Service Code", value = serviceCode)
            InfoRow(label = "Service Type", value = service?.serviceType ?: "HOSPITAL")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HospitalStat(label = "Status", value = activeText, icon = Icons.Default.CheckCircle, modifier = Modifier.weight(1f))
                HospitalStat(label = "Routing", value = "Nearest", icon = Icons.Default.Business, modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        HospitalInfoCard(title = "LOCATION", icon = Icons.Default.LocationOn) {
            InfoRow(label = "Address", value = serviceAddress)
            InfoRow(label = "Coordinates", value = coordinates)
            InfoRow(label = "Coverage", value = "Targeted nearest-hospital SOS routing")
        }

        Spacer(modifier = Modifier.height(20.dp))

        HospitalInfoCard(title = "MEDICAL LEAD", icon = Icons.Default.Person) {
            InfoRow(label = "Registered Account", value = serviceName)
            InfoRow(label = "Direct Line", value = servicePhone)
        }

        Spacer(modifier = Modifier.height(20.dp))

        HospitalInfoCard(title = "EMERGENCY CONTACTS", icon = Icons.Default.Call) {
            val numbers = listOf(servicePhone)
            numbers.forEachIndexed { index, number ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Transparent)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = null,
                        tint = GreenSuccess,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = number,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (index < numbers.size - 1) {
                    HorizontalDivider(color = SurfaceBorder.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(140.dp))
    }
}

@Composable
private fun HospitalStat(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground.copy(alpha = 0.5f))
            .border(1.dp, SurfaceBorder.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HospitalInfoCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBackground.copy(alpha = 0.8f))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
