package com.luigicxv711.x64em.Hardware.CPU

import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.DI
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.ES
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.RAX
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.RBX
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.RCX
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.RDX
import com.luigicxv711.x64em.Hardware.CPU.CPU.Indexes.SI
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

        cpu.updateFlags(result, false, al)

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

        cpu.updateFlags(result, false, al)

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
        val offLo = cpu.read8(cpu.phys(cpu.cs, (cpu.ip + 1) and 0xFFFF))
        val offHi = cpu.read8(cpu.phys(cpu.cs, (cpu.ip + 2) and 0xFFFF))
        val offset = offLo or (offHi shl 8)

        // read segment
        val segLo = cpu.read8(cpu.phys(cpu.cs, (cpu.ip + 3) and 0xFFFF))
        val segHi = cpu.read8(cpu.phys(cpu.cs, (cpu.ip + 4) and 0xFFFF))
        val segment = segLo or (segHi shl 8)

        // do the jump
        cpu.cs = segment and 0xFFFF
        cpu.ip = offset and 0xFFFF
        return 0
    }
    fun INTERRUPT(cpu: CPU, interruptNum: Int): Int {
        val rax = cpu.registers[RAX]
        val rbx = cpu.registers[RCX]
        val rcx = cpu.registers[RCX]
        val rdx = cpu.registers[RDX]
        val al = (rax and AL_MASK).toInt()
        val ah = ((rax and AH_MASK) ushr 8).toInt()

        val bl = (rbx and AL_MASK).toInt()
        val bh = ((rbx and AH_MASK) ushr 8).toInt()

        val cl = (rcx and AL_MASK).toInt()
        val ch = ((rcx and AH_MASK) ushr 8).toInt()

        val dl = (rdx and AL_MASK).toInt()
        val dh = ((rdx and AH_MASK) ushr 8).toInt()
        val skip = when (interruptNum) {
            // INT 0x10
            0x10 -> {
                cpu.setVideoMode(ah, al)
            }
            0x13 -> {
                val disk = cpu.HardDisk
                if (al in 1..127) {
                    when (ah) {
                        0x03 -> {
                            val secs = al
                            val sec = cl and 0x3F
                            val cyl =
                                (ch or ((cl and 0xC0) shl 2))
                            val head = dh

                            val arr = ByteArray(secs * 512)
                            val bx = (rbx and AX_MASK).toInt()
                            // get all the data out of es:bx
                            for (i in 0..secs * 512) {
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
            0x15 -> {
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
            0x16 -> {
                cpu.keyboardHandle(ah, al)
            }
            else -> true
        }
        return if (skip) 2 else 0
    }
    fun Group16B_1(cpu: CPU, modrmNum: Int): Int {
        val imm8 = cpu.read8(cpu.phys(cpu.cs, cpu.ip + 2)).toByte() // signed
        val imm = imm8.toInt() and 0xFFFF                           // sign-extend to 16-bit
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
                    cpu.updateFlags16(result, inc = true, old = dest)
                }
                1 -> { // or
                    val result = (dest or imm) and 0xFFFF
                    cpu.setReg16(cpu, rm, result)
                    cpu.updateFlags16(result, inc = true, old = dest)
                }
                2 -> { // adc
                    val cf = if (cpu.hasFlag(cpu, RFlags.CF)) 1 else 0
                    val result = (dest + imm + cf) and 0xFFFF
                    cpu.setReg16(cpu, rm, result)
                    cpu.updateFlags16(result, inc = true, old = dest)
                }
                3 -> { // sbb
                    val cf = if (cpu.hasFlag(cpu, RFlags.CF)) 1 else 0
                    val result = (dest - imm - cf) and 0xFFFF
                    cpu.setReg16(cpu, rm, result)
                    cpu.updateFlags16(result, inc = false, old = dest)
                }
                4 -> { // and
                    val result = (dest and imm) and 0xFFFF
                    cpu.setReg16(cpu, rm, result)
                    cpu.updateFlags16(result, inc = true, old = dest)
                }
                5 -> { // sub
                    val result = (dest - imm) and 0xFFFF
                    cpu.setReg16(cpu, rm, result)
                    cpu.updateFlags16(result, inc = false, old = dest)
                }
                6 -> { // xor
                    val result = (dest xor imm) and 0xFFFF
                    cpu.setReg16(cpu, rm, result)
                    cpu.updateFlags16(result, inc = true, old = dest)
                }
                7 -> { // cmp
                    val result = (dest - imm) and 0xFFFF
                    cpu.rflags = if (result == 0) cpu.rflags or RFlags.ZF else cpu.rflags and RFlags.ZF.inv()
                    cpu.updateFlags16(result, inc = false, old = dest)
                }
            }
        }

        return 3
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
        return 2
    }
    fun MOV_AL(cpu: CPU, src: Int): Int {
        val raxCleaned = cpu.registers[RAX] and CLEAR_AL_MASK
        cpu.registers[RAX] = raxCleaned or (src.toLong() and AL_MASK) // optimizationingo
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
        println("si ${cpu.registers[SI]}")
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
    fun MOV_DX(cpu: CPU): Int {
        val value = cpu.read16(cpu.phys(cpu.cs, cpu.ip + 1))
        val raxCleaned = cpu.registers[RDX] and CLEAR_AX_MASK // clear lower 16 bits
        cpu.registers[RDX] = raxCleaned or (value.toLong() and 0xFFFFL)
        return 3
    }
    fun CMP_AL(cpu: CPU, arg1: Int): Int {
        val al = (cpu.registers[RAX] and AL_MASK).toInt()
        val res = al - arg1
        cpu.rflags = if (res == 0) cpu.rflags or RFlags.ZF else cpu.rflags and RFlags.ZF.inv()

        cpu.rflags = if ((arg1 and 0xFF) > al)
            cpu.rflags or RFlags.CF
        else
            cpu.rflags and RFlags.CF.inv() // when te suicidas
        return 2
    }
    fun JE(cpu: CPU): Int {
        if (cpu.rflags and RFlags.ZF != 0L) {
            return JMPFAR16bit(cpu)
        }
        return 2
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
        val cx = (cpu.registers[RCX] and AX_MASK).toInt()
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
    fun Group0x0F_INS(cpu: CPU, numbah: Int): Int {
        var mrmBits = cpu.modrmDecode(numbah)
        val mod = (mrmBits ushr 16) and 0xFF
        val reg = (mrmBits ushr 8) and 0xFF
        val rm  = mrmBits and 0xFF
        if (mod == 3) {
            when (reg) {
                // 132nd instruction here what the actual fuck
                0x84 -> return JE(cpu)
            }
        }
        return 2
    }
    fun MOVrm8(cpu: CPU, modrmNum: Int): Int {
        val displacement = cpu.read8(cpu.phys(cpu.cs, cpu.ip + 2))
        val mrmBits = cpu.modrmDecode(modrmNum)
        val mod = (mrmBits ushr 16) and 0xFF
        val reg = (mrmBits ushr 8) and 0xFF
        val rm  = mrmBits and 0xFF

        val value = cpu.getReg(cpu, reg)

        if (mod == 3) {
            cpu.setReg(cpu, reg, value)
            return 2
        }
        val addr = cpu.CalcEffAddr(mod, rm, displacement).toLong()
        cpu.write8(addr, value)

        return 2
    }
    fun STOSB(cpu: CPU): Int {
        val al = (cpu.registers[RAX] and AL_MASK).toInt()
        val es = cpu.registers[ES].toInt() and 0xFFFF
        val di = cpu.registers[DI].toInt() and 0xFFFF

        val addr = cpu.phys(es, di)
        cpu.write8(addr, al)

        cpu.registers[DI] = (di + 1).toLong() // assuming df is cleared

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
            }
        }
        return 2
    }

}