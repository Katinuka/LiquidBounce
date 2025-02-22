/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2024 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.purchasemode

import kotlinx.coroutines.delay
import net.ccbluex.liquidbounce.config.types.Choice
import net.ccbluex.liquidbounce.config.types.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.Sequence
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.AutoShopInventoryManager
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.AutoShopInventoryManager.hasReceivedItems
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.ModuleAutoShop
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.ModuleAutoShop.categorySwitchDelay
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.ModuleAutoShop.hasItemCategoryChanged
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.ModuleAutoShop.isShopClosed
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.ModuleAutoShop.maxItemWaitTime
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.ModuleAutoShop.simulateNextPurchases
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.isArmorItem
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ShopElement
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.stacks
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.screen.slot.SlotActionType

/**
 * This mode makes the module buy all the items within the same item category.
 * Unlike [NormalPurchaseMode], it doesn't check anything after performing each click.
 * It also makes the module wait for [delay] milliseconds after each click.
 *
 * However, if [waitForItems] is true, it will check if the items have been received
 * but only after all the clicks within a category are performed.
 *
 * In case a purchase or a few of them are unsuccessful, the module will:
 * - either wait endlessly if [waitForItems] is true
 * - or try to buy the remaining items that should have been bought in the same category
 *
 * [QuickPurchaseMode] is faster that [NormalPurchaseMode] but it's less safe
 * meaning it has a lower chance of buying items after performing a click.
 * Servers usually don't allow players to click too fast and cancel the clicks if they do.
 *
 * This mode is recommended for servers with older BedWars implementations.
 *
 * Example: Let's say the player has 43 iron ingots and has the following config: {wool, wool, sword, end stone}.
 *
 * Here is how the shopping can take place:
 * - The player opens the shop and waits [ModuleAutoShop.startDelay] ticks;
 *
 * - The module opens the "Blocks" category and waits [ModuleAutoShop.categorySwitchDelay] ticks;
 * - The module makes a click to buy some wool blocks for 4 iron ingots;
 *      - The module waits [delay] milliseconds;
 *
 * - Again, the module makes a click to buy some wool blocks for 4 iron ingots;
 *      - The module waits [delay] ms;
 *
 * - Even though, the sword is the next in the config,
 * the module can see the player has enough resources to buy both
 * the sword and the end stone blocks.
 * So, why not buy the end stone blocks together with the wool bocks? :)
 * - The module makes a click to buy some end stone blocks for 24 iron ingots;
 *      - The module waits [delay] ms;
 *
 * - If [waitForItems] is true, the module waits until all the blocks have been received;
 *
 * - The module opens the "Weapons" category and waits [ModuleAutoShop.categorySwitchDelay] ticks;
 * - The module makes a click to buy a sword for 10 iron ingots;
 *      - The module waits [delay] ticks;
 *
 * - Again, if [waitForItems] is true, the module waits until all the weapons have been received;
 *
 * - The player runs out of resources and the module closes the shop window.
 */
object QuickPurchaseMode : Choice("Quick") {
    override val parent: ChoiceConfigurable<*>
        get() = ModuleAutoShop.purchaseMode

    val delay by intRange("Delay", 50..70, 0..150, "ms")
    private val waitForItems by boolean("WaitForItems", true)

    /**
     * Buys multiple items within the same item category.
     * Waits between clicks based on [delay].
     * After all clicks have been made,
     * it waits [maxItemWaitTime] to receive all purchased items
     * unless the shop gets closed.
     */
    suspend fun Sequence<*>.buyAllItemsInCategory(remainingElements: List<ShopElement>) {
        val simulationResult = simulateNextPurchases(remainingElements, onlySameCategory = true)
        val slotsToClick = simulationResult.first
        val prevInventory = AutoShopInventoryManager.items

        val screen = mc.currentScreen as GenericContainerScreen
        val prevShopStacks = screen.stacks()

        for(slot in slotsToClick) {
            if (slot == -1) {
                break    // it means there will be no next category
            }

            delay(delay.random().toLong())
            // TODO: the shop might get closed while it's waiting

            interaction.clickSlot(screen.screenHandler.syncId, slot, 0, SlotActionType.PICKUP, player)

            if (ModuleDebug.running) {
                ModuleAutoShop.recordedClicks.add(slot)
            }
        }

        val nextCategorySlot = slotsToClick.last()
        if (nextCategorySlot != -1) {
            ModuleAutoShop.prevCategorySlot = nextCategorySlot
        }

        // expects to get items later
        val newPendingItems = if (waitForItems) {
            simulationResult.second.filter { it.key.isArmorItem() }
        } else { simulationResult.second }

        AutoShopInventoryManager.addPendingItems(newPendingItems)

        // waits for an inventory update and for an item category update
        val waitedTooMuch = waitConditional(maxItemWaitTime) { isShopClosed() ||
            (hasReceivedItems(prevInventory, simulationResult.second)
            && (nextCategorySlot == -1 || hasItemCategoryChanged(prevShopStacks))) }

        if (waitedTooMuch) {
            val currentElement = remainingElements.first()
            val category = currentElement.categorySlot
            val item = currentElement.item.id

            ModuleAutoShop.onFailedClick("Buying multiple items in category $category from $item")
        } else {
            // waits extra ticks
            waitConditional(categorySwitchDelay.random()) { isShopClosed() }
        }
    }
}
