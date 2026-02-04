package com.luigicxv711.x64em.Hardware.Ports

import com.luigicxv711.x64em.Hardware.CPU.CPU

class VGAPort(private val cpu: CPU) : Port(cpu) {
    var palIndex = 0
    val brightnessFactor: Int = 4
    override fun init() {}

    override fun write(port: Int, data: ByteArray) {
        val gpu = cpu.GPU?: return
        when (port) {
            0x3C8 -> {
                palIndex = data[0].toInt() and 0xFF
            }
            0x3C9 -> {
                val colors = gpu.palette

                var i = 0
                while (i + 2 < data.size) { // ensures we have a full rgb triplet
                    val idx = palIndex
                    if (idx >= 256) break

                    val r = (data[i].toInt() and 0xFF * brightnessFactor).coerceIn(0, 255)
                    val g = (data[i + 1].toInt() and 0xFF * brightnessFactor).coerceIn(0, 255)
                    val b = (data[i + 2].toInt() and 0xFF * brightnessFactor).coerceIn(0, 255)

                    colors[idx] = (r shl 16) or (g shl 8) or b

                    palIndex = (palIndex + 1) and 0xFF
                    i += 3
                }

                gpu.palette = colors
            }
        }
    }

    override fun read(port: Int) {
        TODO("Not yet implemented")
    }
}