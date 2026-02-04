package com.luigicxv711.x64em.Hardware

import com.luigicxv711.x64em.Hardware.RAM.ATSysRAM

abstract class HardwareComp {
    var shutdowned: Boolean = false
    abstract fun init(): Unit
    abstract fun reset(): Unit
    abstract fun shutdown(): Unit
    abstract fun tick(): Unit
    abstract fun wireWith(comp: HardwareComp): Unit
}