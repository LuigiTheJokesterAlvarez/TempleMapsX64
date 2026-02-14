package com.luigicxv711.x64em.Hardware.CPU

import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.BP
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.DI
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.ES
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.ESPrefixOps
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.RAX
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.RBX
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.RCX
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.RDX
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.SI
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.parityLookup
import com.luigicxv711.x64em.Hardware.CPU.FUNCTIONUtils.getAX
import com.luigicxv711.x64em.Hardware.CPU.FUNCTIONUtils.getCX
import com.luigicxv711.x64em.Hardware.CPU.FUNCTIONUtils.setCX
import com.luigicxv711.x64em.Hardware.HardDisk.HardDisk
import com.luigicxv711.x64em.Hardware.Ports.PITPort

// ALL OF THE OPCODES IN THE X86 INSTRUCTION SET
object Opcodes {
    fun ADD_AL_imm8(cpu: CPU, imm: Int): Int {
        var comps = cpu.getRAXcomponents()
        val al = comps.al
        val result = (al + imm)
        val int8res = result and 0xFF
        val raxCleaned = cpu.registers[RAX] and -0x100L
        cpu.registers[RAX] = raxCleaned or (int8res.toLong() and 0xFFL) // optimizationingo

        cpu.updateFlags(result, false, al, 8)

        cpu.rflags = if (result > 0xFF) cpu.rflags or RFlags.CF else cpu.rflags and RFlags.CF.inv()
        return 2
    }
    fun SUB_AL_imm8(cpu: CPU, imm: Int): Int {
        val comps = cpu.getRAXcomponents()
        val al = comps.al

        val result = al - (imm and 0xFF)
        val int8res = result and 0xFF

        val raxCleaned = cpu.registers[RAX] and -0x100L
        cpu.registers[RAX] = raxCleaned or (int8res.toLong() and 0xFFL) // optimizationingo

        cpu.updateFlags(result, false, al, 8)

        cpu.rflags = if (imm > al) cpu.rflags or RFlags.CF else cpu.rflags and RFlags.CF.inv()
        return 2
    }
    fun CLI(cpu: CPU, arg1: Int = 0): Int {
        cpu.rflags = cpu.rflags and RFlags.IF.inv()
        return 1
    }
    fun JMPShort(cpu: CPU, arg1: Int): Int {
        val rel = arg1.toByte().toInt() // sign-extend
        cpu.ip = (cpu.ip + 2 + rel) and 0xFFFF
        return 0
    }
    fun JMPFAR16bit(cpu: CPU): Int {
        val offset = cpu.read16(cpu.phys(cpu.cs, cpu.ip+1))

        val segment = cpu.read16(cpu.phys(cpu.cs, cpu.ip+3))

        // do the jump
        cpu.cs = segment and 0xFFFF
        cpu.ip = offset and 0xFFFF
        return 0
    }
    // push and pop
    fun PUSH_CS(cpu: CPU): Int {
        cpu.push16(cpu, cpu.cs)
        return 1
    }
    fun POP_ES(cpu: CPU): Int {
        val num = cpu.pop16(cpu)
        cpu.registers[ES] = num.toLong()
        return 1
    }
    val interrupts: Array<((CPU) -> Boolean)?> = Array(0x100, {null})
    init {
        interrupts[0x10] = { cpu ->
            val rax = cpu.registers[RAX]
            val al = (rax and AL_MASK).toInt()
            val ah = ((rax and AH_MASK) ushr 8).toInt()
            cpu.INT10h(ah, al)
            true
        }
        interrupts[0x13] = { cpu ->
            val rax = cpu.registers[RAX]
            val rbx = cpu.registers[RBX]
            val rcx = cpu.registers[RCX]
            val rdx = cpu.registers[RDX]
            val al = (rax and AL_MASK).toInt()
            val ah = ((rax and AH_MASK) ushr 8).toInt()

            val cl = (rcx and AL_MASK).toInt()
            val ch = ((rcx and AH_MASK) ushr 8).toInt()

            val dl = (rdx and AL_MASK).toInt()
            val dh = ((rdx and AH_MASK) ushr 8).toInt()

            val bx = (rbx and AX_MASK).toInt()

            val idx = FUNCTIONUtils.DLtoDISK(dl)

            val disk: HardDisk? = idx?.let { cpu.diskPorts[it] }

            if (al in 1..127) {
                when (ah) {
                    0x02 -> {
                        if (!FUNCTIONUtils.checkESBXValid(bx)) {
                            // fuck you
                            throw IllegalStateException(
                                "Holy shit Gromit, someone tried to break the emulator!"
                            )
                        }
                        val secs = al
                        val sec = cl and 0x3F
                        val cyl =
                            (ch or ((cl and 0xC0) shl 2))
                        val head = dh

                        val diskAddr = HardDisk.CHS2SEC(cyl, head, sec)
                        val res = disk?.readSectors(diskAddr, secs)
                        if (res != null) {
                            for (i in 0 until res.size) {
                                val data = res[i]
                                // ARRAYCOPY
                                for (j in 0 until 512) {
                                    cpu.write8(cpu.phys(cpu.registers[ES].toInt(), bx + i * 512 + j), data[j].toInt())
                                }
                            }
                        }
                    }
                    0x03 -> {
                        if (!FUNCTIONUtils.checkESBXValid(bx)) {
                            // fuck you
                            throw IllegalStateException(
                                "Holy shit Gromit, someone tried to break the emulator!"
                            )
                        }
                        val secs = al
                        println(secs)
                        val sec = cl and 0x3F
                        val cyl =
                            (ch or ((cl and 0xC0) shl 2))
                        val head = dh

                        val arr = ByteArray(secs * 512)
                        // get all the data out of es:bx
                        for (i in 0 until secs * 512) {
                            val data = cpu.read8(
                                cpu.phys(cpu.registers[ES].toInt(), bx + i)
                            )
                            arr[i] = data.toByte()
                        }
                        val diskAddr = HardDisk.CHS2SEC(cyl, head, sec)
                        disk?.writeSectors(diskAddr, arr, secs)
                    }
                }
            }
            true
        }
        interrupts[0x15] = { cpu ->
            val rax = cpu.registers[RAX]
            val ah = ((rax and AH_MASK) ushr 8).toInt()
            when (ah) {
                0x86 -> {
                    val cx = (cpu.registers[RCX] and AX_MASK)
                    val dx = (cpu.registers[RDX] and AX_MASK)
                    val microseconds = (cx shl 16) or dx
                    val ticks = (microseconds.toDouble() / 54945.0).toUInt()
                    val portinho = cpu.portManager.getPort(0x40) as? PITPort
                    portinho?.waitTicks(ticks)
                }
            }
            true
        }
        interrupts[0x16] = { cpu ->
            val rax = cpu.registers[RAX]
            val al = cpu.reg8(rax, false)
            val ah = cpu.reg8(rax, true)
            cpu.keyboardHandle(ah, al)
            true
        }
        interrupts[0x21] = { cpu ->
            val rax = cpu.registers[RAX]
            val al = cpu.reg8(rax, false)
            val ah = cpu.reg8(rax, true)
            val dx = (cpu.registers[RDX] and AX_MASK).toInt()
            with (cpu) {
                when (ah) {
                    0x09 -> {
                        var i = 0
                        while (true) {
                            val ch = read8(phys(ds, dx + i))
                            if (ch == '$'.code) break
                            INT10h(0x0E, ch)
                            i++
                        }
                    }
                }
            }
            true
        }
    }
    fun INTERRUPT(cpu: CPU, interruptNum: Int): Int {
        val skip = interrupts[interruptNum]?.invoke(cpu) ?: true
        return if (skip) 2 else 0
    }
    fun Group16B_1(cpu: CPU, modrmNum: Int): Int {
        val imm8 = cpu.read8(cpu.phys(cpu.cs, cpu.ip + 2)).toByte() // signed
        val imm = imm8.toInt()                     // sign-extend to 16-bit
        val mrmBits = cpu.modrmDecode(modrmNum)
        val mod = (mrmBits ushr 16) and 0xFF
        val reg = (mrmBits ushr 8) and 0xFF
        val rm  = mrmBits and 0xFF
        if (mod == 3) {
            val dest = cpu.getReg16(cpu, rm)
            // hail when statements
            when (reg) {
                0 -> { // add
                    val result = (dest + imm) and 0xFFFF
                    cpu.setReg16(cpu, rm, result)
                    cpu.updateFlags(result, inc = true, old = dest, 16)
                }
                1 -> { // or
                    val result = (dest or imm) and 0xFFFF
                    cpu.setReg16(cpu, rm, result)
                    cpu.updateFlags(result, inc = true, old = dest, 16)
                }
                2 -> { // adc
                    val cf = if (cpu.hasFlag(cpu, RFlags.CF)) 1 else 0
                    val result = (dest + imm + cf) and 0xFFFF
                    cpu.setReg16(cpu, rm, result)
                    cpu.updateFlags(result, inc = true, old = dest, 16)
                }
                3 -> { // sbb
                    val cf = if (cpu.hasFlag(cpu, RFlags.CF)) 1 else 0
                    val result = (dest - imm - cf) and 0xFFFF
                    cpu.setReg16(cpu, rm, result)
                    cpu.updateFlags(result, inc = false, old = dest, 16)
                }
                4 -> { // and
                    val result = (dest and imm) and 0xFFFF
                    cpu.setReg16(cpu, rm, result)
                    cpu.updateFlags(result, inc = true, old = dest, 16)
                }
                5 -> { // sub
                    val result = (dest - imm) and 0xFFFF
                    cpu.setReg16(cpu, rm, result)
                    cpu.updateFlags(result, inc = false, old = dest, 16)
                }
                6 -> { // xor
                    val result = (dest xor imm) and 0xFFFF
                    cpu.setReg16(cpu, rm, result)
                    cpu.updateFlags(result, inc = true, old = dest, 16)
                }
                7 -> { // cmp
                    val result = (dest - imm) and 0xFFFF
                    cpu.rflags = if (result == 0) cpu.rflags or RFlags.ZF else cpu.rflags and RFlags.ZF.inv()
                    cpu.updateFlags(result, inc = false, old = dest, 16)
                }
            }
        }

        return 3
    }

