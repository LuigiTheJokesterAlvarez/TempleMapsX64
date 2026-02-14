package com.github.ssquadteam.templemaps

import com.github.ssquadteam.videomaps.FrameProvider
import com.luigicxv711.x64em.Hardware.GPU.GPUModes

class X64EMProvider : FrameProvider {
    override fun getFrame(frameIndex: Int, width: Int, height: Int): IntArray? {
        val src = X64EMCommand.em.getFrame()
        val gpu = X64EMCommand.em.gpu

        val srcW = gpu.width
        val srcH = gpu.height

        val dstW = width
        val dstH = height
        val dst = IntArray(dstW * dstH) // transparent by default

        val (scaleX, scaleY) = when (gpu.mode) {
            GPUModes.Standard12h -> 2 to 2   // 640x480 to 320×240
            GPUModes.Standard13h -> 1 to 1   // 320x200 to letterbox
            GPUModes.TextMode    -> 2 to 2 // 640x400 to 320x200
        }

        val visW = srcW / scaleX
        val visH = srcH / scaleY

        val offX = (dstW - visW) / 2
        val offY = (dstH - visH) / 2

        for (y in 0 until visH) {
            val srcRow = (y * scaleY) * srcW
            val dstRow = (y + offY) * dstW + offX

            var sx = 0
            for (x in 0 until visW) {
                dst[dstRow + x] = src[srcRow + sx]
                sx += scaleX
            }
        }

        return dst
    }
}