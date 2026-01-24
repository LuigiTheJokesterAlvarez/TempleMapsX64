package com.luigicxv711.x64em.Hardware.IntelCore2

import com.luigicxv711.x64em.Hardware.BIOS.BIOS
import com.luigicxv711.x64em.Hardware.HardwareComp
import com.luigicxv711.x64em.Hardware.RAM.ATSysRAM

enum class CPUModes {
    REAL, PROTECTED, LONG
}
object RFlags {
    const val CF = 1L shl 0;
    const val PF = 1L shl 2;
    const val AF = 1L shl 4;
    const val ZF = 1L shl 6;
    const val SF = 1L shl 7;
    const val IF = 1L shl 9;
    const val DF = 1L shl 10;
    const val OF = 1L shl 11;
}
typealias Opcode = (CPU, Int, Int) -> Int
class CPU : HardwareComp() {
    var rax = 0L
    var rbx = 0L
    var rcx = 0L
    var rdx = 0L

    var rsi = 0L
    var rdi = 0L
    var rbp = 0L
    var rsp = 0L

    var r8to15 = LongArray(8)


    var rip = 0L

    var rflags = 0x2L;

    lateinit var mode: CPUModes

    val BIOS_BASE = 0xF0000L

    var BIOS: BIOS? = null
    var RAM: ATSysRAM? = null


    fun getRAXcomponents(): RAXParts {
        val al  = (rax and 0xFF).toInt()
        val ah  = ((rax shr 8) and 0xFF).toInt()
        val ax  = (rax and 0xFFFF).toInt()
        val eax = (rax and 0xFFFFFFFFL).toInt()

        return RAXParts(
            al,
            ah,
            ax,
            eax
        )
    }
    fun setRAXcomponents(parts: RAXParts) {

        // EAX
        rax = parts.eax.toLong() and EAX_MASK

        // AX
        rax = (rax and AX_MASK) or (parts.ax.toLong() and 0xFFFF)

        // AH
        rax = (rax and AH_MASK) or ((parts.ah.toLong() and 0xFF) shl 8)

        // AL
        rax = (rax and AL_MASK) or (parts.al.toLong() and 0xFF)
    }
    lateinit var OpcodeDirectTable: Array<Opcode?>
    lateinit var ModRMOpTable: Array<Opcode?>

    fun modrm_decode(modrm: Int) {
        val mod = (modrm ushr 6) and 0b11
        val reg = (modrm ushr 3) and 0b111
        val rm  = modrm and 0b111
    }

    override fun init() {
        OpcodeDirectTable = Array(256, {null}) // pre-init
        // load ALL opcodes
        OpcodeDirectTable[0x04] = { cpu, arg1, unused ->
            Opcodes.ADD_AL_imm8(cpu, arg1, 0)
        }
        ModRMOpTable = Array(64, {null})
        reset()
    }

    override fun reset() {
        mode = CPUModes.REAL
        rip = 0xFFF0 // bios rom.
        rflags = 0x2L
    }

    override fun shutdown() {
        TODO("Not yet implemented")
    }

    override fun tick() {
        val encodedOP = read8(rip)
        val op = OpcodeDirectTable[encodedOP]
        val isThere = op != null
        if (isThere) {
            rip += op(this, read8(rip+1), 0)
        }
    }

    override fun wireWith(comp: HardwareComp) {
        when (comp) {
            is ATSysRAM -> {
                RAM = comp
            }
            is BIOS -> {
                BIOS = comp
            }
        }
    }

    fun read8(address: Long): Int {
        BIOS?.let { bios ->
            val len = bios.getRomLen().toLong()
            if (address >= BIOS_BASE && address < BIOS_BASE + len) {
                return bios.read8(address - BIOS_BASE)
            }
        }  // this is fun

        RAM?.let { ram ->
            return ram.read8(address)
        }

        return 0xFF
    }

    fun write8(address: Long, value: Int) {
        var blocked = false
        BIOS?.let { bios ->
            val len = bios.getRomLen().toLong()
            blocked = (address >= BIOS_BASE && address < BIOS_BASE + len)
        } // safeguard

        RAM?.let { ram ->
            if (!blocked)
                ram.write8(address, value)
        }
    }


    // Normal Instructions Dispatch Table
}