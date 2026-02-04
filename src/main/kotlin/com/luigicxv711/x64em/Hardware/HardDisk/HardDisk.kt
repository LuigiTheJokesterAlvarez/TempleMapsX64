package com.luigicxv711.x64em.Hardware.HardDisk

import com.luigicxv711.x64em.Hardware.HardwareComp
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.Collections

// SIZE IN MB
class HardDisk(val path: String, val size: Int) : HardwareComp() {
    val maxSectors = size * 2048
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
        try {
            while (!Thread.currentThread().isInterrupted) {
                Thread.sleep(5000)
                val curr = System.currentTimeMillis()
                synchronized(this) {
                    val keys = cache.keys.toList()
                    for (key in keys) {
                        val last = lastaccess[key] ?: continue
                        if (curr - last > 30000) {
                            deleteSector(key)
                        }
                    }
                }
            }
        } catch (_: InterruptedException) {
            // no stacktrace
        }
    }
    private val cache = Collections.synchronizedMap(mutableMapOf<Int, ByteArray>())
    private val lastaccess = Collections.synchronizedMap(mutableMapOf<Int, Long>())
    // top multithreading
    lateinit var diskfile: File
    lateinit var chan: RandomAccessFile
    override fun init() {
        initWithFile(File(path))
    }

    private fun initWithFile(file: File) {
        diskfile = file
        val supposedSize = size.toLong() * 2048 * SECTOR_SIZE

        if (!diskfile.exists()) {
            diskfile.parentFile?.mkdirs()
            diskfile.createNewFile()

            chan = RandomAccessFile(diskfile, "rw")

            chan.setLength(
                supposedSize
            )
        } else {
            if (diskfile.length() != supposedSize) {
                throw IllegalStateException(
                    "disk image size mismatch: expected $supposedSize bytes, got ${diskfile.length()}"
                )
            }
            chan = RandomAccessFile(diskfile, "rw")
        }

        bgThr.isDaemon = true
        bgThr.start()
    }

    @Deprecated("we fucking die")
    fun resize(len: Long) {
        chan.setLength(len)
    }

    fun readSector(sec: Int): ByteArray {
        require(sec in 0 until maxSectors)
        lastaccess[sec] = System.currentTimeMillis()
        return cache.getOrPut(sec) {
            loadfromDisk(sec)
        }
    }
    fun readSectors(secAddr: Int, secs: Int): Array<ByteArray> {
        require(secAddr >= 0)
        require(secs > 0)
        require(secAddr + secs <= maxSectors)
        return Array(secs) {
            readSector(secAddr + it)
        }
    }
    fun writeSector(sec: Int, data: ByteArray) {
        require(sec in 0 until maxSectors)
        require(data.size == SECTOR_SIZE)
        cache[sec] = data
        lastaccess[sec] = System.currentTimeMillis()
    }
    fun writeSectors(secAddr: Int, arr: ByteArray, secs: Int) {
        require(secAddr >= 0)
        require(secs > 0)
        require(secAddr + secs <= maxSectors)

        val buffer = ByteArray(SECTOR_SIZE)
        for (i in 0 until secs) {
            System.arraycopy(arr, i*SECTOR_SIZE, buffer, 0, SECTOR_SIZE)
            writeSector(secAddr + i, buffer.clone())
        }
    }

    @Synchronized
    fun loadfromDisk(sec: Int): ByteArray {
        val bytes = ByteArray(SECTOR_SIZE)
        chan.seek(sec.toLong() * SECTOR_SIZE)
        val read = chan.read(bytes)
        if (read in 0 until SECTOR_SIZE) {
            for (i in read until SECTOR_SIZE) {
                bytes[i] = 0
            }
        }

        lastaccess[sec] = System.currentTimeMillis()
        return bytes
    }

    @Synchronized
    fun deleteSector(sec: Int) {
        require(sec in 0 until maxSectors)
        cache[sec]?.let { data ->
            chan.seek(sec.toLong() * SECTOR_SIZE)
            chan.write(data)
        }
        cache.remove(sec)
        lastaccess.remove(sec)
    }

    @Synchronized
    fun deleteSectors(sec: Int, secs: Int) {
        require(sec in 0 until maxSectors)
        require(sec + secs <= maxSectors)
        for (i in sec until sec + secs) {
            deleteSector(i)
        }
    }

    override fun reset() {}

    override fun shutdown() {
        // time to start working on that.
        val keys = cache.keys.toList()
        for (key in keys) {
            deleteSector(key)
        }
        bgThr.interrupt()

        chan.fd.sync()
        chan.close()
    }

    override fun tick() {}

    override fun wireWith(comp: HardwareComp) {}

}