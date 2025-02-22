/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2023 CCBlueX
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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop

import net.ccbluex.liquidbounce.config.AutoShopConfig.loadAutoShopConfig
import net.ccbluex.liquidbounce.config.ShopConfigPreset
import net.ccbluex.liquidbounce.event.Sequence
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.purchasemode.NormalPurchaseMode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.purchasemode.NormalPurchaseMode.buyItem
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.purchasemode.QuickPurchaseMode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.purchasemode.QuickPurchaseMode.buyAllItemsInCategory
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ItemInfo
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ShopConfig
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ShopElement
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.ConditionCalculator
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.client.stripMinecraftColorCodes
import net.ccbluex.liquidbounce.utils.kotlin.incrementOrSet
import net.ccbluex.liquidbounce.utils.kotlin.subList
import net.ccbluex.liquidbounce.utils.kotlin.sumValues
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.screen.slot.SlotActionType
import kotlin.math.ceil
import kotlin.math.min

/**
 * AutoShop module
 *
 * Automatically buys specific items in a BedWars shop.
 */
@Suppress("TooManyFunctions")
object ModuleAutoShop : ClientModule("AutoShop", Category.PLAYER) {

    /**
     * Configuration that defines the module's behavior,
     * including (but not limited to)
     * the items to be bought, the purchase order, and how to buy the items.
     */
    private var shopConfig by enumChoice("Config", ShopConfigPreset.PIKA_NETWORK).onChanged {
        loadAutoShopConfig(it)
    }

    /**
     * The delay between opening the shop window and the first click.
     */
    private val startDelay by intRange("StartDelay", 1..2, 0..10, "ticks")

    /**
     * Mode that defines how clicks should be performed.
     * This may affect the purchase order.
     */
    val purchaseMode = choices(this, "PurchaseMode", NormalPurchaseMode,
        arrayOf(NormalPurchaseMode, QuickPurchaseMode)
    )

    /**
     * The delay between changing an item category
     * and the first click within that category.
     */
    val categorySwitchDelay by intRange("categorySwitchDelay", 3..4,
        0..10, "ticks")

    /**
     * The max time the module waits to receive items after making clicks
     */
    val maxItemWaitTime by int("MaxItemWaitTime", 3, 2..10, "ticks")

    /**
     * Specifies whether the shop window should be closed
     * after all available purchases have been made.
     */
    private val autoClose by boolean("AutoClose", true)

    /**
     * Tracks the items the player currently has or is expected to receive later.
     */
    private var waitedBeforeTheFirstClick = false
    private var canAutoClose = false    // allows closing the shop menu only after a purchase
    var prevCategorySlot = -1
    var currentConfig = ShopConfig.emptyConfig()

    // Debug
    val recordedClicks = mutableListOf<Int>()
    private var startMilliseconds = 0L

    init {
        // Updates [currentConfig] on module initialization
        loadAutoShopConfig(shopConfig)
    }

    @Suppress("unused")
    private val repeatable = tickHandler {
        if (isShopClosed()) {
            return@tickHandler
        }

        if (ModuleDebug.running) {
            startMilliseconds = System.currentTimeMillis()
        }

        // waits after opening a shop (before the first click)
        if (!waitedBeforeTheFirstClick) {
            waitConditional(startDelay.random()) { isShopClosed() }

            // the shop might get closed while the module is waiting
            if (isShopClosed()) {
                reset().also { return@tickHandler }
            }
            waitedBeforeTheFirstClick = true
        }

        // buys each item in the config
        for (index in currentConfig.elements.indices) {
            val remainingElements = currentConfig.elements.subList(index)

            buyShopElement(remainingElements)

            // the shop might get closed while the module is waiting
            if (isShopClosed()) {
                reset().also { return@tickHandler }
            }
        }

        // closes the shop after buying items
        if (waitedBeforeTheFirstClick && autoClose && canAutoClose) {
            player.closeHandledScreen()
        }
        reset()
    }

    /**
     * Buys one shop element according to the config.
     * If extra clicks were made, it stops buying the element which means that either:
     * - The shop element is configured incorrectly;
     * - The server didn't register some clicks, and it's recommended to increase the click delay;
     */
    private suspend fun Sequence<*>.buyShopElement(remainingElements: List<ShopElement>) {
        val element = remainingElements.first()
        var needToBuy = checkElement(element, remainingElements)

        while (needToBuy) {
            canAutoClose = true
            doClicks(remainingElements)

            // the shop might get closed while the module is waiting
            if (isShopClosed()) {
                return
            }
            needToBuy = checkElement(element, remainingElements)
        }
    }

