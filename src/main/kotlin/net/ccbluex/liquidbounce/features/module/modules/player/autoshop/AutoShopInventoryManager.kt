/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2025 CCBlueX
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

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.utils.*
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.item.getPotionEffects
import net.ccbluex.liquidbounce.utils.item.id
import net.ccbluex.liquidbounce.utils.item.isNothing
import net.ccbluex.liquidbounce.utils.kotlin.incrementOrSet
import net.ccbluex.liquidbounce.utils.kotlin.sumValues
import net.minecraft.item.ItemStack
import net.minecraft.item.PotionItem
import net.minecraft.item.SplashPotionItem
import net.minecraft.registry.Registries

/**
 * Inventory manager that tracks the items in the player's inventory.
 * Groups items by their indices and stores the amount of the former.
 * Contains the data about the items from the current and previous ticks.
 * If the player buys armor, which is known to be received later, after the shop gets closed,
 * the armor will be stored in [pendingItems] until the player receives it.
 */
object AutoShopInventoryManager : EventListener {

    private val prevInventoryItems = mutableMapOf<String, Int>()
    private val currentInventoryItems = mutableMapOf<String, Int>()

    /**
     * Some BedWars implementations don't give players armor straight after a purchase.
     * The players receive it after a shop gets closed.
     * Until then, the purchased armor is marked as "pending".
     */
    private val pendingItems = mutableMapOf<String, Int>()

    /**
     * The items the player currently has plus the pending items.
     */
    val items : Map<String, Int>
        get() {
            synchronized(currentInventoryItems) {
                synchronized(pendingItems) {
                    return currentInventoryItems.toMutableMap().sumValues(pendingItems)
                }
            }
        }

    /**
     * Updates the items from the player's inventory every tick
     */
    @Suppress("unused")
    private val onTick = handler<GameTickEvent> {
        if (!ModuleAutoShop.running) {
            return@handler // doesn't track the inventory without the module.
        }

        // includes armor and the offhand slot
        val inventoryItems = player.inventory.main.toMutableList().apply {
            addAll(player.inventory.armor)
            addAll(player.inventory.offHand)
        }

        val newItems = mutableMapOf<String, Int>()
        inventoryItems.filter { !it.isNothing() }.forEach { stack ->
            // adds the current item
            newItems.incrementOrSet(stack.item.id, stack.count)

            // collects all kinds of colorful blocks together
            // so that there is no dependency on color
            newItems.incrementOrSet(colorfulBlockOf(stack))

            // groups potions by their effects
            newItems.sumValues(potionsOf(stack))

            // groups items by enchantments
            newItems.sumValues(enchantmentsOf(stack))
        }

        // tracks items with tiers
        newItems.sumValues(tiersOf(newItems))

        // tracks the experience level of the player
        newItems[EXPERIENCE_ID] = player.experienceLevel

        if (ModuleDebug.running) {
            // todo: remove me!!
            logger.info(newItems)
        }

        this.update(newItems)
    }

    /**
     * If [stack] represents a colorful block,
     * it returns a general block ID paired with the [stack] count.
     *
     * Example:
     * - 64 blocks of red_wool will result in: "wool" to 64;
     * - 16 blocks of blue_terracotta will result in: "terracotta" to 16.
     */
    @Suppress("ReturnCount")
    private fun colorfulBlockOf(stack: ItemStack) : Pair<String, Int> {
        when {
            stack.item.isWool() ->          return WOOL_ID to stack.count
            stack.item.isTerracotta() ->    return TERRACOTTA_ID to stack.count
            stack.item.isStainedGlass() ->  return STAINED_GLASS_ID to stack.count
            stack.item.isConcrete() ->      return CONCRETE_ID to stack.count
        }
        return stack.item.id to 0
    }

    /**
     * If [stack] represents a potion,
     * it returns a map of potion effect indices paired with the [stack] count.
     *
     * Example: If [stack] contains a potion item with:
     * - Strength II;
     * - Speed I.
     *
     * the function will return something like this:
     * mapOf("potion:strength::2" to 1, "potion:speed::1" to 1)
     */
    private fun potionsOf(stack: ItemStack) : Map<String, Int> {
        if (stack.item !is PotionItem) {
            return emptyMap()
        }

        return stack.getPotionEffects()
            .map { effect ->
                val effectId = Registries.STATUS_EFFECT.getId(effect.effectType.value())?.path
                "$effectId$LEVEL_PREFIX${effect.amplifier + 1}" // Example: "speed::1"
            }.associate { potionID ->
                // Examples: "splash_potion:speed::1", "potion:strength::2"
                when(stack.item) {
                    is SplashPotionItem ->  "$SPLASH_POTION_PREFIX$potionID" to stack.count
                    else ->                 "$POTION_PREFIX$potionID" to stack.count
                }
            }
    }

