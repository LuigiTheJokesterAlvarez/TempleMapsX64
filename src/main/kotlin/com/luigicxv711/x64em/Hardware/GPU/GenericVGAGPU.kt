package com.luigicxv711.x64em.Hardware.GPU

import com.luigicxv711.x64em.Hardware.HardwareComp

enum class GPUModes {
    Standard13h,
    Standard12h,
    TextMode
}
// this gpu class is compatible with dos and win9x, i'll make another for Windows XP.
open class GenericVGAGPU(var mode: GPUModes = GPUModes.Standard13h) : HardwareComp() {
    companion object ImportantVals {
        const val VGA_BASE = 0xA0000L
        const val WIDTH = 320
        const val HEIGHT = 200
        const val SIZE = WIDTH * HEIGHT
    }

    val modeSize: Int
        get() = when(mode) {
            GPUModes.Standard13h -> 320 * 200
            GPUModes.Standard12h -> 640 * 480
            GPUModes.TextMode -> 80 * 25 * 2
        }

    lateinit var frameBuffer: ByteArray
    override fun init() {
        frameBuffer = ByteArray( 64000)
    }

    override fun reset() {}

    override fun shutdown() {}

    override fun tick() {}

    override fun wireWith(comp: HardwareComp) {}

    open fun write8(addr: Long, value: Int) {
        val offset = (addr - VGA_BASE).toInt()
        when (mode) {
            GPUModes.Standard13h -> {
                if (addr !in VGA_BASE until VGA_BASE + SIZE) return
                frameBuffer[offset] = value.toByte()
            }
            GPUModes.TextMode -> {

            }
            else -> {}
        }
    }

}