package org.ufku.ii20task

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform