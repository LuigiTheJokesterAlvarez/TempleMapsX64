package com.luigicxv711.x64em.Hardware

import com.luigicxv711.x64em.Hardware.BIOS.BIOS
import com.luigicxv711.x64em.Hardware.IntelCore2.CPU
import com.luigicxv711.x64em.Hardware.RAM.ATSysRAM

fun main() {
    var cpu = CPU();
    var bios = BIOS("C:\\Users\\luisa\\Downloads\\nasm-3.01rc9-win64\\boot.bin");
    var ram = ATSysRAM(16)
    cpu.init()
    bios.init()
    ram.init()

    cpu.wireWith(bios)
    cpu.wireWith(ram)
    var cycles = 0L
    var cyclesGOAL = 10L
    val startTime = System.nanoTime()
    var instructionsExecuted = 0L

    while (instructionsExecuted < 100_000_000) {
        if (!cpu.halted) {
            cpu.tick()
            instructionsExecuted++
        } else {
            // If halted, we don't count instructions,
            // but we might want to break or wait for an interrupt.
            break
        }
    }

    println(cpu.getRAXcomponents())
    println(cpu.halted)
    val endTime = System.nanoTime()
    val durationSeconds = (endTime - startTime) / 1_000_000_000.0
    val mips = (instructionsExecuted / durationSeconds) / 1_000_000.0

    println("Executed at: %.2f MIPS".format(mips))
}