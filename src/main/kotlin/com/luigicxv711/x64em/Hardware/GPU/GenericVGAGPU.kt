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
        const val VGA_TEXT_BASE = 0xB8000L
        const val WIDTH = 320
        const val HEIGHT = 200
        const val SIZE = WIDTH * HEIGHT

        const val CHAR_WIDTH = 8
        const val CHAR_HEIGHT = 16
    }
    var palette = IntArray(256)
    val modeSize: Int
        get() = when(mode) {
            GPUModes.Standard13h -> 320 * 200
            GPUModes.Standard12h -> 320 * 200 // uses same space.
            GPUModes.TextMode -> 80 * 25 * 2
        }
    val width: Int
        get() = when(mode) {
            GPUModes.Standard13h -> 320
            GPUModes.Standard12h -> 640
            GPUModes.TextMode -> 640
        }
    val height: Int
        get() = when(mode) {
            GPUModes.Standard13h -> 200
            GPUModes.Standard12h -> 480
            GPUModes.TextMode -> 400
        }

    lateinit var frameBuffer: ByteArray
    lateinit var charQueue: ByteArray
    var charI = 0
    var charStart = 0

    lateinit var latches: ByteArray

    fun rasterOp(byteous: Int, latch: Int): Int {
        return when (rasterMode) {
            0 -> byteous              // replace
            1 -> byteous and latch
            2 -> byteous or latch
            3 -> byteous xor latch
            else -> byteous
        }
    }

    override fun init() {
        frameBuffer = ByteArray( 64000)
        charQueue = ByteArray( 960 ) // chars and attributes

        palette = loadPal()

        planes = Array(4) { ByteArray(64 * 1024) }
        latches = ByteArray(4)
    }

    fun switchMode(newMode: GPUModes) {
        mode = newMode
        frameBuffer = when (mode) {
            GPUModes.Standard13h -> ByteArray(320*200)
            GPUModes.Standard12h -> ByteArray(640 * 480)
            GPUModes.TextMode -> ByteArray(640*400) // for rendering only
        }
        // clear charqueue
        if (mode == GPUModes.TextMode) {
            charQueue = ByteArray(4000)
            charI = 0
            charStart = 0
        }
    }

    override fun reset() {}

    override fun shutdown() {}

    override fun tick() {}

    override fun wireWith(comp: HardwareComp) {}

    open fun write8(addr: Long, value: Int) {
        val offset = (addr - VGA_BASE).toInt()
        when (mode) {
            GPUModes.Standard13h -> {
                if (offset !in 0 until SIZE) return
                frameBuffer[offset] = value.toByte()
            }
            GPUModes.Standard12h -> {
                val off = (addr - VGA_BASE).toInt()
                if (off !in 0 until 0x10000) return

                val b = value.toByte()

                // change addressing
                // gotta add the if but it looks like ts

                when (writeMode) {
                    0 -> {
                        if (!chain4) {
                            for (plane in 0..3) {
                                if ((mapMask and (1 shl plane)) != 0) {
                                    val cpu = rotateRight(b.toInt() and 0xFF, rotateCount)
                                    val lat = latches[plane].toInt() and 0xFF

                                    val rop = rasterOp(cpu, lat)

                                    val result =
                                        (rop and bitMask) or
                                                (lat and bitMask.inv() and 0xFF)

                                    planes[plane][off] = result.toByte()
                                }
                            }
                        } else {
                            val plane = off and 3
                            val addr  = off shr 2
                            // load latches from the addressed byte
                            for (p in 0..3) {
                                latches[p] = planes[p][addr]
                            }

                            val lat = latches[plane].toInt() and 0xFF

                            val cpu = rotateRight(b.toInt() and 0xFF, rotateCount)

                            val rop = rasterOp(cpu, lat)

                            val result =
                                (rop and bitMask) or
                                        (lat and bitMask.inv() and 0xFF)
                            planes[plane][addr ] = result.toByte()
                        }
                    }
                    1 -> {
                        val b = value.toByte()
                        for (plane in 0..3) {
                            if ((mapMask and (1 shl plane)) != 0) {
                                val lat = latches[plane].toInt() and 0xFF
                                val cpu = b.toInt() and 0xFF

                                val rop = cpu and lat //  and with latch
                                val result = (rop and bitMask) or (lat and bitMask.inv() and 0xFF)

                                planes[plane][off] = result.toByte()
                            }
                        }
                    }
                    2 -> {
                        for (plane in 0..3) {
                            if ((mapMask and (1 shl plane)) != 0) {
                                val cpub = b.toInt() and 0xFF
                                val color = cpub and 0x0F
                                val srcBit =
                                    if (enableSetReset(plane)) {
                                        (gcRegs[0] shr plane) and 1   // SET/RESET
                                    } else {
                                        (color shr plane) and 1   // CPU color
                                    }

                                val src = if (srcBit != 0) 0xFF else 0x00

                                val lat = latches[plane].toInt() and 0xFF

                                val rop = rasterOp(src, lat)

                                val result =
                                    (rop and bitMask) or
                                            (lat and bitMask.inv() and 0xFF)

                                planes[plane][off] = result.toByte()
                            }
                        }
                    }
                    3 -> {
                        val cpuMask = b.toInt() and 0xFF
                        for (plane in 0..3) {
                            if ((mapMask and (1 shl plane)) != 0) {
                                val srBit = (gcRegs[0] shr plane) and 1
                                val src = if (srBit != 0) 0xFF else 0x00
                                val rot = rotateRight(src, rotateCount)

                                val lat = latches[plane].toInt() and 0xFF
                                val mask = cpuMask and bitMask

                                val rop = rasterOp(rot, lat)
                                val result = (rop and mask) or (lat and mask.inv() and 0xFF)

                                planes[plane][off] = result.toByte()
                            }
                        }
                    }
                }
            }
            GPUModes.TextMode -> {
                val offset = (addr - VGA_TEXT_BASE).toInt()
                if (offset in 0 until 4000) {
                    charQueue[offset] = (value and 0xFF).toByte()
                    if (!teletyping) return
                    if (offset % 2 == 0) {
                        // this is only for teletype i see
                        // char checks
                        when (value) {
                            0x0A -> { // newline
                                cursorY++
                                if (cursorY == 25) scrollText()
                            }
                            0x0D -> { // carriage return
                                cursorX = 0
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    var teletyping = false

    fun read8(addr: Long): Int {
        val off = (addr - VGA_BASE).toInt()
        if (chain4) {
            val plane = (off and 3)
            val addrr  = (off shr 2)

            for (p in 0..3) {
                latches[p] = planes[p][addrr]
            }
            return planes[plane][addrr].toInt() and 0xFF
        } else {
            for (p in 0..3) {
                latches[p] = planes[p][off]
            }
            return planes[readMapSelect][off].toInt() and 0xFF
        }
    }

    val COLS = 80
    val ROWS = 25


    fun drawChars() {
        val fbWidth = this.width // should be 80 * CHAR_WIDTH

        for (row in 0 until ROWS) {
            val baseY = row * CHAR_HEIGHT
            val rowOffset = row * COLS * 2

            for (col in 0 until COLS) {
                val memIndex = rowOffset + col * 2

                val ch = charQueue[memIndex].toInt() and 0xFF
                val attr = charQueue[memIndex + 1].toInt() and 0xFF

                val fg = attr and 0x0F
                val bg = (attr shr 4) and 0x07 // bit 7 = blink (ignored here)

                val glyph = BIOSFont.chars[ch]

                val baseX = col * CHAR_WIDTH

                for (py in 0 until CHAR_HEIGHT) {
                    val fbIndex = (baseY + py) * fbWidth + baseX

                    // If glyph missing → fill background
                    if (glyph == null) {
                        for (i in 0 until CHAR_WIDTH) {
                            frameBuffer[fbIndex + i] = bg.toByte()
                        }
                        continue
                    }

                    val fontByte = glyph[py].toInt() and 0xFF

                    // SIMD-ish: unrolled 8-bit mask
                    frameBuffer[fbIndex + 0] = (if ((fontByte and 0x80) != 0) fg else bg).toByte()
                    frameBuffer[fbIndex + 1] = (if ((fontByte and 0x40) != 0) fg else bg).toByte()
                    frameBuffer[fbIndex + 2] = (if ((fontByte and 0x20) != 0) fg else bg).toByte()
                    frameBuffer[fbIndex + 3] = (if ((fontByte and 0x10) != 0) fg else bg).toByte()
                    frameBuffer[fbIndex + 4] = (if ((fontByte and 0x08) != 0) fg else bg).toByte()
                    frameBuffer[fbIndex + 5] = (if ((fontByte and 0x04) != 0) fg else bg).toByte()
                    frameBuffer[fbIndex + 6] = (if ((fontByte and 0x02) != 0) fg else bg).toByte()
                    frameBuffer[fbIndex + 7] = (if ((fontByte and 0x01) != 0) fg else bg).toByte()
                }
            }
        }
    }

    val rbArr = IntArray(480) { it * 640 }

    // precompute masks
    val masks = intArrayOf(0x80, 0x40, 0x20, 0x10, 0x08, 0x04, 0x02, 0x01)

    val pLookup = Array(4) { Array(256) { ByteArray(8) } }
    val rotationLookup = Array(256) { v ->
        Array(8) { count ->
            val c = count and 7
            ((v ushr c) or (v shl (8 - c))) and 0xFF
        }
    }

    init {
        for (plane in 0..3) {
            for (b in 0..255) {
                val arr = pLookup[plane][b]
                for (bit in 0..7) {
                    arr[bit] = if ((b and masks[bit]) != 0) (1 shl plane).toByte() else 0
                }
            }
        }
    }
    fun drawPlanes() {
        // super optimized
        val bytesPLine = 80

        for (y in 0 until 480) {
            val rB = rbArr[y]
            for (xB in 0 until bytesPLine) {
                val off = y * bytesPLine + xB

                val p0 = planes[0][off].toInt() and 0xFF
                val p1 = planes[1][off].toInt() and 0xFF
                val p2 = planes[2][off].toInt() and 0xFF
                val p3 = planes[3][off].toInt() and 0xFF

                val lu0 = pLookup[0][p0]
                val lu1 = pLookup[1][p1]
                val lu2 = pLookup[2][p2]
                val lu3 = pLookup[3][p3]

                val xBB = xB * 8
                for (bit in 0 until 8) {
                    frameBuffer[rB + xBB + bit] =
                        (lu0[bit].toInt() or lu1[bit].toInt() or lu2[bit].toInt() or lu3[bit].toInt()).toByte()
                }
            }
        }
    }


    fun draw() {
        when (mode) {
            GPUModes.Standard13h -> {}
            GPUModes.Standard12h -> drawPlanes()
            GPUModes.TextMode -> drawChars()
        }
    }

    fun scrollText() {
        // move all lines up by 1
        // 80x25
        for (row in 1 until 25) {
            for (col in 0 until 80) {
                val srcIdx = (row * 80 + col) * 2
                val dstIdx = ((row - 1) * 80 + col) * 2
                charQueue[dstIdx] = charQueue[srcIdx]
                charQueue[dstIdx + 1] = charQueue[srcIdx + 1]
            }
        }
        // clear last line
        val lastRowStart = (25 - 1) * 80 * 2
        for (i in 0 until 80 * 2) {
            charQueue[lastRowStart + i] = 0
        }
    }

    var cursorX = 0
    var cursorY = 0

    // 12H MODE HELP MEEEEEEEEEEEEE
    var seqIndex = 0
    var mapMask = 0x0F
    var writeMode = 0
    var rasterMode = 0
    var readMapSelect = 0 // default plane 0
    var bitMask = 0xFF

    var gcIndex = 0

    var gcRegs = IntArray(9)

    var rotateCount = 0

    val seqRegs = IntArray(5)
    var chain4 = false
    var oddEven = true

    fun enableSetReset(plane: Int): Boolean {
        return ((gcRegs[1] shr plane) and 1) != 0
    }

    fun rotateRight(v: Int, count: Int): Int {
        return (rotationLookup[v][count and 7])
    }

    lateinit var planes: Array<ByteArray>

}