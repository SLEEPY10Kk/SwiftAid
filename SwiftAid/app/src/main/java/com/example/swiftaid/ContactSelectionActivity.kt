package com.example.swiftaid

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged

class ContactSelectionActivity : AppCompatActivity() {
    private val selectedNumbers = linkedSetOf<String>()
    private var allContacts: List<SelectableContact> = emptyList()
    private lateinit var selectedTitle: TextView
    private lateinit var selectedContainer: LinearLayout
    private lateinit var contactsContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var limitText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedNumbers.addAll(
            EmergencySmsDispatcher.getEmergencyContacts(this)
                .map { normalizePhoneNumber(it) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_EMERGENCY_CONTACTS)
        )
        allContacts = loadSavedPhoneContacts()
        buildUi()
        renderContacts()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(34), dp(20), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(16, 22, 29), Color.rgb(28, 36, 43))
            )
        }

        val topBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        topBar.addView(
            TextView(this).apply {
                text = "Emergency contacts"
                setTextColor(Color.WHITE)
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        topBar.addView(
            Button(this).apply {
                text = "Save"
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = roundedDrawable(Color.rgb(28, 127, 113))
                setOnClickListener { saveAndClose() }
            },
            LinearLayout.LayoutParams(dp(92), dp(48))
        )
        root.addView(topBar, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        limitText = TextView(this).apply {
            setTextColor(Color.rgb(244, 203, 122))
            textSize = 14f
            setPadding(0, dp(12), 0, dp(10))
        }
        root.addView(limitText, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        searchInput = EditText(this).apply {
            hint = "Search contacts"
            setHintTextColor(Color.rgb(117, 135, 146))
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(true)
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedDrawable(Color.rgb(21, 29, 36), Color.rgb(70, 90, 102))
            doAfterTextChanged { renderContacts() }
        }
        root.addView(
            searchInput,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
                bottomMargin = dp(14)
            }
        )

        val scrollView = ScrollView(this).apply {
            isFillViewport = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(content)

        selectedTitle = sectionTitle()
        content.addView(selectedTitle)
        selectedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.argb(224, 29, 40, 48), Color.argb(72, 255, 255, 255))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        content.addView(
            selectedContainer,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        content.addView(
            sectionTitle("All phone contacts"),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(18) }
        )
        contactsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(contactsContainer)

        root.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    private fun renderContacts() {
        limitText.text = "Selected ${selectedNumbers.size}/$MAX_EMERGENCY_CONTACTS"
        selectedTitle.text = "Selected contacts"

        selectedContainer.removeAllViews()
        val selectedContacts = selectedNumbers.map { number ->
            allContacts.firstOrNull { it.number == number } ?: SelectableContact(number, number)
        }
        if (selectedContacts.isEmpty()) {
            selectedContainer.addView(
                TextView(this).apply {
                    text = "No contacts selected yet"
                    setTextColor(Color.rgb(150, 165, 174))
                    textSize = 15f
                    setPadding(dp(8), dp(10), dp(8), dp(10))
                }
            )
        } else {
            selectedContacts.forEach { contact ->
                selectedContainer.addView(contactRow(contact, checked = true))
            }
        }

        contactsContainer.removeAllViews()
        val query = searchInput.text?.toString().orEmpty().trim()
        val filteredContacts = allContacts
            .filterNot { selectedNumbers.contains(it.number) }
            .filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.number.contains(query)
            }

        if (filteredContacts.isEmpty()) {
            contactsContainer.addView(
                TextView(this).apply {
                    text = if (allContacts.isEmpty()) "No saved phone contacts found" else "No matching contacts"
                    setTextColor(Color.rgb(150, 165, 174))
                    textSize = 15f
                    setPadding(dp(8), dp(18), dp(8), dp(18))
                }
            )
        } else {
            filteredContacts.forEach { contact ->
                contactsContainer.addView(contactRow(contact, checked = false))
            }
        }
    }

    private fun contactRow(contact: SelectableContact, checked: Boolean): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))

            val checkBox = CheckBox(this@ContactSelectionActivity).apply {
                isChecked = checked
                setOnClickListener { toggleContact(contact, isChecked, this) }
            }
            addView(checkBox, LinearLayout.LayoutParams(dp(48), dp(48)))

            addView(
                LinearLayout(this@ContactSelectionActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        TextView(this@ContactSelectionActivity).apply {
                            text = contact.name
                            setTextColor(Color.WHITE)
                            textSize = 17f
                            typeface = Typeface.DEFAULT_BOLD
                        }
                    )
                    addView(
                        TextView(this@ContactSelectionActivity).apply {
                            text = contact.number
                            setTextColor(Color.rgb(151, 163, 171))
                            textSize = 14f
                        }
                    )
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            setOnClickListener {
                checkBox.isChecked = !checkBox.isChecked
                toggleContact(contact, checkBox.isChecked, checkBox)
            }
        }
    }

    private fun toggleContact(contact: SelectableContact, checked: Boolean, checkBox: CheckBox) {
        if (checked) {
            if (selectedNumbers.size >= MAX_EMERGENCY_CONTACTS) {
                checkBox.isChecked = false
                limitText.text = "You can select up to $MAX_EMERGENCY_CONTACTS contacts"
                return
            }
            selectedNumbers.add(contact.number)
        } else {
            selectedNumbers.remove(contact.number)
        }
        renderContacts()
    }

    private fun saveAndClose() {
        EmergencySmsDispatcher.saveEmergencyContacts(
            this,
            selectedNumbers.take(MAX_EMERGENCY_CONTACTS).joinToString(separator = "\n")
        )
        finish()
    }

    private fun loadSavedPhoneContacts(): List<SelectableContact> {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val contactsByNumber = linkedMapOf<String, SelectableContact>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val rawNumber = cursor.getString(numberIndex)?.trim().orEmpty()
                val number = normalizePhoneNumber(rawNumber)
                if (number.isBlank()) continue
                val name = cursor.getString(nameIndex)?.trim().orEmpty().ifBlank { number }
                contactsByNumber.putIfAbsent(number, SelectableContact(name, number))
            }
        }
        return contactsByNumber.values.toList()
    }

    private fun sectionTitle(text: String = ""): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.rgb(216, 229, 235))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(8))
        }
    }

    private fun roundedDrawable(fillColor: Int, strokeColor: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(fillColor)
            strokeColor?.let { setStroke(dp(1), it) }
        }
    }

    private fun normalizePhoneNumber(phoneNumber: String): String {
        return PhoneNumberUtils.normalizeNumber(phoneNumber).ifBlank {
            phoneNumber.filter { it.isDigit() || it == '+' }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_EMERGENCY_CONTACTS = 5
    }
}

private data class SelectableContact(
    val name: String,
    val number: String
)
