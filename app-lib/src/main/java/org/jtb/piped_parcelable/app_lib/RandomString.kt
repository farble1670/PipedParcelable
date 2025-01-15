// shared/src/main/kotlin/org/jtb/piped_parcelable/test/RandomString.kt
package org.jtb.piped_parcelable.app_lib

import kotlin.random.Random

object RandomString {
    private val charPool = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    // This is ultra slow
    fun generate(length: Int): String {
        return (1..length)
            .map { Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
    }
}