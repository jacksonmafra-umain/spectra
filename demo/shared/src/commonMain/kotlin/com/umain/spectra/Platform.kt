package com.umain.spectra

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform