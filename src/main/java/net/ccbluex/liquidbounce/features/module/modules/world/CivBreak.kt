/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPackets
import net.ccbluex.liquidbounce.utils.rotation.RotationSettings
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.faceBlock
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.setTargetRotation
import net.ccbluex.liquidbounce.utils.block.BlockUtils.getCenterDistance
import net.ccbluex.liquidbounce.utils.extensions.block
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawBlockBox
import net.ccbluex.liquidbounce.config.boolean
import net.ccbluex.liquidbounce.config.float
import net.minecraft.init.Blocks.air
import net.minecraft.init.Blocks.bedrock
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.START_DESTROY_BLOCK
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import java.awt.Color

object CivBreak : Module("CivBreak", Category.WORLD) {

    private val range by float("Range", 5F, 1F..6F)
    private val visualSwing by boolean("VisualSwing", true, subjective = false)

    private val options = RotationSettings(this).withoutKeepRotation()

    private var blockPos: BlockPos? = null
    private var enumFacing: EnumFacing? = null

    @EventTarget
    fun onBlockClick(event: ClickBlockEvent) {
        if (event.clickedBlock?.let { it.block } == bedrock) {
            return
        }

        blockPos = event.clickedBlock ?: return
        enumFacing = event.enumFacing ?: return

        // Break
        sendPackets(
            C07PacketPlayerDigging(START_DESTROY_BLOCK, blockPos, enumFacing),
            C07PacketPlayerDigging(STOP_DESTROY_BLOCK, blockPos, enumFacing)
        )
    }

    @EventTarget
    fun onRotationUpdate(event: RotationUpdateEvent) {
        val pos = blockPos ?: return
        val isAirBlock = pos.block == air

        if (isAirBlock || getCenterDistance(pos) > range) {
            blockPos = null
            return
        }

        if (options.rotationsActive) {
            val spot = faceBlock(pos) ?: return

            setTargetRotation(spot.rotation, options = options)
        }
    }

    @EventTarget
    fun onTick(event: GameTickEvent) {
        blockPos ?: return
        enumFacing ?: return

        if (visualSwing) {
            mc.thePlayer.swingItem()
        } else {
            sendPacket(C0APacketAnimation())
        }

        // Break
        sendPackets(
            C07PacketPlayerDigging(START_DESTROY_BLOCK, blockPos, enumFacing),
            C07PacketPlayerDigging(STOP_DESTROY_BLOCK, blockPos, enumFacing)
        )

        mc.playerController.clickBlock(blockPos, enumFacing)
    }

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        drawBlockBox(blockPos ?: return, Color.RED, true)
    }
}