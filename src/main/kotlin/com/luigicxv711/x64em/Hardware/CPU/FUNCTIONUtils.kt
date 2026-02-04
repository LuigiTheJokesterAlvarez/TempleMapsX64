package com.luigicxv711.x64em.Hardware.CPU

import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.RAX

object FUNCTIONUtils {
    fun checkESBXValid(bx: Int, size: Int = 512): Boolean {
        return bx >= 0 && bx + size <= 0x10000
    }

    fun setAL(cpu: CPU, al: Int){
        require(al in 0..0xFF)
        cpu.registers[RAX] = (cpu.registers[RAX] and CLEAR_AL_MASK) or (al.toLong() and AL_MASK)
    }

    fun setAH(cpu: CPU, ah: Int){
        require(ah in 0..0xFF)
        cpu.registers[RAX] = (cpu.registers[RAX] and CLEAR_AH_MASK) or ((ah.toLong() and AL_MASK) shl 8)
    }

    fun setAX(cpu: CPU, ax: Int){
        require(ax in 0..0xFFFF)
        cpu.registers[RAX] = (cpu.registers[RAX] and CLEAR_AX_MASK) or ax.toLong()
    }

    fun DLtoDISK(dl: Int): Int? =
        when {
            dl == 0x00 -> 0
            dl == 0x01 -> 1
            dl in 0x80..0x8F -> 2 + (dl - 0x80)
            else -> null
        }
}