    /**
     * Based on the purchase more,
     * performs one or multiple clicks to buy items
     * within the same item category.
     */
    private suspend fun Sequence<*>.doClicks(remainingElements: List<ShopElement>) {
        val currentElement = remainingElements.first() // the item to be bought
        val categorySlot = currentElement.categorySlot

        // switches the item category to buy the item
        switchCategory(categorySlot)

        // the shop might get closed while the module is waiting
        if (isShopClosed()) {
            return
        }

        when (purchaseMode.activeChoice) {
            // buys items (1 click only)
            NormalPurchaseMode -> buyItem(currentElement)

            // buys all available items in the category and switches to the next category
            QuickPurchaseMode -> buyAllItemsInCategory(remainingElements)
        }
    }

    /**
     * Changes the current item category in the shop.
     * Waits until the category is changed unless the shop gets closed.
     */
    private suspend fun Sequence<*>.switchCategory(nextCategorySlot: Int) {
        // we don't need to open, for example, the "Blocks" category again if it's already open
        if (prevCategorySlot == nextCategorySlot) {
            return
        }

        val screen = mc.currentScreen as GenericContainerScreen
        val prevShopStacks = screen.stacks()

        interaction.clickSlot(screen.screenHandler.syncId, nextCategorySlot, 0, SlotActionType.PICKUP, player)

        if (ModuleDebug.running) {
            recordedClicks.add(nextCategorySlot)
        }

        val waitedTooMuch = waitConditional(maxItemWaitTime) {
            isShopClosed() || hasItemCategoryChanged(prevShopStacks)
        }

        if (waitedTooMuch) {
            onFailedClick(failedAt = "Opening category $nextCategorySlot")
        } else {
            prevCategorySlot = nextCategorySlot
            waitConditional(categorySwitchDelay.random()) { isShopClosed() }
        }
    }


    /**
     * Checks if the current item category has been changed
     * based on the contents (items) of the shop window.
     */
    fun hasItemCategoryChanged(prevShopStacks: List<String>): Boolean {
        val currentShopStacks = (mc.currentScreen as GenericContainerScreen).stacks()

        val difference = currentShopStacks
            .filter { !prevShopStacks.contains(it) }
            .union(prevShopStacks.filter { !currentShopStacks.contains(it) })

        return difference.size > 1
    }


    /**
     * Simulates the next purchases based on the player's available resources and the purchase order
     * specified in the configuration.
     *
     * Returns a pair:
     * - A list of clickable slots, with the last item indicating the next category slot
     *   (if no category switch is needed, it will be -1).
     * - A map of expected items and their amounts after the purchases.
     *
     * The function takes into account the player's current inventory
     * and checks what items in the given [remainingElements] can be bought,
     * based on their prices and purchase conditions.
     */
    fun simulateNextPurchases(
        remainingElements: List<ShopElement>,
        onlySameCategory: Boolean) : Pair<List<Int>, Map<String, Int>> {

        if (remainingElements.isEmpty()) {
            return Pair(emptyList(), emptyMap())
        }

        val initialCategorySlot = remainingElements.first().categorySlot
        var currentCategorySlot = initialCategorySlot
        val currentItems = AutoShopInventoryManager.items.toMutableMap()
        val slots = mutableListOf<Int>()
        val expectedItems = mutableMapOf<String, Int>()
        var nextCategorySlot = -1

        @Suppress("LoopWithTooManyJumpStatements")
        for (element in remainingElements) {
            if (!checkElement(element, items = currentItems)) {
                continue
            }

            val requiredItems = mapOf(element.price.id to element.price.minAmount)
            val clicks = getRequiredClicks(element, currentItems, requiredItems)
            if (clicks < 1) {
                continue    // we can't buy the item actually
            }

            // subtracts the required items from the limited items we have
            currentItems.sumValues(requiredItems.mapValues { -it.value * clicks })
            currentItems.incrementOrSet(element.item.id, element.amountPerClick * clicks)


            if (!onlySameCategory) {
                if (element.categorySlot != currentCategorySlot) {
                    slots.add(element.categorySlot)
                    currentCategorySlot = element.categorySlot
                }
                repeat(clicks) { slots.add(element.itemSlot) }
                expectedItems.incrementOrSet(element.item.id, element.amountPerClick * clicks)
                expectedItems.incrementOrSet(element.price.id, -element.price.minAmount * clicks)
                continue
            }

            if (element.categorySlot == initialCategorySlot) {
                repeat(clicks) { slots.add(element.itemSlot) }
                // for example, [wool: 64, iron_ingot: -16]
                expectedItems.incrementOrSet(element.item.id, element.amountPerClick * clicks)
                expectedItems.incrementOrSet(element.price.id, -element.price.minAmount * clicks)
                continue
            }

            // updates the next category slot if it's empty
            if (nextCategorySlot == -1) {
                nextCategorySlot = element.categorySlot
            }

        }

        slots.add(nextCategorySlot)
        return Pair(slots, expectedItems)
    }


