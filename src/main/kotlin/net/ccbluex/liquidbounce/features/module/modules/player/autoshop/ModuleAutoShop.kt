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

import kotlinx.coroutines.delay
import net.ccbluex.liquidbounce.config.AutoShopConfig.loadAutoShopConfig
import net.ccbluex.liquidbounce.config.ShopConfigPreset
import net.ccbluex.liquidbounce.event.Sequence
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.purchasemode.NormalPurchaseMode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.purchasemode.QuickPurchaseMode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ItemInfo
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ShopConfig
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ShopElement
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.ConditionCalculator
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.utils.client.chat
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
    private val extraCategorySwitchDelay by intRange("ExtraCategorySwitchDelay", 3..4,
        0..10, "ticks")

    /**
     * Specifies whether the shop window should be closed
     * after all available purchases have been made.
     */
    private val autoClose by boolean("AutoClose", true)

    /**
     * Tracks the items the player currently has or is expected to receive later.
     */
    private val inventoryManager = AutoShopInventoryManager()
    private var waitedBeforeTheFirstClick = false
    private var canAutoClose = false    // allows closing the shop menu only after a purchase
    private var prevCategorySlot = -1
    var currentConfig = ShopConfig.emptyConfig()

    // Debug
    private val recordedClicks = mutableListOf<Int>()
    private var startMilliseconds = 0L

    init {
        // Updates [currentConfig] on module initialization
        loadAutoShopConfig(shopConfig)
    }

    @Suppress("unused")
    private val repeatable = tickHandler {
        if (!isShopOpen()) {
            return@tickHandler
        }

        if (ModuleDebug.running) {
            startMilliseconds = System.currentTimeMillis()
        }

        // waits after opening a shop (before the first click)
        if (!waitedBeforeTheFirstClick) {
            waitConditional(startDelay.random()) { !isShopOpen() }
            waitedBeforeTheFirstClick = true
        }

        // the shop might get closed while the module is waiting
        if (!isShopOpen()) {
            reset()
            return@tickHandler
        }

        for (index in currentConfig.elements.indices) {
            val element = currentConfig.elements[index]
            val remainingElements = currentConfig.elements.subList(index)
            var needToBuy = checkElement(element, remainingElements)

            // buys an item
            while (needToBuy) {
                canAutoClose = true
                doClicks(currentConfig.elements.subList(index))

                // the shop might get closed while the module is waiting
                if (!isShopOpen()) {
                    reset()
                    return@tickHandler
                }
                needToBuy = checkElement(element, remainingElements)
            }
        }

        // closes the shop after buying items
        if (waitedBeforeTheFirstClick && autoClose && canAutoClose) {
            player.closeHandledScreen()
        }
        reset()
    }

    /**
     * Based on the purchase more,
     * performs one or multiple clicks to buy items
     * within the same item category.
     */
    private suspend fun Sequence<*>.doClicks(remainingElements: List<ShopElement>) {
        val currentElement = remainingElements.first() // the item to be bought
        val categorySlot = currentElement.categorySlot
        val itemSlot = currentElement.itemSlot

        // switches the item category to buy the item
        switchCategory(categorySlot)

        // the shop might get closed while the module is waiting
        if (!isShopOpen()) {
            return
        }

        when (purchaseMode.activeChoice) {
            // buys the item (1 click only)
            NormalPurchaseMode -> buyItem(itemSlot, currentElement)

            // buys all items in the category and switches to the next category
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

        val prevShopStacks = (mc.currentScreen as GenericContainerScreen).stacks()
        interaction.clickSlot(
            (mc.currentScreen as GenericContainerScreen).screenHandler.syncId,
            nextCategorySlot,
            0,
            SlotActionType.PICKUP,
            mc.player
        )

        if (ModuleDebug.running) {
            recordedClicks.add(nextCategorySlot)
        }

        prevCategorySlot = nextCategorySlot
        waitUntil { !isShopOpen() || hasItemCategoryChanged(prevShopStacks) }
        waitConditional(extraCategorySwitchDelay.random()) { !isShopOpen() }
    }

    /**
     * Buys the item by clicking on specific [itemSlot].
     * Waits until the item is received unless the shop gets closed.
     */
    private suspend fun Sequence<*>.buyItem(itemSlot: Int, shopElement: ShopElement) {
        val currentInventory = inventoryManager.items

        interaction.clickSlot(
            (mc.currentScreen as GenericContainerScreen).screenHandler.syncId,
            itemSlot,
            0,
            SlotActionType.PICKUP,
            mc.player
        )

        if (ModuleDebug.running) {
            recordedClicks.add(itemSlot)
        }

        // waits to receive items from a server after clicking before performing the next click
        waitUntil { !isShopOpen() || hasReceivedItems(
                prevInventory = currentInventory,
                expectedItems = mapOf(
                    shopElement.item.id to shopElement.amountPerClick,
                    shopElement.price.id to -shopElement.price.minAmount))
        }

        // expects to get an item later
        if (shopElement.item.id.isArmorItem()) {
            inventoryManager.addPendingItems(mapOf(
                shopElement.item.id to shopElement.amountPerClick
            ))
        }

        // waits extra ticks
        waitConditional(NormalPurchaseMode.extraDelay.random()) { !isShopOpen() }
    }

    /**
     * Buys multiple items within the same item category.
     * Waits between clicks based on [QuickPurchaseMode.delay].
     * After all clicks have been made,
     * it waits until all purchased items are received
     * unless the shop gets closed.
     */
    private suspend fun Sequence<*>.buyAllItemsInCategory(remainingElements: List<ShopElement>) {
        val simulationResult = simulateNextPurchases(remainingElements, onlySameCategory = true)
        val slotsToClick = simulationResult.first
        val prevInventory = inventoryManager.items
        val prevShopStacks = (mc.currentScreen as GenericContainerScreen).stacks()

        for(slot in slotsToClick) {
            if (slot == -1) {
                continue    // it looks as if it doesn't require to switch an item category anymore
            }

            delay(QuickPurchaseMode.delay.random().toLong())

            interaction.clickSlot(
                (mc.currentScreen as GenericContainerScreen).screenHandler.syncId,
                slot,
                0,
                SlotActionType.PICKUP,
                mc.player
            )

            if (ModuleDebug.running) {
                recordedClicks.add(slot)
            }
        }

        val nextCategorySlot = slotsToClick.last()
        if (nextCategorySlot != -1) {
            prevCategorySlot = nextCategorySlot
        }

        // expects to get items later
        val newPendingItems = if (QuickPurchaseMode.waitForItems) {
            simulationResult.second.filter { it.key.isArmorItem() }
        } else { simulationResult.second }
        inventoryManager.addPendingItems(newPendingItems)

        // waits for an inventory update and for an item category update
        waitUntil { !isShopOpen() || (hasReceivedItems(prevInventory, simulationResult.second)
            && (nextCategorySlot == -1 || hasItemCategoryChanged(prevShopStacks))) }

        // waits extra ticks
        waitConditional(extraCategorySwitchDelay.random()) { !isShopOpen() }
    }

    /**
     * Checks if the current item category has been changed
     * based on the contents (items) of the shop window.
     */
    private fun hasItemCategoryChanged(prevShopStacks: List<String>): Boolean {
        val currentShopStacks = (mc.currentScreen as GenericContainerScreen).stacks()

        val difference = currentShopStacks
            .filter { !prevShopStacks.contains(it) }
            .union(prevShopStacks.filter { !currentShopStacks.contains(it) })

        return difference.size > 1
    }

    /**
     * Checks if the player has received [expectedItems].
     *
     * If [expectedItems] contain only armor which can be received only after the shop is closed,
     * it will check whether the items required to buy it are taken.
     **/
    private fun hasReceivedItems(prevInventory: Map<String, Int>,
                                 expectedItems: Map<String, Int>): Boolean {
        val exceptedItemsToGet = expectedItems.filter { it.value > 0 }
        val exceptedItemsToLose = expectedItems.filter { it.value < 0 }
        val isArmorOnly = exceptedItemsToGet.all { it.key.isArmorItem() }

        val currentInventory = inventoryManager.items
        val receivedNewItems = exceptedItemsToGet.all { (item, expectedNewAmount) ->
            val prevItemAmount = prevInventory[item] ?: 0
            val newItemAmount = currentInventory[item] ?: 0

            newItemAmount - prevItemAmount >= expectedNewAmount
        }

        val lostPriceItems = isArmorOnly && exceptedItemsToLose.all { (item, expectedNewAmount) ->
            val prevItemAmount = prevInventory[item] ?: 0
            val newItemAmount = currentInventory[item] ?: 0

            newItemAmount - prevItemAmount <= expectedNewAmount
        }

        return receivedNewItems || lostPriceItems
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
    private fun simulateNextPurchases(
        remainingElements: List<ShopElement>,
        onlySameCategory: Boolean) : Pair<List<Int>, Map<String, Int>> {

        if (remainingElements.isEmpty()) {
            return Pair(emptyList(), emptyMap())
        }

        val initialCategorySlot = remainingElements.first().categorySlot
        var currentCategorySlot = initialCategorySlot
        val currentItems = inventoryManager.items.toMutableMap()
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
        items: Map<String, Int> = inventoryManager.items) : Boolean {

        // checks if the player already has the required item to be bought
        if ((items[shopElement.item.id] ?: 0) >= shopElement.item.minAmount) {
            return false
        }

        // checks the item's price
        if (!checkPrice(shopElement.price, items)) {
            return false
        }

        // checks if the player is capable of buying a better item so that this item is not actually needed
        if (shopElement.item.id.isItemWithTiers() && remainingElements != null) {
            val simulationResult = simulateNextPurchases(remainingElements, onlySameCategory = false)
            val canBuyBetterItem = hasBetterTierItem(shopElement.item.id, simulationResult.second)
            if (canBuyBetterItem) {
                return false
            }
        }

        // makes sure that other conditions are met
        if (!ConditionCalculator.items(items).process(
                shopElement.item.id, shopElement.purchaseConditions)) {
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
        val currentItemAmount = min(items[shopElement.item.id] ?: 0, shopElement.item.minAmount)
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

    /**
     * Returns `true` if the target shop window is open,
     * based on the current configuration.
     */
    private fun isShopOpen(): Boolean {
        val screen = mc.currentScreen as? GenericContainerScreen ?: return false

        val title = screen.title.string.stripMinecraftColorCodes()
        val isTitleValid = currentConfig.traderTitles.any {
            title.contains(it, ignoreCase = true)
        }

        return isTitleValid
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

        inventoryManager.clearPendingItems()
        prevCategorySlot = currentConfig.initialCategorySlot
        waitedBeforeTheFirstClick = false
        canAutoClose = false
    }
}
