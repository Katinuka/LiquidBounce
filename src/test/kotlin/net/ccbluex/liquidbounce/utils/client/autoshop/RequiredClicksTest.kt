package net.ccbluex.liquidbounce.utils.client.autoshop

import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.ModuleAutoShop.getRequiredClicks
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ItemInfo
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ShopElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

object RequiredClicksTest {
    @Test
    fun testGetRequiredClicksEnoughItems() {
        assertEquals(0, getRequiredClicks(
            currentAmount = 64,
            requiredAmount = 64,
            amountPerClick = 16,
            currentCurrencyItems = mapOf("iron_ingot" to 12),
            requiredCurrencyItems = mapOf("iron_ingot" to 4))
        )

        assertEquals(0, getRequiredClicks(
            currentAmount = 3,
            requiredAmount = 1,
            amountPerClick = 1,
            currentCurrencyItems = emptyMap(),
            requiredCurrencyItems = mapOf("iron_ingot" to 10))
        )
    }

    @Test
    fun testGetRequiredClicksEnoughResources() {
        assertEquals(2, getRequiredClicks(
            currentAmount = 27,
            requiredAmount = 32,
            amountPerClick = 4,
            currentCurrencyItems = mapOf("iron_ingot" to 10),
            requiredCurrencyItems = mapOf("iron_ingot" to 4))
        )

        assertEquals(3, getRequiredClicks(
            currentAmount = 18,
            requiredAmount = 36,
            amountPerClick = 6,
            currentCurrencyItems = mapOf("iron_ingot" to 9),
            requiredCurrencyItems = mapOf("iron_ingot" to 3))
        )

        // Test with multiple currency items, all sufficient
        assertEquals(4, getRequiredClicks(
            currentAmount = 0,
            requiredAmount = 64,
            amountPerClick = 16,
            currentCurrencyItems = mapOf(
                "iron_ingot" to 20,
                "gold_ingot" to 16,
                "diamond" to 8
            ),
            requiredCurrencyItems = mapOf(
                "iron_ingot" to 5,
                "gold_ingot" to 4,
                "diamond" to 2
            ))
        )
    }

    @Test
    fun testGetRequiredClicksNotEnoughResources() {
        // Single currency item with insufficient resources
        assertEquals(2, getRequiredClicks(
            currentAmount = 0,
            requiredAmount = 64,
            amountPerClick = 16,
            currentCurrencyItems = mapOf("iron_ingot" to 8),
            requiredCurrencyItems = mapOf("iron_ingot" to 4))
        )

        // Multiple currency items where one limits the clicks
        assertEquals(1, getRequiredClicks(
            currentAmount = 0,
            requiredAmount = 32,
            amountPerClick = 8,
            currentCurrencyItems = mapOf(
                "iron_ingot" to 20,
                "gold_ingot" to 3
            ),
            requiredCurrencyItems = mapOf(
                "iron_ingot" to 4,
                "gold_ingot" to 3
            ))
        )

        // Empty current currency map
        assertEquals(0, getRequiredClicks(
            currentAmount = 0,
            requiredAmount = 64,
            amountPerClick = 16,
            currentCurrencyItems = emptyMap(),
            requiredCurrencyItems = mapOf("iron_ingot" to 4))
        )

        // Zero current currency amount
        assertEquals(0, getRequiredClicks(
            currentAmount = 0,
            requiredAmount = 64,
            amountPerClick = 16,
            currentCurrencyItems = mapOf("iron_ingot" to 0),
            requiredCurrencyItems = mapOf("iron_ingot" to 4))
        )
    }

    @Test
    fun testEdgeCases() {
        // Test with very large numbers
        assertEquals(1_000_000_000, getRequiredClicks(
            currentAmount = 0,
            requiredAmount = 1_000_000_000,
            amountPerClick = 1,
            currentCurrencyItems = mapOf(
                "iron_ingot" to 2_000_000_000,
                "gold_ingot" to 1_000_000_000,
            ),
            requiredCurrencyItems = mapOf(
                "iron_ingot" to 2,
                "gold_ingot" to 1,
            ))
        )

        // Test with empty required currency map
        assertEquals(5, getRequiredClicks(
            currentAmount = 0,
            requiredAmount = 10,
            amountPerClick = 2,
            currentCurrencyItems = mapOf(
                "iron_ingot" to 4,
                "gold_ingot" to 4
            ),
            requiredCurrencyItems = emptyMap())
        )

        // Test with bad currency amount
        assertThrows<IllegalArgumentException> {
            getRequiredClicks(
                currentAmount = 0,
                requiredAmount = 10,
                amountPerClick = 2,
                currentCurrencyItems = mapOf(
                    "iron_ingot" to -10,
                    "gold_ingot" to 5
                ),
                requiredCurrencyItems = mapOf(
                    "iron_ingot" to 2,
                    "gold_ingot" to 0
                ))
        }

        // Test with amountPerClick = 0
        assertThrows<IllegalArgumentException> {
            getRequiredClicks(
                currentAmount = 0,
                requiredAmount = 10,
                amountPerClick = 0,
                currentCurrencyItems = emptyMap(),
                requiredCurrencyItems = emptyMap()
            )
        }

        // Test with non-currency items
        assertThrows<IllegalArgumentException> {
            getRequiredClicks(
                currentAmount = 1,
                requiredAmount = 2,
                amountPerClick = 1,
                currentCurrencyItems = mapOf("stick" to 2),
                requiredCurrencyItems = mapOf("apple" to 1)
            )
        }
    }

    @Test
    fun testGetRequiredClicksWithInventory() {
        assertEquals(2, getRequiredClicks(
            element = ShopElement(
                item = ItemInfo(id = "wool", minAmount = 64),
                amountPerClick = 16,
                categorySlot = 0,
                itemSlot = 21,
                price = ItemInfo(id = "iron_ingot", minAmount = 4)
            ),
            items = mapOf(
                "stone_sword" to 1,
                "wool" to 15,
                "iron_ingot" to 9
            ),
        ))

        // TODO: add more test here with different inventories and elements!
    }
}
