package com.luigicxv711.x64em.Hardware

import com.luigicxv711.x64em.Hardware.BIOS.BIOS
import com.luigicxv711.x64em.Hardware.CPU.CPU
import com.luigicxv711.x64em.Hardware.CPU.FUNCTIONUtils
import com.luigicxv711.x64em.Hardware.GPU.GenericVGAGPU
import com.luigicxv711.x64em.Hardware.Keyboard.Keyboard
import com.luigicxv711.x64em.Hardware.RAM.ATSysRAM

class X64Em(val gpu: GenericVGAGPU, private val keyboard: Keyboard, private val ramSize: Int) {
    val cpu: CPU = CPU()
    private val gpuLock = Any()

    private var frameArr = IntArray(0)
    @Volatile
    var running = false
    private val CPUThr = Thread {
        while (!cpu.halted) {
            cpu.tick()
            if (FUNCTIONUtils.getBL(cpu) == 255) cpu.halted = true
        }
        running = false
    }
    private val GPUThr = Thread {
        val frameTime = 1L // ~30 FPS
        while (running) {
            val start = System.currentTimeMillis()
            synchronized(gpuLock) {
                gpu.draw()
            }
            val elapsed = System.currentTimeMillis() - start
            val sleepTime = frameTime - elapsed
            if (sleepTime > 0) Thread.sleep(sleepTime)
        }
    }
    fun loadBIOS(romPath: String) {
        val bios = BIOS(romPath);
        bios.init()
        cpu.wireWith(bios)
    }
    fun init(): Boolean {
        running = true
        cpu.wireWith(ATSysRAM(ramSize))
        cpu.wireWith(gpu)
        cpu.wireWith(keyboard)

        cpu.init()
        gpu.init()
        keyboard.init()
        CPUThr.start()
        GPUThr.start()
        return true
    }
    fun getFrame(): IntArray {
        synchronized(gpuLock) {
            val w = gpu.width
            val h = gpu.height
            if (frameArr.size != w * h) {
                frameArr = IntArray(w * h)
            }
            val pal = gpu.palette
            val fb = gpu.frameBuffer

            for (i in 0 until w * h) {
                val colorIndex = fb[i].toInt() and 0xFF
                frameArr[i] = (0xFF shl 24) or pal[colorIndex] // RGBA
            }
            return frameArr
        }
    }
}