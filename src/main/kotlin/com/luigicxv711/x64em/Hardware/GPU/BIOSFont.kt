package com.luigicxv711.x64em.Hardware.GPU

object BIOSFont {
    val chars = Array<ByteArray?>(256) { null }

    val charA = byteArrayOf(
        0b00000000,
        0b00000000,
        0b00000000,
        0b00010000,
        0b00111000,
        0b01101100,
        0b11000110.toByte(),
        0b11000110.toByte(),
        0b11111110.toByte(),
        0b11000110.toByte(),
        0b11000110.toByte(),
        0b11000110.toByte(),
        0b00000000,
        0b00000000,
        0b00000000,
        0b00000000,
    )

    init {
        chars[65] = charA
    }
}