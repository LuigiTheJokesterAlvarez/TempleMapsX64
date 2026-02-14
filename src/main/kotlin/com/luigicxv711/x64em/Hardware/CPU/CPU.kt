package com.luigicxv711.x64em.Hardware.CPU

import com.luigicxv711.x64em.Hardware.BIOS.BIOS
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.RAX
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
object SegmentFlags {
    const val GRANULARITY_4K = 1 shl 0   // G
    const val DEFAULT_32    = 1 shl 1   // D/B
    const val LONG_MODE     = 1 shl 2   // L (ignore for now)
    const val AVAILABLE     = 1 shl 3   // AVL
}
data class SegDescriptor(
    val base: Int,
    val limit: Int,
    val access: Int,
    val flags: Int,
)
val SegDescriptor.default32 get() = flags and SegmentFlags.DEFAULT_32 != 0
val SegDescriptor.granularity get() = flags and SegmentFlags.GRANULARITY_4K != 0
val SegDescriptor.isCode get() = access and 0x08 != 0
val SegDescriptor.expandDown get() = !isCode && (access and 0x04 != 0)
val SegDescriptor.present get() = access and 0x80 != 0
object RFlags {
    const val CF = 1L shl 0;
    const val PF = 1L shl 2;
    const val AF = 1L shl 4;
    const val ZF = 1L shl 6;
    const val SF = 1L shl 7;
    const val TF = 1L shl 8
    const val IF = 1L shl 9;
    const val DF = 1L shl 10;
    const val OF = 1L shl 11;
}
typealias Opcode = (CPU, Int, Int) -> Int
class CPU : HardwareComp() {
    var physFn: (Int, Int) -> Long = ::physReal

    fun reg8(reg: Long, high: Boolean = false) = if (high) ((reg and AH_MASK) ushr 8).toInt() else (reg and AL_MASK).toInt()


    fun writeFlag(flag: Long, cond: Boolean) {
        rflags = if (cond) rflags or flag else rflags and flag.inv()
    }

    fun clearFlag(flag: Long) {
        rflags = rflags and flag.inv()
    }

    fun raiseInterrupt(num: Int) {
        halted = false

        // Push FLAGS
        push16(this, rflags.toInt())

        // Clear IF and TF
        clearFlag(RFlags.IF)
        clearFlag(RFlags.TF)

        // Push CS and IP
        push16(this, cs)
        push16(this, ip)

        // Load vector from IVT (real mode)
        val vecAddr = (num * 4).toLong()
        ip = read16(vecAddr)
        cs = read16(vecAddr + 2)
    }

    fun decodeDescriptor(raw: ByteArray): SegDescriptor {
        val base =
            (raw[2].toInt() and 0xFF) or
                    ((raw[3].toInt() and 0xFF) shl 8) or
                    ((raw[4].toInt() and 0xFF) shl 16) or
                    ((raw[7].toInt() and 0xFF) shl 24)

        var limit =
            (raw[0].toInt() and 0xFF) or
                    ((raw[1].toInt() and 0xFF) shl 8) or
                    ((raw[6].toInt() and 0x0F) shl 16)

        val access = raw[5].toInt() and 0xFF
        val flags = (raw[6].toInt() shr 4) and 0x0F

        return SegDescriptor(base, limit, access, flags)
    }

    fun getDescriptor(selector: Int): SegDescriptor {
        val sel = selector and 0xFFFC
        val index = sel ushr 3

        require(index != 0) { "GP fault: null selector" }

        val isLdt = ((sel ushr 2) and 1) == 1
        val tableBase: Int
        val tableLimit: Int

        if (isLdt) {
            require(ldtLimit != 0) { "GP fault: LDT not loaded" }
            tableBase = ldtBase
            tableLimit = ldtLimit
        } else {
            tableBase = gdtBase
            tableLimit = gdtLimit
        }

        val offset = index * 8
        require(offset + 7 <= tableLimit) { "GP fault: selector out of bounds" }

        val addr = tableBase + offset
        val raw = ByteArray(8) {
            read8((addr + it).toLong()).toByte()
        }

        return decodeDescriptor(raw)
    }

    fun physProtected(seg: Int, offset: Int): Long {
        val desc = getDescriptor(seg)
        require(desc.present) { "NP fault: segment not present" }

        val off =
            if (desc.default32)
                offset.toLong() and 0xFFFFFFFFL
            else
                offset.toLong() and 0xFFFF

        val limit =
            if (desc.granularity)
                (desc.limit.toLong() shl 12) or 0xFFFL
            else
                desc.limit.toLong()

        if (!desc.expandDown) {
            require(off <= limit) {
                "GP fault: offset $off > limit $limit"
            }
        } else {
            val max =
                if (desc.default32) 0xFFFFFFFFL else 0xFFFFL
            require(off > limit && off <= max) {
                "GP fault: expand-down offset $off invalid"
            }
        }

        return (desc.base.toLong() + off) and 0xFFFFFFFFL
    }

