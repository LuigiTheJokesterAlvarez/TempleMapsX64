package com.luigicxv711.x64em.Hardware

import com.luigicxv711.x64em.Hardware.BIOS.BIOS
import com.luigicxv711.x64em.Hardware.GPU.GenericVGAGPU
import com.luigicxv711.x64em.Hardware.CPU.CPU
import com.luigicxv711.x64em.Hardware.Keyboard.Keyboard
import com.luigicxv711.x64em.Hardware.RAM.ATSysRAM
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.*

fun showVGA(gpu: GenericVGAGPU) {
    val width = GenericVGAGPU.WIDTH
    val height = GenericVGAGPU.HEIGHT

    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val palette = gpu.palette
    for (y in 0 until height) {
        for (x in 0 until width) {
            val colorIndex = gpu.frameBuffer[y * width + x].toInt() and 0xFF
            image.setRGB(x, y, palette[colorIndex])
        }
    }
    val frame = JFrame("VGA Emulator")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.contentPane = object : JPanel() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            g.drawImage(image, 0, 0, width * 4, height * 4, null) // scale 2x
        }
    }
    frame.setSize(width * 4, height * 4)
    frame.isVisible = true
}


fun main() {
    val cpu = CPU();
    val bios = BIOS("C:\\Users\\luisa\\Downloads\\nasm-3.01rc9-win64\\nopalettetest.bin");
    val ram = ATSysRAM(16)
    val gpu = GenericVGAGPU()
    val keyboard = Keyboard()

    cpu.init()
    bios.init()
    ram.init()
    gpu.init()
    keyboard.init()

    cpu.wireWith(bios)
    cpu.wireWith(ram)
    cpu.wireWith(gpu)
    cpu.wireWith(keyboard)
    keyboard.pushKey(Keyboard.Key(127, 0x1c))

    while (!cpu.halted) {
        cpu.tick();
    }

    showVGA(gpu)

    println(cpu.halted)

    println(cpu.getRAXcomponents())
}