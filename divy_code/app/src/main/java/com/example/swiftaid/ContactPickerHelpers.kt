package com.example.swiftaid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp

private data class ContactNumberOption(
    val label: String,
    val number: String
)

private data class SelectedContactNumbers(
    val contactName: String,
    val numbers: List<ContactNumberOption>
)

@Composable
fun EmergencyContactPickerButton(
    buttonText: String,
    isDark: Boolean,
    onContactPicked: (contactName: String, contactNumber: String) -> Unit,
    onMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val latestOnMessage by rememberUpdatedState(onMessage)
    val latestOnContactPicked by rememberUpdatedState(onContactPicked)

    var chooserState by remember { mutableStateOf<SelectedContactNumbers?>(null) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri ->
        if (contactUri == null) return@rememberLauncherForActivityResult
        val selected = readContactNumbers(context, contactUri)
        if (selected == null) {
            latestOnMessage("Could not read the selected contact.")
            return@rememberLauncherForActivityResult
        }
        if (selected.numbers.isEmpty()) {
            latestOnMessage("Selected contact has no phone number.")
            return@rememberLauncherForActivityResult
        }
        if (selected.numbers.size == 1) {
            latestOnContactPicked(selected.contactName, selected.numbers.first().number)
        } else {
            chooserState = selected
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            contactPickerLauncher.launch(null)
        } else {
            latestOnMessage("Contacts permission is required to pick a contact.")
        }
    }

    Button(
        onClick = {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                contactPickerLauncher.launch(null)
            } else {
                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(imageVector = Icons.Default.ContactPhone, contentDescription = null)
        Text(text = buttonText, modifier = Modifier.padding(start = 8.dp))
    }

    chooserState?.let { selection ->
        AlertDialog(
            onDismissRequest = { chooserState = null },
            title = { Text("Choose phone number") },
            text = {
                Column {
                    Text(
                        text = selection.contactName,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    selection.numbers.forEachIndexed { index, option ->
                        OutlinedButton(
                            onClick = {
                                chooserState = null
                                latestOnContactPicked(selection.contactName, option.number)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(text = option.label.ifBlank { "Number ${index + 1}" })
                                Text(text = option.number)
                            }
                        }
                        if (index != selection.numbers.lastIndex) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { chooserState = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun readContactNumbers(
    context: Context,
    contactUri: android.net.Uri
): SelectedContactNumbers? {
    val contactProjection = arrayOf(
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.DISPLAY_NAME
    )

    var contactId: String? = null
    var contactName = ""
    context.contentResolver.query(contactUri, contactProjection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            if (idIndex >= 0) contactId = cursor.getString(idIndex)
            if (nameIndex >= 0) contactName = cursor.getString(nameIndex).orEmpty()
        }
    }

    val resolvedContactId = contactId ?: return null
    val numbers = mutableListOf<ContactNumberOption>()
    val phoneProjection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.TYPE,
        ContactsContract.CommonDataKinds.Phone.LABEL
    )
    val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
    val selectionArgs = arrayOf(resolvedContactId)

    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        phoneProjection,
        selection,
        selectionArgs,
        null
    )?.use { cursor ->
        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
        val labelIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
        while (cursor.moveToNext()) {
            val rawNumber = if (numberIndex >= 0) cursor.getString(numberIndex).orEmpty() else ""
            val number = rawNumber.trim()
            if (number.isBlank()) continue
            val type = if (typeIndex >= 0) cursor.getInt(typeIndex) else ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
            val label = if (labelIndex >= 0) cursor.getString(labelIndex) else null
            val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                context.resources,
                type,
                label
            ).toString()
            numbers.add(
                ContactNumberOption(
                    label = typeLabel,
                    number = number
                )
            )
        }
    }

    return SelectedContactNumbers(
        contactName = contactName.ifBlank { "Contact" },
        numbers = numbers.distinctBy { it.number }
    )
}