    fun physReal(cs: Int, ip: Int): Long {
        return ((cs shl 4) + (ip and 0xFFFF)).toLong() and 0xFFFFF
    } // this is CRUCIAL for Real Mode.

    fun setCPUMode(newMode: CPUModes) {
        mode = newMode
        physFn = when (newMode) {
            CPUModes.REAL -> ::physReal
            CPUModes.PROTECTED -> ::physProtected
            CPUModes.LONG -> ::physReal
        }
    }


    fun phys(cs: Int, ip: Int): Long =
        physFn(cs, ip)

    var gdtBase: Int = 0x00000000
    var gdtLimit: Int = 0x0000000

    var ldtBase: Int = 0x00000000
    var ldtLimit: Int = 0x0000000


    var portManager = PortManager(this)
    var ss = 0
    var sp = 0

    lateinit var diskPorts: Array<HardDisk?>

    fun push16(cpu: CPU, num: Int) {
        cpu.sp = (cpu.sp - 2) and 0xFFFF
        write16(cpu.phys(cpu.ss, cpu.sp), num)
    }
    fun pop16(cpu: CPU): Int {
        val res = read16(cpu.phys(cpu.ss, cpu.sp))
        cpu.sp = (cpu.sp + 2) and 0xFFFF
        return res
    }
    // i deadass seen ppl try to replicate the stack pointer math when u could just like do an array list :skull:

    fun hasFlag(cpu: CPU, flag: Long): Boolean {
        return (cpu.rflags and flag).toInt() != 0
    }