    /**
     * Returns `true` if the item can be bought, based on the player's current resources,
     * the item's price, and whether the player can afford a better item.
     */
    private fun checkElement(
        shopElement: ShopElement,
        remainingElements: List<ShopElement>? = null,
        items: Map<String, Int> = AutoShopInventoryManager.items) : Boolean {

        // checks if the player already has the required item to be bought
        val betterItemAmount = betterItemsOf(shopElement.item.id, items).values.sum()
        val amount = (items[shopElement.item.id] ?: 0) + betterItemAmount

        // checks the item's presence and price
        if (amount >= shopElement.item.minAmount || !checkPrice(shopElement.price, items)) {
            return false
        }


        // checks if the player is capable of buying a better item
        // so that this item is not actually needed
        if (shopElement.item.id.isItemWithTiers() && remainingElements != null) {
            val simulationResultItems = simulateNextPurchases(remainingElements, onlySameCategory = false).second
            val betterItemAmount = betterItemsOf(shopElement.item.id, simulationResultItems).values.sum()
            val canBuyBetterItems = betterItemAmount > shopElement.item.minAmount

            if (canBuyBetterItems) {
                return false
            }
        }

        // makes sure that other conditions are met
        if (!ConditionCalculator.items(items).process(shopElement.purchaseConditions)) {
            return false
        }

        return true
    }

    /**
     * Returns the number of clicks that can be performed to buy an item
     * For example, it might need 4 clicks to buy wool blocks
     * but there might be enough resources only for 3 clicks.
     */
    private fun getRequiredClicks(
        shopElement: ShopElement,
        items: Map<String, Int>,
        requiredCurrencyItems: Map<String, Int>) : Int {

        val currentCurrencyItems = items.filterKeys { it in CURRENCY_ITEMS }
        val betterItemAmount = betterItemsOf(shopElement.item.id, items).values.sum()
        val currentItemAmount = min(
            betterItemAmount + (items[shopElement.item.id] ?: 0),
            shopElement.item.minAmount)
        val maxBuyClicks = ceil(
            1f * (shopElement.item.minAmount - currentItemAmount) / shopElement.amountPerClick).toInt()
        var minMultiplier = Int.MAX_VALUE

        for (key in requiredCurrencyItems.keys) {
            val requiredItemsAmount = requiredCurrencyItems[key] ?: 0
            val currentItemsAmount = currentCurrencyItems[key] ?: 0
            val newMultiplier = min(maxBuyClicks, currentItemsAmount / requiredItemsAmount)
            minMultiplier = min(minMultiplier, newMultiplier)
        }
        return minMultiplier
    }

    /**
     * Checks if there are enough [items] to meet the [price].
     */
    private fun checkPrice(price: ItemInfo, items: Map<String, Int>) : Boolean {
        val requiredItemAmount = items[price.id] ?: 0
        return requiredItemAmount >= price.minAmount
    }

    fun onFailedClick(failedAt: String) {
        val message = ModuleAutoShop.message("failedClick", failedAt)
        notification(ModuleAutoShop.name, message, NotificationEvent.Severity.INFO)
    }

    /**
     * Returns `true` if the target shop window is closed,
     * based on the current configuration.
     */
    fun isShopClosed(): Boolean {
        val screen = mc.currentScreen as? GenericContainerScreen ?: return true

        val title = screen.title.string.stripMinecraftColorCodes()
        val isTitleInvalid = currentConfig.traderTitles.none {
            title.contains(it, ignoreCase = true)
        }

        return isTitleInvalid
    }

    /**
     * Resets the state of some key parts of the module.
     */
    private fun reset() {
        if (ModuleDebug.running && startMilliseconds != 0L && canAutoClose) {
            chat("[AutoShop] Time elapsed: ${System.currentTimeMillis() - startMilliseconds} ms")
            chat("[AutoShop] Clicked on the following slots: $recordedClicks")
            recordedClicks.clear()
            startMilliseconds = 0L
        }

        AutoShopInventoryManager.clearPendingItems()
        prevCategorySlot = currentConfig.initialCategorySlot
        waitedBeforeTheFirstClick = false
        canAutoClose = false
    }
}
