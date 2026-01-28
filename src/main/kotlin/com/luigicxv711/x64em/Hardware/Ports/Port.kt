package com.luigicxv711.x64em.Hardware.Ports

import com.luigicxv711.x64em.Hardware.CPU.CPU

// no ubytearray i'll just do and 0xFF
abstract class Port(private val cpu: CPU) {
    abstract fun init()
    abstract fun write(port: Int, data: ByteArray)
    abstract fun read(port: Int)
    // read having port arg is so different ports get different info lol
}

data class PortRange(val start: Int, val end: Int)

class PortManager(private val cpu: CPU) {
    private val ports = mutableMapOf<PortRange, Port>()

    fun registerPort(range: IntRange, port: Port) {
        port.init()
        ports[PortRange(range.first, range.last)] = port
    }

    fun getPort(portNumber: Int): Port? {
        return ports.entries.firstOrNull { portNumber in it.key.start..it.key.end }?.value
    }

    fun writePort(portNumber: Int, data: ByteArray) {
        val port = getPort(portNumber) ?: return

        port.write(portNumber, data)
    }
}