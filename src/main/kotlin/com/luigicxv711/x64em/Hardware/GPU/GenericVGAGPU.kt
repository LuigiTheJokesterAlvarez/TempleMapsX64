package com.luigicxv711.x64em.Hardware.GPU

import com.luigicxv711.x64em.Hardware.HardwareComp
import java.io.File
import javax.imageio.ImageIO

enum class GPUModes {
    Standard13h,
    Standard12h,
    TextMode
}
fun loadPal(): IntArray {
    val pal = IntArray(256)
    var i = 0
    val img = ImageIO.read(File("C:\\Users\\luisa\\Downloads\\stickers\\vgapal.png"))
    for (y in 0 until img.height) {
        for (x in 0 until img.width) {
            val col = img.getRGB(x, y)
            pal[i++] = col and 0xFFFFFF
        }
    }
    return pal
}

// this gpu class is compatible with dos and win9x, i'll make another for Windows XP.
open class GenericVGAGPU(var mode: GPUModes = GPUModes.Standard13h) : HardwareComp() {
    companion object ImportantVals {
        const val VGA_BASE = 0xA0000L
        const val WIDTH = 320
        const val HEIGHT = 200
        const val SIZE = WIDTH * HEIGHT
    }
    var palette = IntArray(256)
    val modeSize: Int
        get() = when(mode) {
            GPUModes.Standard13h -> 320 * 200
            GPUModes.Standard12h -> 640 * 480
            GPUModes.TextMode -> 80 * 25 * 2
        }

    lateinit var frameBuffer: ByteArray
    override fun init() {
        frameBuffer = ByteArray( 64000)
        palette = loadPal()
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