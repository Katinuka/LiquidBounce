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
 * This mode makes the module buy items click by click
 * while checking if a purchase has been successful after every click.
 * It also makes the module wait for [extraDelay] ticks after each successful purchase.
 *
 * If a purchase is unsuccessful and the player hasn't received items,
 * nor the server has taken the resources, which serve as the price of the items, from the player,
 * the module will just wait. (until the shop window gets closed)
 *
 * TODO: make it try to buy items again instead of letting it wait endlessly
 *
 * [NormalPurchaseMode] is slower that [QuickPurchaseMode] but it's safer
 * meaning it has a higher chance of buying items after performing a click.
 * Servers usually don't allow players to click too fast and cancel the clicks if they do.
 *
 * This mode is recommended for most servers.
 *
 * Example: Let's say the player has 43 iron ingots and has the following config: {wool, wool, sword, end stone}.
 *
 * Here is how the shopping can take place:
 * - The player opens the shop and waits [ModuleAutoShop.startDelay] ticks;
 *
 * - The module opens the "Blocks" category and waits [ModuleAutoShop.extraCategorySwitchDelay] ticks;
 * - The module makes a click to buy some wool blocks for 4 iron ingots;
 *      - The module waits until the player gets the wool blocks;
 *      - The module waits [extraDelay] ticks;
 *
 * - Again, the module makes a click to buy some wool blocks for 4 iron ingots;
 *      - The module waits until the player gets the wool blocks;
 *      - The module waits [extraDelay] ticks;
 *
 * - The module opens the "Weapons" category and waits [ModuleAutoShop.extraCategorySwitchDelay] ticks;
 * - The module makes a click to buy a sword for 10 iron ingots;
 *      - The module waits until the player gets the sword;
 *      - The module waits [extraDelay] ticks;
 *
 * - One more time, the module opens the "Blocks" category and waits [ModuleAutoShop.extraCategorySwitchDelay] ticks;
 * - The module makes a click to buy some end stone blocks for 24 iron ingots;
 *      - The module waits until the player gets the end stone blocks;
 *      - The module waits [extraDelay] ticks;
 *
 * - The player runs out of resources and the module closes the shop window.
 */
object NormalPurchaseMode : Choice("Normal") {
    override val parent: ChoiceConfigurable<*>
        get() = ModuleAutoShop.purchaseMode

    val extraDelay by intRange("ExtraDelay", 2..3, 0..10, "ticks")
}
