package com.luigicxv711.x64em.Hardware.IntelCore2

import com.luigicxv711.x64em.Hardware.IntelCore2.CPU.Indexes.RAX

// ALL OF THE OPCODES IN THE X86 INSTRUCTION SET
object Opcodes {
    fun ADD_r8_r8(cpu: CPU, dest: Int, src: Int): Int {
        return 0 // todo
    }
    fun ADD_AL_imm8(cpu: CPU, imm: Int): Int {
        var comps = cpu.getRAXcomponents()
        val al = comps.al
        val result = (al + imm)
        val int8res = result and 0xFF
        cpu.setRAXcomponents(comps.copy(al=int8res))

        cpu.updateFlags(result, false, al)

        cpu.rflags = if (result > 0xFF) cpu.rflags or RFlags.CF else cpu.rflags and RFlags.CF.inv()
        return 2
    }
    fun SUB_AL_imm8(cpu: CPU, imm: Int): Int {
        val comps = cpu.getRAXcomponents()
        val al = comps.al

        val result = al - (imm and 0xFF)
        val int8res = result and 0xFF
        cpu.setRAXcomponents(comps.copy(al = int8res))

        cpu.updateFlags(result, false, al)

        cpu.rflags = if (imm > al) cpu.rflags or RFlags.CF else cpu.rflags and RFlags.CF.inv()
        return 2
    }
    fun CLI(cpu: CPU, arg1: Int = 0): Int {
        cpu.rflags = cpu.rflags and RFlags.IF.inv()
        return 1
    }
    fun JMPShort(cpu: CPU, arg1: Int): Int {
        val rel = arg1.toByte().toInt() // sign-extend
        cpu.ip = (cpu.ip + 2 + rel) and 0xFFFF
        return 0
    }
    fun JMPFAR16bit(cpu: CPU): Int {
        val offLo = cpu.read8(cpu.phys(cpu.cs, (cpu.ip + 1) and 0xFFFF))
        val offHi = cpu.read8(cpu.phys(cpu.cs, (cpu.ip + 2) and 0xFFFF))
        val offset = offLo or (offHi shl 8)

        // read segment
        val segLo = cpu.read8(cpu.phys(cpu.cs, (cpu.ip + 3) and 0xFFFF))
        val segHi = cpu.read8(cpu.phys(cpu.cs, (cpu.ip + 4) and 0xFFFF))
        val segment = segLo or (segHi shl 8)

        // do the jump
        cpu.cs = segment and 0xFFFF
        cpu.ip = offset and 0xFFFF
        return 0
    }
    fun MOV_AL(cpu: CPU, src: Int): Int {
        val raxCleaned = cpu.registers[RAX] and -0x100L
        cpu.registers[RAX] = raxCleaned or (src.toLong() and 0xFFL) // optimizationingo
        return 2
    }

    fun Group4_INS(cpu: CPU, numbah: Int): Int {
        var mrmBits = cpu.modrmDecode(numbah)
        val mod = (mrmBits ushr 16) and 0xFF
        val reg = (mrmBits ushr 8) and 0xFF
        val rm  = mrmBits and 0xFF

        if (mod == 3) {
            when (reg) {
                0 -> cpu.inc8(rm)
                1 -> cpu.dec8(rm)
            }
        }

        return 2 // 2 byte oh yeah
    }
}