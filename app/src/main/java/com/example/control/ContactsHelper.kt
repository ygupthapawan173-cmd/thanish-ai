package com.example.control

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactMatch(val displayName: String, val phoneNumber: String)

class ContactsHelper(private val context: Context) {

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    fun findContacts(nameQuery: String, limit: Int = 5): List<ContactMatch> {
        val cleanQuery = nameQuery.trim()
        if (cleanQuery.isEmpty() || !hasPermission()) return emptyList()

        val matches = LinkedHashMap<String, ContactMatch>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$cleanQuery%"),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext() && matches.size < limit) {
                val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                val num = if (numIdx >= 0) cursor.getString(numIdx).orEmpty().filter { it.isDigit() || it == '+' } else ""
                if (name.isNotEmpty() && num.isNotEmpty()) {
                    matches.putIfAbsent("$name|$num", ContactMatch(name, num))
                }
            }
        }
        return matches.values.toList()
    }

    fun resolvePhoneOrName(input: String): ContactMatch? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.count { it.isDigit() } >= 7) {
            return ContactMatch(trimmed, trimmed.filter { it.isDigit() || it == '+' })
        }
        return findContacts(trimmed, limit = 1).firstOrNull()
    }
}
