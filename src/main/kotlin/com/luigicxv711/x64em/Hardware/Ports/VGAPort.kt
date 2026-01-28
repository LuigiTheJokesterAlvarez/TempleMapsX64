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
                val colors = IntArray(256)
                if (data.size != 768) return
                for (i in palIndex until data.size / 3) {
                    var r = data[i*3].toInt() and 0xFF
                    var g = data[i*3+1].toInt() and 0xFF
                    var b = data[i*3+2].toInt() and 0xFF
                    r *= brightnessFactor
                    g *= brightnessFactor
                    b *= brightnessFactor

                    colors[i] = (r shl 16) or (g shl 8) or b
                }
                gpu.palette = colors
            }
        }
    }

    override fun read(port: Int) {
        TODO("Not yet implemented")
    }
}