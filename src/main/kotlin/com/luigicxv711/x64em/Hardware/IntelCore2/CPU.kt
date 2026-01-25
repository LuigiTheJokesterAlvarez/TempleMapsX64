package com.luigicxv711.x64em.Hardware.IntelCore2

import com.luigicxv711.x64em.Hardware.BIOS.BIOS
import com.luigicxv711.x64em.Hardware.GPU.GenericVGAGPU
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
    // values that are used a lot in very important instructions so i cache them like this
    val maskAffected = RFlags.ZF or RFlags.SF or RFlags.PF or RFlags.OF or RFlags.AF
    val parityLookup = LongArray(256) { i ->
        if (Integer.bitCount(i) % 2 == 0) 0x4L else 0L // 0x4 is the bit for PF
    }

    companion object Indexes {
        var RAX = 0
        val OpcodeDirectTable: Array<((CPU, Int, Int) -> Int)?> = Array(256) { null } // pre-init

        // load ALL opcodes
        init {
            OpcodeDirectTable[0x04] = { cpu, arg1, _ ->
                Opcodes.ADD_AL_imm8(cpu, arg1)
            } //  ADD AL, num

            OpcodeDirectTable[0x90] = { _, _, _ -> 1 } // NOP

            OpcodeDirectTable[0x2C] = {cpu, arg1, _ ->
                Opcodes.SUB_AL_imm8(cpu, arg1)
            }

            OpcodeDirectTable[0xB0] = { cpu, src, _ ->
                Opcodes.MOV_AL(cpu, src)
            }

            OpcodeDirectTable[0xEA] = { cpu, _, _ ->
                Opcodes.JMPFAR16bit(cpu)
            } // JMP FAR ptr16:16

            OpcodeDirectTable[0xEB] = { cpu, arg1, _ ->
                Opcodes.JMPShort(cpu, arg1)
            }

            OpcodeDirectTable[0xF4] = { cpu, _, _ ->
                cpu.halted = true
                1
            }
            OpcodeDirectTable[0xFA] = { cpu, _, _ ->
                Opcodes.CLI(cpu)
            }
            OpcodeDirectTable[0xFE] = { cpu, num, _ ->
                Opcodes.Group4_INS(cpu, num)
            }
        }
    }

    var registers = LongArray(16)


    var halted = false

    var rip = 0L

    var rflags = 0x2L;

    lateinit var mode: CPUModes
    var cs = 0
    var ip = 0 // we wouldn't be in 32 bit, we would be in Real Mode which is 16 bit so an Int is Enough.

    val BIOS_BASE = 0xF0000L

    var BIOS: BIOS? = null
    var RAM: ATSysRAM? = null
    var GPU: GenericVGAGPU? = null

    fun phys(cs: Int, ip: Int): Long {
        return ((cs shl 4) + (ip and 0xFFFF)).toLong() and 0xFFFFF
    } // this is CRUCIAL for Real Mode.

    fun updateFlags(res: Int, inc: Boolean, old: Int) {
        var newFlags = 0L

        if ((res and 0xFF) == 0) newFlags = newFlags or RFlags.ZF

        if ((res and 0x80) != 0) newFlags = newFlags or RFlags.SF

        newFlags = newFlags or parityLookup[res and 0xFF]

        if (inc) {
            if (old == 0x7F && res == 0x80) newFlags = newFlags or RFlags.OF
        } else {
            if (old == 0x80 && res == 0x7F) newFlags = newFlags or RFlags.OF
        }

        rflags = (rflags and maskAffected.inv()) or (newFlags and maskAffected)
    }

    fun inc8(rm: Int) {
        if (rm < 4) {
            // [a to d]l
            var reg = registers[rm]
            val value = (reg and 0xFF).toInt()
            val res = (value + 1) and 0xFF

            registers[rm] = (reg and -0x100L) or res.toLong()
            updateFlags(res, true, value)
        } else {
            // [a to d]h
            val idx = rm - 4
            var reg = registers[idx]
            val value = ((reg shr 8) and 0xFF).toInt()
            val res = (value + 1) and 0xFF

            registers[idx] = (reg and -0xFF01L) or (res.toLong() shl 8)
            updateFlags(res, false, value)
        }
    }

    fun dec8(rm: Int) {
        if (rm < 4) {
            var reg = registers[rm]
            val value = (reg and 0xFF).toInt()
            val res = (value - 1) and 0xFF // wraps are good

            registers[rm] = (reg and -0x100L) or res.toLong()
            // updateFlags(res)
        } else {
            val idx = rm - 4
            var reg = registers[idx]
            val value = ((reg shr 8) and 0xFF).toInt()
            val res = (value - 1) and 0xFF

            registers[idx] = (reg and -0xFF01L) or (res.toLong() shl 8)
            // updateFlags(res)
        }
    }

    fun getRAXcomponents(): RAXParts {
        val rax = registers[RAX]
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

        var rax = registers[RAX]
        // EAX
        rax = parts.eax.toLong() and EAX_MASK

        // AX
        rax = (rax and AX_MASK) or (parts.ax.toLong() and 0xFFFF)

        // AH
        rax = (rax and AH_MASK) or ((parts.ah.toLong() and 0xFF) shl 8)

        // AL
        rax = (rax and AL_MASK) or (parts.al.toLong() and 0xFF)

        registers[RAX] = rax
    }
    lateinit var ModRMOpTable: Array<Opcode?>

    fun modrmDecode(modrm: Int): Int {
        val mod = (modrm ushr 6) and 0x03
        val reg = (modrm ushr 3) and 0x07
        val rm  = modrm and 0x07

        return (mod shl 16) or (reg shl 8) or rm
    }

    override fun init() {

        ModRMOpTable = Array(64, {null})
        reset()
    }

    override fun reset() {
        mode = CPUModes.REAL
        cs = 0xF000
        ip = 0xFFF0
        rip = 0xFFF0 // bios rom.
        rflags = 0x2L
    }

    override fun shutdown() {
        TODO("Not yet implemented")
    }

    override fun tick() {
        if (halted) return
        when (mode) {
            CPUModes.REAL -> {
                val phys = phys(cs, ip)
                val opcode = read8(phys)
                val op = OpcodeDirectTable[opcode]
                if (op != null) {
                    val len = op(this, read8(phys(cs, ip + 1)), 0)
                    ip = (ip + len) and 0xFFFF
                }
            }

            else -> {}
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
            is GenericVGAGPU -> {
                GPU = comp
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
        BIOS?.let { bios ->
            val len = bios.getRomLen().toLong()
            if (address in BIOS_BASE until BIOS_BASE + len) return
        }

        RAM?.let { ram ->
            ram.write8(address, value)
        }
    }


    // Normal Instructions Dispatch Table
}