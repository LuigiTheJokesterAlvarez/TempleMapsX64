package com.luigicxv711.x64em.Hardware

import com.luigicxv711.x64em.Hardware.BIOS.BIOS
import com.luigicxv711.x64em.Hardware.GPU.GenericVGAGPU
import com.luigicxv711.x64em.Hardware.CPU.CPU
import com.luigicxv711.x64em.Hardware.CPU.FUNCTIONUtils
import com.luigicxv711.x64em.Hardware.HardDisk.HardDisk
import com.luigicxv711.x64em.Hardware.Keyboard.Keyboard
import com.luigicxv711.x64em.Hardware.RAM.ATSysRAM
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.*

fun showVGA(gpu: GenericVGAGPU) {
    val width = gpu.width
    val height = gpu.height

    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val palette = gpu.palette
    for (y in 0 until height) {
        for (x in 0 until width) {
            val original = gpu.frameBuffer[y * width + x].toInt()
            var colorIndex = original and 0xFF
            if (original == -1) {
                colorIndex = 0
            }
            image.setRGB(x, y, palette[colorIndex])
        }
    }
    val frame = JFrame("VGA Emulator")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.contentPane = object : JPanel() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            g.drawImage(image, 0, 0, width * 2, height * 2, null) // scale 2x
        }
    }
    frame.setSize(width * 2, height * 2)
    frame.isVisible = true
}


fun main() {
    val em = X64Em(GenericVGAGPU(), Keyboard(), 32)

    em.loadBIOS("C:\\Users\\luisa\\Downloads\\nasm-3.01rc9-win64\\luigibios\\BIOSfull.bin")

    em.init()

    while (em.running) {}

    em.gpu.draw()


    showVGA(em.gpu)
}