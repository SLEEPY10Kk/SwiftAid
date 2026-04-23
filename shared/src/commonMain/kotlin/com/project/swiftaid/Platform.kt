package com.project.swiftaid

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform