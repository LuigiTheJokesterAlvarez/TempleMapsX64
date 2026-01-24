package com.luigicxv711.x64em.Hardware.RAM

import com.luigicxv711.x64em.Hardware.HardwareComp

private const val pageShift = 20          // pages of 1mb
private const val pageSize = 1 shl pageShift
private const val pageMask = pageSize-1

class ATSysRAM(mbSize: Int = 512): HardwareComp() {
    private val maxPages = (mbSize * 1024 * 1024L) ushr pageShift
    private val pages = HashMap<Long, ByteArray>()

    override fun init() {
        reset()
    }

    override fun reset() {
        pages.clear()
    }

    override fun shutdown() {}

    override fun tick() {}

    override fun wireWith(comp: HardwareComp) {}

    private fun getPage(pageIndex: Long, create: Boolean = false): ByteArray? {
        return pages[pageIndex] ?: run {
            if (!create) return null
            val page = ByteArray(pageSize)
            pages[pageIndex] = page
            page
        }
    }

    fun read8(address: Long): Int {
        if (address < 0) return 0xFF // open bus
        val pageIndex = address ushr pageShift
        val page = getPage(pageIndex) ?: return 0xFF

        val offset = (address and pageMask.toLong()).toInt()
        return page[offset].toInt() and 0xFF
    }

    fun read16(offset: Long): Int {
        val lo = read8(offset)
        val hi = read8(offset + 1)
        return lo or (hi shl 8)
    }

    fun read32(offset: Long): Int {
        val lo = read16(offset)
        val hi = read16(offset + 2)
        return lo or (hi shl 16)
    }

    fun write8(address: Long, value: Int) {
        if (address < 0) return
        val pageIndex = address ushr pageShift
        if (pageIndex >= maxPages) return

        val page = getPage(pageIndex, create = true) ?: return
        val offset = (address and pageMask.toLong()).toInt()
        page[offset] = (value and 0xFF).toByte()
    }

    fun write16(address: Long, value: Int) {
        write8(address, value)
        write8(address + 1, value)
    }

    fun write32(address: Long, value: Int) {
        write16(address, value)
        write16(address + 2, value)
    }
}