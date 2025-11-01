package com.madovygabor.maturimate

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform