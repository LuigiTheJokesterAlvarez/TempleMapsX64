package com.luigicxv711.x64em.Hardware.InterCon

import com.luigicxv711.x64em.Hardware.CPU.CPU
import com.luigicxv711.x64em.Hardware.HardwareComp

// fun fact this should only raise if cpu is halted
class InterruptController(private val cpu: CPU) : HardwareComp() {
    fun raise_INT(num: Int) {
        cpu.raiseInterrupt(num)
    }

    override fun init() {
        TODO("Not yet implemented")
    }

    override fun reset() {
        TODO("Not yet implemented")
    }

    override fun shutdown() {
        TODO("Not yet implemented")
    }

    override fun tick() {
        TODO("Not yet implemented")
    }

    override fun wireWith(comp: HardwareComp) {

    }
}