    // MEMORY LAYOUT:/
    //     0      1       2           3
    // [opcode][modrm][displacement][displacement2 (optional)]
    fun calcMEMaddr(cpu: CPU, rm: Int, mod: Int): Long {
        with(cpu) {
            val dispPtr = phys(cs, ip+2)
            var BPing = false
            val off = when (rm) {
                0 -> getReg16(cpu, RBX) + (registers[SI] and 0xFFFF)
                1 -> getReg16(cpu, RBX) + (registers[DI] and 0xFFFF)
                2 -> {
                    BPing = true
                    registers[BP] + (registers[SI] and 0xFFFF)
                }
                3 -> {
                    BPing = true
                    registers[BP] + (registers[DI] and 0xFFFF)
                }
                4 -> registers[SI]
                5 -> registers[DI]
                6 -> {
                    if (mod == 0) {
                        // disp16 only
                        read16(dispPtr)
                    } else {
                        BPing = true
                        registers[BP]
                    }
                }
                7 -> getReg16(cpu, RBX)
                else -> 0
            }


            val off2 = when (mod) {
                // if you notice carefully, the instruction with mod 0b01 could be like OP modrmbyte DISP1, so that means we must read after the modrmbyte
                0b01 -> read8(dispPtr).toByte().toInt()   // signed disp8
                0b10 -> read16(dispPtr)
                else -> 0
            }

            val segment = if (BPing)
                ss
            else
                ds

            return phys(segment, off.toInt() + off2)
        }
    }


