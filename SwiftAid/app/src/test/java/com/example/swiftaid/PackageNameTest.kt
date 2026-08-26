package com.example.swiftaid

import org.junit.Assert.assertEquals
import org.junit.Test

class PackageNameTest {
    @Test
    fun applicationIdUsesSwiftAidPackage() {
        assertEquals("com.example.swiftaid", BuildConfig.APPLICATION_ID)
    }
}
