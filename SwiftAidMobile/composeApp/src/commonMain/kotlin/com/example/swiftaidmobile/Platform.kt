package com.example.swiftaidmobile

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform