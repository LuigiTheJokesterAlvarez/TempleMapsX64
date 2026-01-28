package com.luigicxv711.x64em.Hardware.CPU

data class RAXParts(
    val al: Int,
    val ah: Int,
    val ax: Int,
    val eax: Int
)
const val AL_MASK = 0xFFL
const val AH_MASK = 0xFFL shl 8
const val AX_MASK = 0xFFFFL
const val EAX_MASK = 0xFFFFFFFFL
const val CLEAR_AL_MASK: Long = AL_MASK.inv()
const val CLEAR_AH_MASK: Long = AH_MASK.inv()
const val CLEAR_AX_MASK: Long = -0x10000L