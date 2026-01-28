package com.luigicxv711.x64em.Hardware.Keyboard

import com.luigicxv711.x64em.Hardware.HardwareComp

class Keyboard : HardwareComp() {
    lateinit var buffer: ArrayDeque<Key>  // simple queue for pressed keys

    data class Key(val ascii: Byte, val scancode: Byte)

    // called by us
    fun pushKey(key: Key) {
        buffer.addLast(key)
    }
    // this is specifically for ah = 1 function type
    fun getKeyNonBlocking(): Key? {
        return if (buffer.isEmpty()) null else buffer.removeFirst()
    }

    override fun init() {
        buffer = ArrayDeque()
    }

    override fun reset() {
        TODO("Not yet implemented")
    }

    override fun shutdown() {
        TODO("Not yet implemented")
    }

    override fun tick() {
        TODO("Not yet implemented")
    }

    override fun wireWith(comp: HardwareComp) {
        TODO("Not yet implemented")
    }
}