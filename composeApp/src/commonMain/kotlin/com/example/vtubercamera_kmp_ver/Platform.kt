package com.example.vtubercamera_kmp_ver

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
