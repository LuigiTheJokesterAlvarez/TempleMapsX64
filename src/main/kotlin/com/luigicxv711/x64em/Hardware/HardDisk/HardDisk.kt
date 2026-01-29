package com.luigicxv711.x64em.Hardware.HardDisk

import com.luigicxv711.x64em.Hardware.HardwareComp
import java.io.File
import java.util.Collections

// SIZE IN MB
class HardDisk(val name: String, val size: Int) : HardwareComp() {
    companion object {
        const val SECTOR_SIZE = 512
        const val SECTORS_PER_HEAD = 63
        const val HEADS_PER_CYLINDER = 16
        fun CHS2SEC(c: Int, h: Int, s: Int): Int {
            require(s in 1..SECTORS_PER_HEAD)
            require(h in 0..<HEADS_PER_CYLINDER)
            return (c * HEADS_PER_CYLINDER + h) * SECTORS_PER_HEAD + (s - 1)
        }
    }
    val bgThr = Thread {
        while (true) {
            Thread.sleep(5000)
            val curr = System.currentTimeMillis()
            cache.forEach { (key, _) ->
                val last = lastaccess[key]?: return@forEach
                if (curr - last > 30000) {
                    deleteSector(key)
                }
            }
        }
    }
    private val cache = Collections.synchronizedMap(mutableMapOf<Int, ByteArray>())
    private val lastaccess = Collections.synchronizedMap(mutableMapOf<Int, Long>())
    // top multithreading
    lateinit var diskfile: File
    override fun init() {
        diskfile = File("${name}.epstein")
        if (!diskfile.exists()) {
            // optimization to writing so it doesn't take 2 minutes to write it on an NVME
            val totalb = size.toLong() * 2048 * SECTOR_SIZE
            val batchSize = 8 * 1024 * 1024 // 8 mb batches
            val batch = ByteArray(batchSize) // it's ALL ZEROS, ALL ZEROS, AND ALL ZEROS

            diskfile.outputStream().use { fos ->
                var amo = 0L
                while (amo < totalb) {
                    val data = minOf(batchSize.toLong(), totalb - amo).toInt()
                    fos.write(batch, 0, data)
                    amo += data
                }
            }
        }
        bgThr.isDaemon = true
        bgThr.start()
    }

    fun readSector(sec: Int): ByteArray? {
        return cache.getOrPut(sec) {
            loadfromDisk(sec)
        }
    }
    fun writeSector(sec: Int, data: ByteArray) {
        require(data.size == SECTOR_SIZE)
        cache[sec] = data
        lastaccess[sec] = System.currentTimeMillis()
    }
    fun writeSectors(secAddr: Int, arr: ByteArray, secs: Int) {
        require(arr.size >= secs * 512)

        var addr = secAddr

        for (i in 0 until secs) {
            val start = i * 512
            val data = arr.sliceArray(start until start + 512)
            writeSector(addr, data)
            addr++
        }
    }

    fun loadfromDisk(sec: Int): ByteArray {
        val bytes = ByteArray(SECTOR_SIZE)
        diskfile.inputStream().use { fis ->
            fis.skip(sec.toLong() * SECTOR_SIZE)  // move to the correct sector
            fis.read(bytes)
        }
        lastaccess[sec] = System.currentTimeMillis()
        return bytes
    }

    fun deleteSector(sec: Int) {
        cache[sec]?.let { data ->
            diskfile.outputStream().use { fos ->
                fos.channel.position(sec.toLong() * SECTOR_SIZE)
                fos.write(data)
            }
        }
        cache.remove(sec)
        lastaccess.remove(sec)
    }

    override fun reset() {}

    override fun shutdown() {}

    override fun tick() {}

    override fun wireWith(comp: HardwareComp) {}

}