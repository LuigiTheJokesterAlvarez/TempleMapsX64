package com.github.ssquadteam.templemaps

import com.github.ssquadteam.talelib.command.TaleCommand
import com.github.ssquadteam.talelib.command.TaleContext
import com.github.ssquadteam.talelib.message.error
import com.github.ssquadteam.talelib.message.info
import com.github.ssquadteam.talelib.message.muted
import com.github.ssquadteam.talelib.message.success
import com.github.ssquadteam.templemaps.TempleCommand.CliCommand
import com.github.ssquadteam.templemaps.TempleCommand.ClickCommand
import com.github.ssquadteam.templemaps.TempleCommand.JoinCommand
import com.github.ssquadteam.templemaps.TempleCommand.KeyCommand
import com.github.ssquadteam.templemaps.TempleCommand.LeaveCommand
import com.github.ssquadteam.templemaps.TempleCommand.ListImagesCommand
import com.github.ssquadteam.templemaps.TempleCommand.MouseCommand
import com.github.ssquadteam.templemaps.TempleCommand.StartCommand
import com.github.ssquadteam.templemaps.TempleCommand.StatusCommand
import com.github.ssquadteam.templemaps.TempleCommand.StopCommand
import com.github.ssquadteam.templemaps.TempleCommand.TypeCommand
import com.github.ssquadteam.videomaps.MapDisplayManager
import com.luigicxv711.x64em.Hardware.GPU.GenericVGAGPU
import com.luigicxv711.x64em.Hardware.Keyboard.Keyboard
import com.luigicxv711.x64em.Hardware.X64Em
import java.io.File

class X64EMCommand : TaleCommand("yoman", "Run x86 OSes on the world map") {
    companion object {
        const val DISPLAY_ID = "yo_displayer"
        var em = X64Em(GenericVGAGPU(), Keyboard(), 32)
        init {
            em.loadBIOS("C:\\Users\\luisa\\Downloads\\nasm-3.01rc9-win64\\testoplano2.bin")
        }
    }

    init {
        subCommand(StartCommand())
        subCommand(JoinCommand())
    }

    override fun onExecute(ctx: TaleContext) {
        ctx.reply("x86 Emulator Commands:".info())
        /*
        ctx.reply("  /temple list - List available disk images".muted())
        ctx.reply("  /temple start <image> - Start OS (e.g., win95, freedos)".muted())
        ctx.reply("  /temple stop - Stop TempleOS".muted())
        ctx.reply("  /temple join - Join as player".muted())
        ctx.reply("  /temple leave - Leave session".muted())
        ctx.reply("  /temple status - Show status".muted())
        ctx.reply("  /temple type <text> - Type text".muted())
        ctx.reply("  /temple key <key> - Send key".muted())
        ctx.reply("  /temple click [times <n>] - Left click (or n times)".muted())
        ctx.reply("  /temple mouse <x> <y> - Move mouse".muted())
        ctx.reply("  /temple cli <cmd> - Run CLI command".muted())
         */
        ctx.reply("   /yoman start - start the test")
    }

    class StartCommand : TaleCommand("start", "Start an OS from disk image") {
        private val imageArg = stringArg("image", "Image filename (e.g., win95.img, freedos.iso)")

        override fun onExecute(ctx: TaleContext) {

            if (em.running) {
                ctx.reply("Emulator is already running! Use /temple stop first.".error())
                return
            }

            /*

            val imageName = ctx.get(imageArg)
            val imagesDir = File("mods/SSquadTeam_TempleMaps")

            var imageFile = File(imagesDir, imageName)
            if (!imageFile.exists()) {
                for (ext in listOf(".img", ".iso", ".IMG", ".ISO")) {
                    imageFile = File(imagesDir, "$imageName$ext")
                    if (imageFile.exists()) break
                }
            }

            if (!imageFile.exists()) {
                ctx.reply("Image not found: $imageName".error())
                ctx.reply("Place disk images in: mods/SSquadTeam_TempleMaps/".muted())
                ctx.reply("Supported: .img (HDD), .iso (CD-ROM)".muted())
                return
            }

            val ext = imageFile.extension.lowercase()
            val bootType = when (ext) {
                "iso" -> TempleEngine.BootType.CDROM
                "img" -> TempleEngine.BootType.HDA
                else -> TempleEngine.BootType.HDA
            }

            val ramMB = when {
                imageName.contains("win95", ignoreCase = true) -> 480
                imageName.contains("win98", ignoreCase = true) -> 512
                imageName.contains("dos", ignoreCase = true) -> 64
                else -> 256
            }

             */

            if (!MapDisplayManager.exists(DISPLAY_ID)) {
                MapDisplayManager.create {
                    id = DISPLAY_ID
                    startChunkX = 0
                    startChunkZ = 0
                    widthChunks = 10
                    heightChunks = 8
                }
            }

            ctx.reply("Starting VM (32 MB RAM)...".info())

            try {
                if (em.init()) {
                    Thread.sleep(1000)

                    MapDisplayManager.startAnimation(
                        displayId = DISPLAY_ID,
                        frameProvider = X64EMProvider(),
                        frameCount = 0,
                        fps = 30,
                        loop = true
                    )

                    ctx.reply("Emulator started!".success())
                    ctx.reply("Use /yoman join to play.".muted())
                } else {
                    ctx.reply("Failed to start emulator. Check console.".error())
                }
            } catch (e: Exception) {
                ctx.reply("Failed to start: ${e.message}".error())
                e.printStackTrace()
            }
        }
    }

    class JoinCommand : TaleCommand("join", "Join emulator session") {
        override fun onExecute(ctx: TaleContext) {
            val player = ctx.requirePlayer() ?: return
            /*
            val engine = TempleMapsPlugin.instance.engine

            if (!engine.isRunning()) {
                ctx.reply("Emulator is not running! Use /temple start first.".error())
                return
            }
             */
            TempleMapsPlugin.instance.addPlayer(player, DISPLAY_ID)
            ctx.reply("Joined! Open your map (M) to view.".success())
            /*
            ctx.reply("Controls (change mode via hotbar slots 1-9):".info())
            ctx.reply("  Slot 1: Mouse (Fast) - WASD moves cursor, Jump clicks".muted())
            ctx.reply("  Slot 2: Arrow Keys - WASD = arrows, Jump = Enter".muted())
            ctx.reply("  Slot 3: System - W=ESC, A=F1, S=F5, D=Menu".muted())
            ctx.reply("  Slot 4: Windows - Maximize, Tile, Next Window".muted())
            ctx.reply("  Slot 5: Zoom - Zoom In/Out, Scroll".muted())
            ctx.reply("  Slot 6: Terminal - New Terminal, Tab".muted())
            ctx.reply("  Slot 7: Text Nav - PageUp/Down, Home/End".muted())
            ctx.reply("  Slot 8: Modifiers - Toggle Shift/Ctrl/Alt".muted())
            ctx.reply("  Slot 9: Mouse (Slow) - Micro adjustments, Jump clicks".muted())

             */
        }
    }
}