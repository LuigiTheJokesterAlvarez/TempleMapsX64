package com.luigicxv711.x64em.Hardware.CPU

import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.ES
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.RAX

object ES_Opcodes {
    fun MOV_AL(cpu: CPU, ip: Int) {
        val res = cpu.read8(cpu.phys(cpu.registers[ES].toInt(), ip))
        val raxCleaned = cpu.registers[RAX] and CLEAR_AL_MASK
        cpu.registers[RAX] = raxCleaned or (res.toLong() and AL_MASK) // optimizationing
    }
}