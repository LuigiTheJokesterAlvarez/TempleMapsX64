package com.luigicxv711.x64em.Hardware.IntelCore2

// ALL OF THE OPCODES IN THE X86 INSTRUCTION SET
object Opcodes {
    fun ADD_r8_r8(cpu: CPU, dest: Int, src: Int): Int {
        return 0 // todo
    }
    fun ADD_AL_imm8(cpu: CPU, imm: Int, notused: Int = 0): Int {
        var comps = cpu.getRAXcomponents()
        val al = comps.al
        val result = (al + imm) and 0xFF
        cpu.setRAXcomponents(comps.copy(al=result))
        return 2
    }
    fun NOP(cpu: CPU, arg1: Int = 0, arg2: Int = 0): Int {
        // this literally does NOTHING
        return 1
    }
}