    fun XOR_rm8_8(cpu: CPU, modrmNum: Int): Int {
        val mrmBits = cpu.modrmDecode(modrmNum)
        val mod = (mrmBits ushr 16) and 0xFF
        val reg = (mrmBits ushr 8) and 0xFF
        val rm  = mrmBits and 0xFF

        var a = 0
        var b = 0

        var idx = 0
        var destination = 0L

        if (mod == 0b11) {
            a = if (reg < 4)
                    (cpu.registers[reg] and AL_MASK).toInt()
            else
                    ((cpu.registers[reg - 4] and AH_MASK) ushr 8).toInt()

            idx = rm

            b = if (rm < 4)
                    (cpu.registers[rm] and AL_MASK).toInt()
            else
                    ((cpu.registers[rm - 4] and AH_MASK) ushr 8).toInt()
        } else {
            // mem to register?
            destination = calcMEMaddr(cpu, rm, mod)
            a = cpu.read8(destination)

            b = if (reg < 4)
                (cpu.registers[reg] and AL_MASK).toInt()
            else
                ((cpu.registers[reg - 4] and AH_MASK) ushr 8).toInt()
        }

        // it is dst xor src
        val result = (b xor a) and 0xFF

        with (cpu) {
            if (mod != 0b11) {
                write8(destination, result)
            } else {
                setReg(this, idx, result)
            }
            writeFlag(RFlags.ZF, result == 0)
            writeFlag(RFlags.SF, result and 0x80 != 0)
            writeFlag(RFlags.PF, Integer.bitCount(result) % 2 == 0)

            writeFlag(RFlags.CF, false)
            writeFlag(RFlags.OF, false)
        }
        val length = 2 + when(mod) {
            0b00 -> if (rm == 6) 2 else 0  // direct memory
            0b01 -> 1                       // disp8
            0b10 -> 2                       // disp16
            else -> 0                        // register-direct
        }
        return length
    }

