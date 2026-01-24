package com.luigicxv711.x64em.Hardware.IntelCore2

data class RAXParts(
    val al: Int,
    val ah: Int,
    val ax: Int,
    val eax: Int
)
const val AL_MASK = 0xFFL
const val AH_MASK = 0xFFL shl 8
const val AX_MASK = -1L shl 16
const val EAX_MASK = 0xFFFFFFFFL