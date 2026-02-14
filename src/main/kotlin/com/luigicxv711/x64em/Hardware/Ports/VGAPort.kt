package com.luigicxv711.x64em.Hardware.Ports

import com.luigicxv711.x64em.Hardware.CPU.CPU
import com.luigicxv711.x64em.Hardware.GPU.GPUModes

class VGAPort(private val cpu: CPU) : Port(cpu) {
    var palIndex = 0
    val brightnessFactor: Int = 4
    override fun init() {}

    override fun write(port: Int, data: ByteArray) {
        val gpu = cpu.GPU?: return
        when (port) {
            0x3C4 -> gpu.seqIndex = data[0].toInt() and 0xFF

            0x3C5 -> when (gpu.seqIndex) {
                0x02 -> gpu.mapMask = data[0].toInt() and 0x0F
                0x04 -> {
                    val v = data[0].toInt() and 0xFF
                    gpu.chain4  = (v and 0x08) != 0
                    gpu.oddEven = (v and 0x04) == 0
                }
            }
            0x3C8 -> {
                palIndex = data[0].toInt() and 0xFF
            }
            0x3C9 -> {
                val colors = gpu.palette

                var i = 0
                while (i + 2 < data.size) { // ensures we have a full rgb triplet
                    val idx = palIndex
                    if (idx >= 256) break

                    val r = ((data[i].toInt() and 0x3F) * brightnessFactor).coerceIn(0, 255)
                    val g = ((data[i + 1].toInt() and 0x3F) * brightnessFactor).coerceIn(0, 255)
                    val b = ((data[i + 2].toInt() and 0x3F) * brightnessFactor).coerceIn(0, 255)

                    colors[idx] = (r shl 16) or (g shl 8) or b

                    palIndex = (palIndex + 1) and 0xFF
                    i += 3
                }

                gpu.palette = colors
            }
            0x3CE -> gpu.gcIndex = data[0].toInt() and 0xFF

            0x3CF -> {
                val value = data[0].toInt() and 0xFF
                with (gpu) {
                    when (gcIndex) {
                        0x00 -> gcRegs[0] = value and 0x0F
                        0x01 -> gcRegs[1] = value and 0x0F
                        0x03 -> {
                            gcRegs[3] = value and 0xFF
                            rotateCount = gcRegs[3] and 0x07
                            rasterMode = (gcRegs[3] shr 3) and 0x03
                            // last bytes are unused (5-7)
                        }
                        0x04 -> {
                            readMapSelect = value and 0x03
                        }
                        0x05 -> {
                            gpu.writeMode = value and 0x03
                        }
                        0x08 -> {
                            gcRegs[8] = value and 0xFF
                            bitMask = gcRegs[8]
                        }
                    }
                }
            }
        }
    }

    override fun read(port: Int) {
        TODO("Not yet implemented")
    }
}