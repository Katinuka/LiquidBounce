package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.block.BlockUtils
import net.ccbluex.liquidbounce.config.boolean
import net.minecraft.init.Blocks.lava
import net.minecraft.init.Blocks.water
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action
import net.minecraft.util.EnumFacing

object NoFluid : Module("NoFluid", Category.MOVEMENT) {

    val waterValue by boolean("Water", true)
    val lavaValue by boolean("Lava", true)
    private val oldGrim by boolean("OldGrim",false)

    @EventTarget
    fun onUpdate(event: UpdateEvent){
        if ((waterValue || lavaValue) && oldGrim) {
            val searchBlocks = BlockUtils.searchBlocks(2, setOf(water, lava))
            for (block in searchBlocks) {
                val blockpos = block.key
                //TODO:only do this for blocks that player touched
                sendPacket(C07PacketPlayerDigging(Action.STOP_DESTROY_BLOCK, blockpos, EnumFacing.DOWN))
            }
        }
    }
}
