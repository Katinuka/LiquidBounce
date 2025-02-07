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

import net.ccbluex.liquidbounce.config.types.Choice
import net.ccbluex.liquidbounce.config.types.ChoiceConfigurable
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.ModuleAutoShop

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
 * TODO: make it try to buy items again instead of letting it wait endlessly
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
 * - The module opens the "Blocks" category and waits [ModuleAutoShop.extraCategorySwitchDelay] ticks;
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
 * - The module opens the "Weapons" category and waits [ModuleAutoShop.extraCategorySwitchDelay] ticks;
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
    val waitForItems by boolean("WaitForItems", true)
}
