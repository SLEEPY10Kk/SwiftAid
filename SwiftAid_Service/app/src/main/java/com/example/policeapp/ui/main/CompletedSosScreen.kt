package com.example.policeapp.ui.main

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.policeapp.data.model.SosRequest
import com.example.policeapp.data.model.SosType
import com.example.policeapp.theme.AccentBlue
import com.example.policeapp.theme.AccentGold
import com.example.policeapp.theme.AccentRed
import com.example.policeapp.theme.CardBackground
import com.example.policeapp.theme.GreenSuccess
import com.example.policeapp.theme.PrimaryBlue
import com.example.policeapp.theme.SurfaceBorder
import com.example.policeapp.theme.TextPrimary
import com.example.policeapp.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CompletedSosScreen(
    requests: List<SosRequest>,
    onCardClick: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("Name") }
    var showFilterMenu by remember { mutableStateOf(false) }

    // Date filter states
    var filterDay by remember { mutableStateOf("") }
    var filterMonth by remember { mutableStateOf("") }
    var filterYear by remember { mutableStateOf("") }

    val isDateFilter = filterType == "Requested On" || filterType == "Completed On"

    val filteredRequests = remember(searchQuery, filterType, requests, filterDay, filterMonth, filterYear) {
        requests.filter { request ->
            if (isDateFilter) {
                val timestamp = if (filterType == "Requested On") request.timestamp else request.completedTimestamp
                if (timestamp == null) return@filter false
                
                val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
                val day = calendar.get(Calendar.DAY_OF_MONTH).toString()
                val month = (calendar.get(Calendar.MONTH) + 1).toString()
                val year = calendar.get(Calendar.YEAR).toString()

                val dayMatch = filterDay.isEmpty() || day == filterDay || day == "0$filterDay"
                val monthMatch = filterMonth.isEmpty() || month == filterMonth || month == "0$filterMonth"
                val yearMatch = filterYear.isEmpty() || year == filterYear

                dayMatch && monthMatch && yearMatch
            } else {
                if (searchQuery.isEmpty()) true
                else when (filterType) {
                    "Name" -> request.personName.contains(searchQuery, ignoreCase = true)
                    "Phone" -> request.phoneNumber.contains(searchQuery)
                    else -> true
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Completed SOS Requests",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${filteredRequests.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(GreenSuccess, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Filter UI
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isDateFilter) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DateInputField(value = filterDay, onValueChange = { if (it.length <= 2) filterDay = it }, placeholder = "DD", modifier = Modifier.weight(1f))
                        DateInputField(value = filterMonth, onValueChange = { if (it.length <= 2) filterMonth = it }, placeholder = "MM", modifier = Modifier.weight(1f))
                        DateInputField(value = filterYear, onValueChange = { if (it.length <= 4) filterYear = it }, placeholder = "YYYY", modifier = Modifier.weight(1.5f))
                    }
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Filter by $filterType...", fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = SurfaceBorder,
                            cursorColor = PrimaryBlue
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                Box {
                    IconButton(
                        onClick = { showFilterMenu = true },
                        modifier = Modifier
                            .background(CardBackground.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter By", tint = PrimaryBlue)
                    }
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false },
                        modifier = Modifier.background(CardBackground)
                    ) {
                        listOf("Name", "Phone", "Requested On", "Completed On").forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type, color = TextPrimary) },
                                onClick = {
                                    filterType = type
                                    showFilterMenu = false
                                    // Reset filters when changing type
                                    searchQuery = ""
                                    filterDay = ""
                                    filterMonth = ""
                                    filterYear = ""
                                }
                            )
                        }
                    }
                }
            }

            if (searchQuery.isNotEmpty() || filterDay.isNotEmpty() || filterMonth.isNotEmpty() || filterYear.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isDateFilter) "Filtering by $filterType" else "Filtering by $filterType: \"$searchQuery\"",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clear",
                        color = PrimaryBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { 
                            searchQuery = ""
                            filterDay = ""
                            filterMonth = ""
                            filterYear = ""
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        items(filteredRequests, key = { it.id }) { request ->
            CompletedSosCard(
                request = request,
                onClick = { onCardClick(request.id) },
            )
        }

        if (filteredRequests.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    Text("No requests found matching filters", color = TextSecondary)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(100.dp)) } // Padding for floating nav
    }
}

@Composable
private fun DateInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = PrimaryBlue,
            unfocusedBorderColor = SurfaceBorder,
            cursorColor = PrimaryBlue
        ),
        shape = RoundedCornerShape(8.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center)
    )
}

@Composable
private fun CompletedSosCard(
    request: SosRequest,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
            .background(CardBackground.copy(alpha = 0.8f))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Column {
            // Top row: badge + resolved
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // SOS Type Badge
                val (badgeText, badgeColor) = when (request.sosType) {
                    SosType.SELF -> "Self Called" to AccentRed
                    SosType.OTHER -> "Called for Other" to AccentGold
                    SosType.APP -> "App Called" to AccentBlue
                }
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )

                // Resolved badge
                Text(
                    text = "Resolved",
                    style = MaterialTheme.typography.labelSmall,
                    color = GreenSuccess,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(GreenSuccess.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Person name
            Text(
                text = request.personName,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary.copy(alpha = 0.8f),
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Address + arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (request.address.length > 35) request.address.take(35) + "…" else request.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View details",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