    /**
     * If [stack] represents an enchanted item,
     * it returns a map of enchantment indices paired with the [stack] count.
     *
     * Example: If [stack] contains a stone sword item with:
     * - Sharpness II;
     * - Unbreaking I.
     *
     * the function will return something like this:
     * mapOf("stone_sword:sharpness::2" to 1, "stone_sword:unbreaking::1" to 1)
     */
    private fun enchantmentsOf(stack: ItemStack) : Map<String, Int> {
        return stack.enchantments.enchantmentEntries.mapNotNull {
            "${it.key.idAsString.removePrefix("minecraft:")}$LEVEL_PREFIX${it.intValue}" // Example: "sharpness::2"
        }.associate { "${stack.item.id}:$it" to stack.count } // Example: "iron_sword:sharpness:2"
    }

    /**
     * Returns a map representing items with tiers and their amount in [inventoryItems].
     *
     * Example: If the tier dictionary is:
     * - "sword": ("wooden_sword", "stone_sword");
     * - "bow": ("bow:power1", "bow:power::3").
     *
     * and [inventoryItems] contains:
     * - 2 items of "wooden_sword";
     * - 1 item of "bow:power::3".
     *
     * the function will return something like this:
     * mapOf("sword:tier::1" to 2, "bow:tier::2" to 1)
     */
    private fun tiersOf(inventoryItems: Map<String, Int>): Map<String, Int> {
        // TODO: I don't like this accessing ModuleAutoShop.currentConfig.itemsWithTiers
        //  it would be better if it was written better(somehow)
        val tierDictionary = ModuleAutoShop.currentConfig.tierDictionary ?: return emptyMap()

        return tierDictionary.flatMap { (tierName, items) ->
            items.mapIndexedNotNull { index, itemId ->
                val newID = "$tierName$TIER_ID$LEVEL_PREFIX${index + 1}"
                val amount = inventoryItems[itemId] ?: 0
                if (amount > 0) newID to amount else null
            }
        }.toMap()
    }

    /**
     * Updates the states of the inventory from the current and previous ticks.
     */
    private fun update(newItems: Map<String, Int>) {
        synchronized(currentInventoryItems) {
            prevInventoryItems.clear()
            prevInventoryItems.putAll(currentInventoryItems)

            currentInventoryItems.clear()
            currentInventoryItems.putAll(newItems)

            // updates pending items on the inventory update
            updatePendingItems()
        }
    }

    /**
     * Updates the state of the pending items.
     * If the player buys an item in a certain quantity, the item is marked as pending
     * until an inventory update comes with the modified quantity of this item.
     *
     * If the inventory update doesn't bring or take enough items,
     * the item remains marked as pending until the player gets or spends enough items.
     */
    private fun updatePendingItems() {
        val itemsToRemove = mutableSetOf<String>()
        val itemsToUpdate = mutableMapOf<String, Int>()

        synchronized(pendingItems) {
            pendingItems.forEach { (item, _) ->
                val newAmount = currentInventoryItems[item] ?: 0
                val prevAmount = prevInventoryItems[item] ?: 0
                val currentPendingAmount = pendingItems[item] ?: 0

                val difference = newAmount - prevAmount

                // prevents the pending items amount increase
                // if the player loses those items somehow and vise versa
                val receivedPositiveItems = currentPendingAmount > 0 && difference > 0
                val spentNegativeItems = currentPendingAmount < 0 && difference < 0

                val newPendingAmount = currentPendingAmount - difference
                if (receivedPositiveItems) {
                    when {
                        newPendingAmount <= 0 -> itemsToRemove.add(item) // the player has received enough items
                        else -> itemsToUpdate[item] = newPendingAmount
                    }
                } else if (spentNegativeItems) {
                    when {
                        newPendingAmount >= 0 -> itemsToRemove.add(item) // the player has spent enough items
                        else -> itemsToUpdate[item] = newPendingAmount
                    }
                }
            }

            itemsToRemove.forEach { item ->
                pendingItems.remove(item)
            }
            itemsToUpdate.forEach { (item, newPendingAmount) ->
                pendingItems[item] = newPendingAmount
            }
        }
    }

    /**
     * Checks if the player has received [expectedItems].
     *
     * If [expectedItems] contain only armor which can be received only after the shop is closed,
     * it will check whether the items required to buy it are taken.
     **/
    fun hasReceivedItems(prevInventory: Map<String, Int>,
                         expectedItems: Map<String, Int>): Boolean {
        val exceptedItemsToGet = expectedItems.filter { it.value > 0 }
        val exceptedItemsToLose = expectedItems.filter { it.value < 0 }
        val isArmorOnly = exceptedItemsToGet.all { it.key.isArmorItem() }

        val currentInventory = items
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

    fun addPendingItems(items: Map<String, Int>) {
        synchronized(pendingItems) {
            pendingItems.sumValues(items)
        }
    }

    fun clearPendingItems() {
        synchronized(pendingItems) {
            pendingItems.clear()
        }
    }

    override fun parent() = ModuleAutoShop
}
