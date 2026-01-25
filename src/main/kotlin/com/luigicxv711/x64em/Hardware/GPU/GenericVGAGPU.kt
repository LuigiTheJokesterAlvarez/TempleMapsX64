package com.luigicxv711.x64em.Hardware.GPU

import com.luigicxv711.x64em.Hardware.HardwareComp

enum class Modes {
    Standard13h,
    Standard12h,
    TextMode
}
// this gpu class is compatible with dos and win9x, i'll make another for windows xp.
open class GenericVGAGPU(var mode: Modes) : HardwareComp() {
    companion object {
        const val VGA_BASE = 0xA0000L
        const val WIDTH = 320
        const val HEIGHT = 200
        const val SIZE = WIDTH * HEIGHT
    }

    lateinit var frameBuffer: ByteArray
    override fun init() {
        frameBuffer = ByteArray( 0x20000) // 128KB of FrameBuffer RAM
    }

    override fun reset() {}

    override fun shutdown() {}

    override fun tick() {}

    override fun wireWith(comp: HardwareComp) {}

    open fun write8(addr: Long, value: Int) {
        val offset = (addr - VGA_BASE).toInt()
        when (mode) {
            Modes.Standard13h -> {
                if (addr !in VGA_BASE until VGA_BASE + SIZE) return
                frameBuffer[offset] = value.toByte()
            }
            Modes.TextMode -> {

            }
            else -> {}
        }
    }

}