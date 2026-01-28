package com.luigicxv711.x64em.Hardware.Ports

import com.luigicxv711.x64em.Hardware.CPU.CPU

class VGAPort(cpu: CPU) : Port(cpu) {
    override fun write(port: Int, data: UByteArray) {
        TODO("Not yet implemented")
    }

    override fun read(): UByteArray {
        TODO("Not yet implemented")
    }
}