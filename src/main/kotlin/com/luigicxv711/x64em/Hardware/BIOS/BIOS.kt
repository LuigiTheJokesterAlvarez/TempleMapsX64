package com.luigicxv711.x64em.Hardware.BIOS

import com.luigicxv711.x64em.Hardware.HardwareComp

class BIOS : HardwareComp() {
    var path = "C:\\TestBios\\ibm.bin"
    lateinit var rom: ByteArray
    override fun init() {
        rom = java.io.File(this.path).readBytes()
    }

    override fun reset() {

    }

    override fun shutdown() {

    }

    override fun tick() {}
    override fun wireWith(comp: HardwareComp) {}

    fun getRomLen(): Int {
        return rom.size;
    }

    fun read8(offset: Long): Int {
        val i = offset.toInt()
        if (i < 0 || i >= rom.size) {
            return 0xFF // open bus
        }
        return rom[i].toInt() and 0xFF
    }
}