    fun AND_AL(cpu: CPU, arg1: Int): Int {
        val rax = cpu.registers[RAX]
        val raxCleaned = rax and -0x100L
        val al = (rax and AL_MASK).toInt()
        val res = al and arg1
        cpu.registers[RAX] = raxCleaned or (res.toLong() and 0xFFL) // optimizationingo
        return 2
    }
    fun OR_AL(cpu: CPU, arg1: Int): Int {
        val rax = cpu.registers[RAX]
        val raxCleaned = rax and -0x100L
        val al = (rax and AL_MASK).toInt()
        val res = al or arg1
        cpu.registers[RAX] = raxCleaned or (res.toLong() and 0xFFL) // optimizationingo
        return 2
    }
    fun XOR_AL(cpu: CPU, arg1: Int): Int {
        val rax = cpu.registers[RAX]
        val raxCleaned = rax and CLEAR_AL_MASK
        val al = (rax and AL_MASK).toInt()
        val res = al xor arg1
        cpu.registers[RAX] = raxCleaned or (res.toLong() and 0xFFL) // optimizationingo

        cpu.writeFlag(RFlags.ZF, res == 0)
        cpu.writeFlag(RFlags.SF, (res and 0x80) != 0)
        cpu.writeFlag(RFlags.PF, parityLookup[res] != 0L)
        cpu.writeFlag(RFlags.CF, false)
        cpu.writeFlag(RFlags.OF, false)
        cpu.writeFlag(RFlags.AF, false)
        return 2
    }
    fun MOV_AL(cpu: CPU, src: Int): Int {
        val raxCleaned = cpu.registers[RAX] and CLEAR_AL_MASK
        cpu.registers[RAX] = raxCleaned or (src.toLong() and AL_MASK) // optimizationingo
        return 2
    }
    fun MOV_BL(cpu: CPU, src: Int): Int {
        val raxCleaned = cpu.registers[RBX] and CLEAR_AL_MASK
        cpu.registers[RBX] = raxCleaned or (src.toLong() and AL_MASK) // optimizationingo
        return 2
    }
    fun MOV_DL(cpu: CPU, src: Int): Int {
        val raxCleaned = cpu.registers[RDX] and CLEAR_AL_MASK
        cpu.registers[RDX] = raxCleaned or (src.toLong() and AL_MASK) // optimizationingo
        return 2
    }
    fun MOV_AH(cpu: CPU, src: Int): Int {
        val raxCleaned = cpu.registers[RAX] and CLEAR_AH_MASK
        cpu.registers[RAX] = raxCleaned or ((src.toLong() and 0xFF) shl 8)
        return 2
    }
    fun MOV_DI(cpu: CPU): Int {
        val value = cpu.read16(cpu.phys(cpu.cs, cpu.ip + 1))
        cpu.registers[DI] = value.toLong() and 0xFFFF
        return 3
    }
    fun MOV_AX(cpu: CPU): Int {
        val value = cpu.read16(cpu.phys(cpu.cs, cpu.ip + 1))
        val raxCleaned = cpu.registers[RAX] and CLEAR_AX_MASK // clear lower 16 bits
        cpu.registers[RAX] = raxCleaned or (value.toLong() and 0xFFFFL)
        return 3
    }
    fun MOV_BX(cpu: CPU): Int {
        val value = cpu.read16(cpu.phys(cpu.cs, cpu.ip + 1))
        val raxCleaned = cpu.registers[RBX] and CLEAR_AX_MASK // clear lower 16 bits
        cpu.registers[RBX] = raxCleaned or (value.toLong() and 0xFFFFL)
        return 3
    }
    fun MOV_CX(cpu: CPU): Int {
        val value = cpu.read16(cpu.phys(cpu.cs, cpu.ip + 1))
        val raxCleaned = cpu.registers[RCX] and CLEAR_AX_MASK // clear lower 16 bits
        cpu.registers[RCX] = raxCleaned or (value.toLong() and 0xFFFFL)
        return 3
    }
    fun MOV_SI(cpu: CPU): Int {
        val value = cpu.read16(cpu.phys(cpu.cs, cpu.ip + 1))
        cpu.registers[SI] = (value and 0xFFFF).toLong()
        return 3
    }
    fun OUT(cpu: CPU): Int {
        with (cpu) {
            val port = (registers[RDX] and AX_MASK).toInt()
            val al = (registers[RAX] and AL_MASK).toByte()

            portManager.writePort(port, byteArrayOf(al))
        }
        return 1
    }
    fun ES_PREFIX_OPCODES(cpu: CPU, op: Int): Int {
        with (cpu) {
            val arg = read16(cpu.phys(cs, ip + 2))
            val opfunc = ESPrefixOps[op]
            if (opfunc != null) {
                opfunc(this, arg)
            }
        }
        return 4
    }
    fun OUTSB(cpu: CPU): Int {
        with (cpu) {
            val port = (registers[RDX] and AX_MASK).toInt()
            val len = (registers[RCX] and AX_MASK).toInt()
            val arr = ByteArray(len)
            if (REP) {
                var si = registers[SI]
                for (i in 0 until len) {
                    arr[i] = read8(phys(ds, (si + i).toInt())).toByte()
                }
                registers[SI] += len

                registers[RCX] = (registers[RCX] and CLEAR_AX_MASK) or 0L

                REP = false
            } else {
                arr[0] = read8(phys(ds, registers[SI].toInt())).toByte()
                registers[SI] += 1

                val cx = ((registers[RCX] and AX_MASK).toInt() - 1) and 0xFFFF
                registers[RCX] = (registers[RCX] and CLEAR_AX_MASK) or (cx.toLong() and 0xFFFFL)
            }

            portManager.writePort(port, arr)
        }
        return 1
    }
    fun MOVSB(cpu: CPU): Int {
        with(cpu) {
            val len = (registers[RCX] and AX_MASK).toInt()
            if (REP) {
                ArrCPY(this, len)
                REP = false
                return 1
            }

            // single byte move
            val v = read8(phys(ds, registers[SI].toInt()))
            write8(phys(registers[ES].toInt(), registers[DI].toInt()), v)

            val step = if ((rflags and RFlags.DF) == 0L) 1 else -1
            registers[SI] += step
            registers[DI] += step

            // cx - 1
            val cx = ((registers[RCX] and AX_MASK).toInt() - 1) and 0xFFFF
            registers[RCX] = (registers[RCX] and CLEAR_AX_MASK) or (cx.toLong() and 0xFFFFL)

            return 1
        }
    }
    fun STOSW(cpu: CPU): Int {
        val ax = getAX(cpu)
        with (cpu) {
            val step = if ((rflags and RFlags.DF) == 0L) 2 else -2
            if (REP) {
                val cx = getCX(this)
                repeat (cx) {
                    write16(phys(registers[ES].toInt(), registers[DI].toInt()), ax)
                    registers[DI] += step
                }
                REP = false
                setCX(this, 0)
                return 1
            }

            write16(phys(registers[ES].toInt(), registers[DI].toInt()), ax)
            registers[DI] += step
            return 1
        }
    } // store write ax register
    fun MOV_DX(cpu: CPU): Int {
        val value = cpu.read16(cpu.phys(cpu.cs, cpu.ip + 1))
        val raxCleaned = cpu.registers[RDX] and CLEAR_AX_MASK // clear lower 16 bits
        cpu.registers[RDX] = raxCleaned or (value.toLong() and 0xFFFFL)
        return 3
    }
    fun CLD(cpu: CPU): Int {
        cpu.rflags = cpu.rflags and RFlags.DF.inv()
        return 1
    }
    fun CMP_AL(cpu: CPU, arg1: Int): Int {
        val a = ((cpu.registers[RAX] and AL_MASK).toInt()) and 0xFF
        val b = arg1 and 0xFF
        val res = (a - b) and 0xFF

        cpu.writeFlag(RFlags.ZF, res == 0)
        cpu.writeFlag(RFlags.SF, (res and 0x80) != 0)
        cpu.writeFlag(RFlags.PF, parityLookup[res] != 0L)
        cpu.writeFlag(RFlags.AF, ((a xor b xor res) and 0x10) != 0)
        cpu.writeFlag(RFlags.CF, a < b)
        val overflow = ((a xor b) and (a xor res) and 0x80) != 0
        cpu.writeFlag(RFlags.OF, overflow)
        return 2
    }
    fun JE(cpu: CPU): Int {
        val rel = cpu.read16(cpu.phys(cpu.cs, cpu.ip + 2)).toShort().toInt()
        if ((cpu.rflags and RFlags.ZF) != 0L) {
            cpu.ip = (cpu.ip + 4 + rel) and 0xFFFF
            return 0
        }
        return 4
    }
    fun JNE(cpu: CPU): Int {
        val rel = cpu.read16(cpu.phys(cpu.cs, cpu.ip + 2)).toShort().toInt()
        if ((cpu.rflags and RFlags.ZF) == 0L) {
            cpu.ip = (cpu.ip + 4 + rel) and 0xFFFF
            return 0
        }
        return 4
    }
    fun JE_short(cpu: CPU, arg1: Int): Int {
        val rel = arg1.toByte().toInt()
        if (cpu.hasFlag(cpu, RFlags.ZF)) {
            cpu.ip = (cpu.ip + 2 + rel) and 0xFFFF
            return 0
        }
        return 2
    }
    fun DEC_CX(cpu: CPU): Int {
        val cx = (cpu.registers[RCX] and AX_MASK).toInt() - 1
        val raxCleaned = cpu.registers[RCX] and CLEAR_AX_MASK // clear lower 16 bits
        cpu.registers[RCX] = raxCleaned or (cx.toLong() and 0xFFFFL)
        return 1
    }
    fun Group4_INS(cpu: CPU, numbah: Int): Int {
        var mrmBits = cpu.modrmDecode(numbah)
        val mod = (mrmBits ushr 16) and 0xFF
        val reg = (mrmBits ushr 8) and 0xFF
        val rm  = mrmBits and 0xFF

        if (mod == 3) {
            when (reg) {
                0 -> cpu.inc8(rm)
                1 -> cpu.dec8(rm)
            }
        }

        return 2 // 2 byte oh yeah
    }
    // 0x0F group opcodes
    fun Group0x0F_INS(cpu: CPU): Int {
        val opcode2 = cpu.read8(cpu.phys(cpu.cs, cpu.ip + 1))
        return when (opcode2) {
            0x84 -> JE(cpu)   // JE near
            0x85 -> JNE(cpu)  // JNE near (you’ll add this)
            else -> 2 // UNHANDLED!
        }
    }
    fun MOVrm8(cpu: CPU, modrmNum: Int): Int {
        val displacement = cpu.read8(cpu.phys(cpu.cs, cpu.ip + 2))
        val mrmBits = cpu.modrmDecode(modrmNum)
        val mod = (mrmBits ushr 16) and 0xFF
        val reg = (mrmBits ushr 8) and 0xFF
        val rm  = mrmBits and 0xFF

        val value = cpu.getReg(cpu, reg)

        if (mod == 3) {
            cpu.setReg(cpu, rm, value)
            return 2
        }
        val addr = cpu.CalcEffAddr(mod, rm, displacement).toLong()
        cpu.write8(addr, value)

        return 2
    }
    fun STOSB(cpu: CPU): Int {
        val al = (cpu.registers[RAX] and AL_MASK).toInt()
        val es = cpu.registers[ES].toInt() and 0xFFFF
        var di = cpu.registers[DI].toInt() and 0xFFFF

        val step = if (cpu.rflags and RFlags.DF != 0L) -1 else 1
        if (cpu.REP) {
            repeat (getCX(cpu)) {
                val addr = cpu.phys(es, di)
                cpu.write8(addr, al)
                di += step
            }
            cpu.registers[DI] += getCX(cpu) * step
            setCX(cpu, 0)
            cpu.REP = false
            return 1
        }

        val addr = cpu.phys(es, di)
        cpu.write8(addr, al)

        cpu.registers[DI] += step // assuming df is cleared

        return 1
    }
    fun LOOP(cpu: CPU, arg: Int): Int {
        val rel = arg.toByte().toInt()
        val oldCx = (cpu.registers[RCX] and 0xFFFF).toInt()
        val cx = (oldCx - 1) and 0xFFFF
        cpu.registers[RCX] = (cpu.registers[RCX] and -0x10000L) or (cx.toLong() and 0xFFFFL)

        if (oldCx != 1) { // if it wasn’t 1, CX != 0 after decrement → jump
            cpu.ip = (cpu.ip + 2 + rel) and 0xFFFF
            return 0
        }
        return 2
    }

    fun MOV_reg2seg(cpu: CPU, modrmNum: Int): Int {
        val mrmBits = cpu.modrmDecode(modrmNum)
        val mod = (mrmBits ushr 16) and 0xFF
        val reg = (mrmBits ushr 8) and 0xFF
        val rm  = mrmBits and 0xFF
        if (mod == 3) {
            when (reg) {
                0x00 -> { // ES
                    val value = cpu.getReg16(cpu, rm)
                    cpu.registers[ES] = (value and 0xFFFF).toLong()
                }
                0x03 -> { // ES
                    val value = cpu.getReg16(cpu, rm)
                    cpu.ds = (value and 0xFFFF)
                }
            }
        }
        return 2
    }

}