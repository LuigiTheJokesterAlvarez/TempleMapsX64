package com.luigicxv711.x64em.Hardware.Ports

import com.luigicxv711.x64em.Hardware.CPU.CPU

// top 5 fake names, i named it like that even though it should be named something like BIOSClock
class PITPort(private val cpu: CPU) : Port(cpu) {
    @Volatile
    var tickCount: UInt = 0u

    private val thr = Thread {
        while (true) {
            Thread.sleep(55)
            tickCount++
        }
    }

    override fun init() {
        thr.isDaemon = true
        thr.start()
    }

    override fun write(port: Int, data: ByteArray) {
        // no
    }

    override fun read(port: Int) {
        // nope
    }

    fun waitTicks(ticks: UInt) {
        val start = tickCount
        while (tickCount < (start + ticks)) {
            Thread.sleep(1)
        }
    }
}