    fun insertDisk(disk: HardDisk, index: Int) {
        require(index >= 0)
        require(index < diskPorts.size)

        diskPorts[index] = disk
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
        const val BP = 21
        // bro why is the order swapped in x86
        val OpcodeDirectTable: Array<((CPU, Int, Int) -> Int)?> = Array(256) { null } // pre-init
        val ESPrefixOps: Array<((CPU, Int) -> Unit)?> = Array(256) { null }

        // load ALL opcodes
        init {
            OpcodeDirectTable[0x04] = { cpu, arg1, _ ->
                Opcodes.ADD_AL_imm8(cpu, arg1)
            } //  ADD AL, num
            OpcodeDirectTable[0x07] = { cpu, _, _ ->
                Opcodes.POP_ES(cpu)
            }
            OpcodeDirectTable[0x0C] = { cpu, arg1, _ ->
                Opcodes.OR_AL(cpu, arg1)
            }
            OpcodeDirectTable[0x0E] = { cpu, _, _ ->
                Opcodes.PUSH_CS(cpu)
            }
            OpcodeDirectTable[0x0F] = { cpu, _, _ ->
                Opcodes.Group0x0F_INS(cpu)
            } //  ADD AL, num

            OpcodeDirectTable[0x74] = { cpu, num, _ ->
                Opcodes.JE_short(cpu, num)
            }

            OpcodeDirectTable[0x88] = { cpu, modrmnum, _ ->
                Opcodes.MOVrm8(cpu, modrmnum)
            }

            OpcodeDirectTable[0x24] = { cpu, arg1, _ ->
                Opcodes.AND_AL(cpu, arg1)
            }

            OpcodeDirectTable[0x26] = { cpu, arg1, _ ->
                Opcodes.ES_PREFIX_OPCODES(cpu, arg1)
            }

            OpcodeDirectTable[0x2C] = { cpu, arg1, _ ->
                Opcodes.SUB_AL_imm8(cpu, arg1)
            }

            OpcodeDirectTable[0x30] = { cpu, arg1, _ ->
                Opcodes.XOR_rm8_8(cpu, arg1)
            }

            OpcodeDirectTable[0x34] = { cpu, arg1, _ ->
                Opcodes.XOR_AL(cpu, arg1)
            }

            OpcodeDirectTable[0x3C] = { cpu, arg1, _ ->
                Opcodes.CMP_AL(cpu, arg1)
            }

            OpcodeDirectTable[0x49] = { cpu, _, _ ->
                Opcodes.DEC_CX(cpu)
            }

            OpcodeDirectTable[0x6E] = { cpu, _, _ ->
                Opcodes.OUTSB(cpu)
            }

            OpcodeDirectTable[0x83] = { cpu, arg1, _ ->
                Opcodes.Group16B_1(cpu, arg1)
            }


            OpcodeDirectTable[0x8E] = { cpu, arg1, _ ->
                Opcodes.MOV_reg2seg(cpu, arg1)
            }

            OpcodeDirectTable[0xA4] = { cpu, _, _ ->
                Opcodes.MOVSB(cpu)
            }

            OpcodeDirectTable[0xAA] = { cpu, _, _ ->
                Opcodes.STOSB(cpu)
            }

            OpcodeDirectTable[0xAB] = { cpu, _, _ ->
                Opcodes.STOSW(cpu)
            }

            OpcodeDirectTable[0xB0] = { cpu, src, _ ->
                Opcodes.MOV_AL(cpu, src)
            }
            OpcodeDirectTable[0xB2] = { cpu, src, _ ->
                Opcodes.MOV_DL(cpu, src)
            }
            OpcodeDirectTable[0xB3] = { cpu, src, _ ->
                Opcodes.MOV_BL(cpu, src)
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
            OpcodeDirectTable[0xFC] = { cpu, _, _ ->
                Opcodes.CLD(cpu)
            }
            OpcodeDirectTable[0xFE] = { cpu, num, _ ->
                Opcodes.Group4_INS(cpu, num)
            }

            // ES OPCODES
            ESPrefixOps[0xA0] = ES_Opcodes::MOV_AL
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

    fun INT10h(ftype: Int, value: Int): Boolean {
        when (ftype) {
            0x00 -> {
                GPU?.let { gpu ->
                    val mode = when (value) {
                        0x13 -> GPUModes.Standard13h
                        0x12 -> GPUModes.Standard12h
                        0x03 -> GPUModes.TextMode
                        else -> GPUModes.Standard13h
                    }
                    gpu.switchMode(mode)
                }
            } // set video mode
            0x0E -> {
                GPU?.let { gpu ->
                    with (gpu) {
                        when (mode) {
                            GPUModes.TextMode -> {
                                teletyping = true
                                val offset = (cursorY * 80 + cursorX) * 2
                                write8(GenericVGAGPU.VGA_TEXT_BASE + offset, value)
                                write8(GenericVGAGPU.VGA_TEXT_BASE + offset + 1, 0x0F)      // white on black
                                teletyping = false

                                cursorX++
                                if (cursorX == 80) {
                                    cursorX = 0
                                    cursorY++
                                    if (cursorY == 25) {
                                        scrollText()
                                        cursorY--
                                    }
                                }
                            }
                            GPUModes.Standard13h -> {

                            }
                            GPUModes.Standard12h -> {

                            }
                        }
                    }
                }
            }
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

    fun updateFlags(res: Int, inc: Boolean, old: Int, bits: Int) {
        val mask = (1 shl bits) - 1
        val signBit = 1 shl (bits - 1)

        val r = res and mask
        val o = old and mask
        var newFlags = 0L

        // ZF
        if (r == 0) newFlags = newFlags or RFlags.ZF

        // SF
        if ((r and signBit) != 0) newFlags = newFlags or RFlags.SF

        // PF (always low 8 bits!)
        newFlags = newFlags or parityLookup[r and 0xFF]

        // AF
        if (((o xor r) and 0x10) != 0) newFlags = newFlags or RFlags.AF

        // OF
        if (inc) {
            if (o == signBit - 1 && r == signBit)
                newFlags = newFlags or RFlags.OF
        } else {
            if (o == signBit && r == signBit - 1)
                newFlags = newFlags or RFlags.OF
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
            updateFlags(res, true, value, 8)
        } else {
            // [a to d]h
            val idx = rm - 4
            var reg = registers[idx]
            val value = ((reg shr 8) and 0xFF).toInt()
            val res = (value + 1) and 0xFF

            registers[idx] = (reg and -0xFF01L) or (res.toLong() shl 8)
            updateFlags(res, false, value, 8)
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
        portManager.registerPort(0x3C0..0x3CF, VGAPort(this))
        portManager.registerPort(0x40..0x40, PITPort(this))

        diskPorts = arrayOfNulls(18) // first two are for floppies
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

        GPU?.let { gpu ->
            val size = gpu.modeSize
            when (gpu.mode) {
                GPUModes.Standard13h, GPUModes.Standard12h -> {
                    val vga_b = GenericVGAGPU.VGA_BASE
                    if (address in vga_b until vga_b + size) {
                        return gpu.read8(address)
                    }
                }
                GPUModes.TextMode -> {
                    val vga_text = GenericVGAGPU.VGA_TEXT_BASE
                    if (address in vga_text until vga_text + size) {
                        return gpu.read8(address)
                    }
                }
            }
        }

        RAM?.read8(address)

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
            val size = gpu.modeSize
            when (gpu.mode) {
                GPUModes.Standard13h, GPUModes.Standard12h -> {
                    val vga_b = GenericVGAGPU.VGA_BASE
                    if (address in vga_b until vga_b + size) {
                        gpu.write8(address, value)
                        return
                    }
                }
                GPUModes.TextMode -> {
                    val vga_text = GenericVGAGPU.VGA_TEXT_BASE
                    if (address in vga_text until vga_text + size) {
                        gpu.write8(address, value)
                        return
                    }
                }
            }
        }

        RAM?.write8(address, value)
    }

    fun write16(address: Long, value: Int) {
        val vall = value and 0xFFFF
        write8(address, vall and 0xFF)
        write8(address+1, vall ushr 8)
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