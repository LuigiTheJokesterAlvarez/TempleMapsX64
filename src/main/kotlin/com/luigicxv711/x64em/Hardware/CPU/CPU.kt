package com.luigicxv711.x64em.Hardware.CPU

import com.luigicxv711.x64em.Hardware.BIOS.BIOS
import com.luigicxv711.x64em.Hardware.GPU.GPUModes
import com.luigicxv711.x64em.Hardware.GPU.GenericVGAGPU
import com.luigicxv711.x64em.Hardware.HardDisk.HardDisk
import com.luigicxv711.x64em.Hardware.HardwareComp
import com.luigicxv711.x64em.Hardware.Keyboard.Keyboard
import com.luigicxv711.x64em.Hardware.Ports.PITPort
import com.luigicxv711.x64em.Hardware.Ports.PortManager
import com.luigicxv711.x64em.Hardware.Ports.VGAPort
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
    var portManager = PortManager(this)
    val stack = ByteArray(0x10000) // 64 BINARY kilobytes
    var SP = 0x10000

    fun push(cpu: CPU, num: Int) {
        cpu.SP = (cpu.SP - 2) and 0xFFFF
        stack[cpu.SP] = (num and 0xFF).toByte()
        stack[cpu.SP + 1] = (num ushr 8).toByte()
    }
    fun pop(cpu: CPU): Int {
        val lo = stack[cpu.SP].toInt()
        val hi = stack[cpu.SP + 1].toInt()
        cpu.SP = (cpu.SP + 2) and 0xFFFF
        return lo or (hi shl 8)
    }
    // i deadass seen ppl try to replicate the stack pointer math when u could just like do an array list :skull:

    fun hasFlag(cpu: CPU, flag: Long): Boolean {
        return (cpu.rflags and flag).toInt() != 0
    }

    // values that are used a lot in very important instructions, so I cache them like this
    companion object Indexes {
        const val maskAffected = RFlags.ZF or RFlags.SF or RFlags.PF or RFlags.OF or RFlags.AF
        val parityLookup = LongArray(256) { i ->
            if (Integer.bitCount(i) % 2 == 0) 0x4L else 0L // 0x4 is the bit for PF
        }
        const val RAX = 0
        const val RCX = 1
        const val RDX = 2
        const val RBX = 3

        const val RSI = 4
        const val RDI = 5

        const val RBP = 8
        const val RSP = 9

        const val ES = 18
        const val DI = 19

        const val SI = 20
        const val DS = 21
        // bro why is the order swapped in x86
        val OpcodeDirectTable: Array<((CPU, Int, Int) -> Int)?> = Array(256) { null } // pre-init

        // load ALL opcodes
        init {
            OpcodeDirectTable[0x04] = { cpu, arg1, _ ->
                Opcodes.ADD_AL_imm8(cpu, arg1)
            } //  ADD AL, num
            OpcodeDirectTable[0x0C] = {cpu, arg1, _ ->
                Opcodes.OR_AL(cpu, arg1)
            }
            OpcodeDirectTable[0x0F] = { cpu, arg1, _ ->
                Opcodes.Group0x0F_INS(cpu, arg1)
            } //  ADD AL, num

            OpcodeDirectTable[0x74] = { cpu, num, _ ->
                Opcodes.JE_short(cpu, num)
            }

            OpcodeDirectTable[0x88] = { cpu, modrmnum, _ ->
                Opcodes.MOVrm8(cpu, modrmnum)
            }

            OpcodeDirectTable[0x24] = {cpu, arg1, _ ->
                Opcodes.AND_AL(cpu, arg1)
            }

            OpcodeDirectTable[0x2C] = {cpu, arg1, _ ->
                Opcodes.SUB_AL_imm8(cpu, arg1)
            }


            OpcodeDirectTable[0x34] = {cpu, arg1, _ ->
                Opcodes.XOR_AL(cpu, arg1)
            }

            OpcodeDirectTable[0x3C] = {cpu, arg1, _ ->
                Opcodes.CMP_AL(cpu, arg1)
            }

            OpcodeDirectTable[0x49] = {cpu, _, _ ->
                Opcodes.DEC_CX(cpu)
            }

            OpcodeDirectTable[0x6E] = {cpu, _, _ ->
                Opcodes.OUTSB(cpu)
            }

            OpcodeDirectTable[0x83] = {cpu, arg1, _ ->
                Opcodes.Group16B_1(cpu, arg1)
            }


            OpcodeDirectTable[0x8E] = {cpu, arg1, _ ->
                Opcodes.MOV_reg2seg(cpu, arg1)
            }

            OpcodeDirectTable[0xA4] = { cpu, _, _ ->
                Opcodes.MOVSB(cpu)
            }

            OpcodeDirectTable[0xAA] = { cpu, _, _ ->
                Opcodes.STOSB(cpu)
            }

            OpcodeDirectTable[0xB0] = { cpu, src, _ ->
                Opcodes.MOV_AL(cpu, src)
            }
            OpcodeDirectTable[0xB4] = { cpu, src, _ ->
                Opcodes.MOV_AH(cpu, src)
            }
            OpcodeDirectTable[0xBE] = { cpu, _, _ ->
                Opcodes.MOV_SI(cpu)
            }

            OpcodeDirectTable[0xBF] = { cpu, _, _ ->
                Opcodes.MOV_DI(cpu)
            }
            OpcodeDirectTable[0xB8] = { cpu, _, _ ->
                Opcodes.MOV_AX(cpu)
            }
            OpcodeDirectTable[0xB9] = { cpu, _, _ ->
                Opcodes.MOV_CX(cpu)
            }
            OpcodeDirectTable[0xBA] = { cpu, _, _ ->
                Opcodes.MOV_DX(cpu)
            }
            OpcodeDirectTable[0xBB] = { cpu, _, _ ->
                Opcodes.MOV_BX(cpu)
            }

            OpcodeDirectTable[0xCD] = { cpu, interruptNum, _ ->
                Opcodes.INTERRUPT(cpu, interruptNum)
            }
            OpcodeDirectTable[0xE2] = { cpu, relrel, _ ->
                Opcodes.LOOP(cpu, relrel)
            }
            OpcodeDirectTable[0xEA] = { cpu, _, _ ->
                Opcodes.JMPFAR16bit(cpu)
            } // JMP FAR ptr16:16

            OpcodeDirectTable[0xEB] = { cpu, arg1, _ ->
                Opcodes.JMPShort(cpu, arg1)
            }

            OpcodeDirectTable[0xEE] = { cpu, _, _ ->
                Opcodes.OUT(cpu)
            }

            OpcodeDirectTable[0xF3] = { cpu, _, _ ->
                cpu.REP = true
                1
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

    var registers = LongArray(22)
    /*
    0 to 3: r[a to d]x
    4 to 7 r[s to IDK]i
    8 and 9 rbp and rsp
    10 to 17 r[8 to 15]
    18 and 19 es and di
    20 source register
     */

    var REP = false

    fun getReg(cpu: CPU, reg: Int): Int {
        return when (reg) {
            0 -> (cpu.registers[RAX] and 0xFF).toInt()
            1 -> (cpu.registers[RCX] and 0xFF).toInt()
            2 -> (cpu.registers[RDX] and 0xFF).toInt()
            3 -> (cpu.registers[RBX] and 0xFF).toInt()
            4 -> ((cpu.registers[RAX] ushr 8) and 0xFF).toInt()
            5 -> ((cpu.registers[RCX] ushr 8) and 0xFF).toInt()
            6 -> ((cpu.registers[RDX] ushr 8) and 0xFF).toInt()
            7 -> ((cpu.registers[RBX] ushr 8) and 0xFF).toInt()
            else -> 0
        }
    }

    fun getReg16(cpu: CPU, reg: Int): Int {
        return when (reg) {
            0 -> (cpu.registers[RAX] and 0xFFFF).toInt()
            1 -> (cpu.registers[RCX] and 0xFFFF).toInt()
            2 -> (cpu.registers[RDX] and 0xFFFF).toInt()
            3 -> (cpu.registers[RBX] and 0xFFFF).toInt()
            4 -> ((cpu.registers[RAX] ushr 8) and 0xFFFF).toInt()
            5 -> ((cpu.registers[RCX] ushr 8) and 0xFFFF).toInt()
            6 -> ((cpu.registers[RDX] ushr 8) and 0xFFFF).toInt()
            7 -> ((cpu.registers[RBX] ushr 8) and 0xFFFF).toInt()
            else -> 0
        }
    }


    fun setReg(cpu: CPU, reg: Int, value: Int) {
        val v = value and 0xFF
        when (reg) {
            0 -> cpu.registers[RAX] = (cpu.registers[RAX] and -0x100L) or v.toLong()
            1 -> cpu.registers[RCX] = (cpu.registers[RCX] and -0x100L) or v.toLong()
            2 -> cpu.registers[RDX] = (cpu.registers[RDX] and -0x100L) or v.toLong()
            3 -> cpu.registers[RBX] = (cpu.registers[RBX] and -0x100L) or v.toLong()
            4 -> cpu.registers[RAX] = (cpu.registers[RAX] and -0xFF00L.inv()) or (v.toLong() shl 8)
            5 -> cpu.registers[RCX] = (cpu.registers[RCX] and -0xFF00L.inv()) or (v.toLong() shl 8)
            6 -> cpu.registers[RDX] = (cpu.registers[RDX] and -0xFF00L.inv()) or (v.toLong() shl 8)
            7 -> cpu.registers[RBX] = (cpu.registers[RBX] and -0xFF00L.inv()) or (v.toLong() shl 8)
        }
    }

    fun setReg16(cpu: CPU, reg: Int, value: Int) {
        val v = value and 0xFFFF
        when (reg) {
            0 -> cpu.registers[RAX] = (cpu.registers[RAX] and -0x10000L) or v.toLong()
            1 -> cpu.registers[RCX] = (cpu.registers[RCX] and -0x10000L) or v.toLong()
            2 -> cpu.registers[RDX] = (cpu.registers[RDX] and -0x10000L) or v.toLong()
            3 -> cpu.registers[RBX] = (cpu.registers[RBX] and -0x10000L) or v.toLong()
        }
    }

    fun CalcEffAddr(mod: Int, rm: Int, displacement: Int): Int {
        val bx = registers[RBX].toInt()
        val bp = registers[RBP].toInt()
        val si = registers[RSI].toInt()
        val di = registers[RDI].toInt()

        val base = when (rm) {
            0 -> bx + si
            1 -> bx + di
            2 -> bp + si
            3 -> bp + di
            4 -> si
            5 -> di
            6 -> if (mod == 0) 0 else bp   // special case
            7 -> bx
            else -> 0
        }

        return (base + displacement) and 0xFFFF
    }


    var halted = false

    var rip = 0L

    var rflags = 0x2L;

    lateinit var mode: CPUModes
    var cs = 0
    var ds = 0
    var ip = 0 // we wouldn't be in 32 bit, we would be in Real Mode which is 16 bit so an Int is Enough.

    val BIOS_BASE = 0xF0000L

    var BIOS: BIOS? = null
    var RAM: ATSysRAM? = null
    var GPU: GenericVGAGPU? = null
    var Keyboard: Keyboard? = null
    var HardDisk: HardDisk? = null

    // interrupt functions

    fun setVideoMode(ftype: Int, value: Int): Boolean {
        when (ftype) {
            0x00 -> {
                GPU?.let { gpu ->
                    gpu.mode = when (value) {
                        0x13 -> GPUModes.Standard13h
                        0x12 -> GPUModes.Standard12h
                        else -> GPUModes.Standard13h
                    }
                }
            } // set video mode
        }
        return true
    }

    fun keyboardHandle(ftype: Int, value: Int): Boolean {
        Keyboard?.let { keyboard ->
            when (ftype) {
                0x00 -> {
                    val key = keyboard.getKeyNonBlocking() ?: return false

                    Opcodes.MOV_AL(this, key.ascii.toInt())
                    Opcodes.MOV_AH(this, key.scancode.toInt())

                    return true
                }
            }
        }
        return false
    }

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

    fun updateFlags16(res: Int, inc: Boolean, old: Int) {
        var newFlags = 0L

        if ((res and 0xFFFF) == 0) newFlags = newFlags or RFlags.ZF

        if ((res and 0x8000) != 0) newFlags = newFlags or RFlags.SF

        newFlags = newFlags or parityLookup[res and 0xFF]

        if (inc) {
            if (old == 0x7FFF && res == 0x8000) newFlags = newFlags or RFlags.OF
        } else {
            if (old == 0x8000 && res == 0x7FFF) newFlags = newFlags or RFlags.OF
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

    fun modrmDecode(modrm: Int): Int {
        val mod = (modrm ushr 6) and 0x03
        val reg = (modrm ushr 3) and 0x07
        val rm  = modrm and 0x07

        return (mod shl 16) or (reg shl 8) or rm
    }

    override fun init() {
        portManager.registerPort(0x3C8..0x3C9, VGAPort(this))
        portManager.registerPort(0x40..0x40, PITPort(this))
        reset()
    }

    override fun reset() {
        mode = CPUModes.REAL
        cs = 0xF000
        ds = cs
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
                val physPrev = phys(cs, ip - 1)
                val opcode = read8(phys)
                val op = OpcodeDirectTable[opcode]
                if (op != null) {
                    val len = op(this, read8(phys(cs, ip + 1)), 0)
                    ip = (ip + len) and 0xFFFF
                } else {
                    ip += 2
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
            is Keyboard -> {
                Keyboard = comp
            }
            is HardDisk -> {
                HardDisk = comp
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

    fun read16(address: Long): Int {
        val lo = read8(address)
        val hi = read8(address + 1)
        return lo or (hi shl 8)
    }

    fun write8(address: Long, value: Int) {
        BIOS?.let { bios ->
            val len = bios.getRomLen().toLong()
            if (address in BIOS_BASE until BIOS_BASE + len) return
        }

        GPU?.let { gpu ->
            val frameBufferLen = gpu.frameBuffer.size
            val vga_b = GenericVGAGPU.ImportantVals.VGA_BASE
            val size = GenericVGAGPU.ImportantVals.SIZE
            if (address in vga_b until vga_b + size) {
                gpu.write8(address, value)
                return
            }
        }

        RAM?.let { ram ->
            ram.write8(address, value)
        }
    }

    fun ArrCPY(cpu: CPU, length: Int) {
        val si = cpu.registers[SI]
        val di = cpu.registers[DI]

        val ds = cpu.ds
        val es = cpu.registers[ES].toInt()

        for (i in 0 until length) {
            val v = cpu.read8(cpu.phys(ds, (si + i).toInt()))
            cpu.write8(cpu.phys(es, (di + i).toInt()), v)
        }

        cpu.registers[SI] += length
        cpu.registers[DI] += length
        val raxCleaned = cpu.registers[RCX] and CLEAR_AX_MASK // clear lower 16 bits
        cpu.registers[RCX] = raxCleaned or 0L
    }


    // Normal Instructions Dispatch Table
}