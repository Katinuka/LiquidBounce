package net.ccbluex.liquidbounce.utils.client.autoshop.serializable

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.AutoShopConfig
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ItemInfo
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ShopElement
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.AllNode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.AnyNode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.ItemNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

object ShopElementDeserializeTest {
    private const val PATH = "/autoshop/shop_elements.json"

    private lateinit var shopElements: JsonObject

    @JvmStatic
    @BeforeAll
    fun init() {
        javaClass.getResourceAsStream(PATH).use { inputStream ->
            check(inputStream != null) { "Failed to load resource: $PATH" }

            shopElements = JsonParser.parseReader(inputStream.reader()).asJsonObject
        }
    }

    private fun parse(member: String): ShopElement {
        return AutoShopConfig.autoShopGson.fromJson(
            shopElements[member],
            ShopElement::class.java)
    }

    @Test
    fun shopElementWithoutConditionsTest() {
        assertEquals(
            ShopElement(
                item = ItemInfo(id="apple", minAmount = 1),
                amountPerClick = 1,
                categorySlot = 0,
                itemSlot = 19,
                price = ItemInfo(id="brick", minAmount = 1),
                purchaseConditions = null
            ),
            parse("minimum_fields")
        )

        assertEquals(
            ShopElement(
                item = ItemInfo(id="fireball"),
                categorySlot = 6,
                itemSlot = 21,
                price = ItemInfo(id="gold_ingot")
            ),
            parse("minimum_fields2")
        )

        assertEquals(
            ShopElement(
                item = ItemInfo(id = "wool", minAmount = 128),
                amountPerClick = 16,
                categorySlot = 1,
                itemSlot = 18,
                price = ItemInfo(id = "iron_ingot", minAmount = 4),
                purchaseConditions = null
            ),
            parse("without_conditions")
        )

        assertEquals(
            ShopElement(
                item = ItemInfo(id = "arrow"),
                categorySlot = 4,
                itemSlot = 20,
                price = ItemInfo(id = "gold_ingot")
            ),
            parse("without_conditions2")
        )
    }

    @Test
    fun shopElementWithConditionsTest() {
        assertEquals(
            ShopElement(
                item = ItemInfo(id = "stone_sword"),
                categorySlot = 2, itemSlot = 19,
                price = ItemInfo(id = "iron_ingot", minAmount = 10),
                purchaseConditions = ItemNode(id = "wool", min = 1, max = 100)
            ), parse("with_condition")
        )

        assertEquals(
            ShopElement(
                item = ItemInfo(id = "iron_chestplate", minAmount = 2),
                categorySlot = 0, itemSlot = 11,
                price = ItemInfo(id = "gold_ingot", minAmount = 7),
                purchaseConditions = ItemNode(id = "iron_sword", min = 1, max = Int.MAX_VALUE)
            ), parse("with_condition2")
        )

        assertEquals(
            ShopElement(
                item = ItemInfo(id = "sword:tier::2"),
                categorySlot = 0, itemSlot = 29,
                price = ItemInfo(id = "gold_ingot", minAmount = 7),
                purchaseConditions = AnyNode(listOf(
                    ItemNode(id = "armor:tier::2", min = 1, max = Int.MAX_VALUE),
                    ItemNode(id = "armor:tier::3")
                ))
            ), parse("with_multiple_conditions")
        )

        assertEquals(
            ShopElement(
                item = ItemInfo(id = "golden_apple", minAmount = 12),
                categorySlot = 6, itemSlot = 18,
                price = ItemInfo(id = "gold_ingot", minAmount = 3),
                purchaseConditions = AllNode(listOf(
                    ItemNode(id = "golden_apple", min = 0, max = 5),
                    ItemNode(id = "boots:tier::3"),
                    ItemNode(id = "sword:tier::2")
                ))
            ), parse("with_conditions_and_comments")
        )

        assertEquals(
            ShopElement(
                item = ItemInfo(id = "sword:tier::2"),
                categorySlot = 1, itemSlot = 19,
                price = ItemInfo(id = "gold_ingot", minAmount = 7),
                purchaseConditions = AnyNode(listOf(
                    ItemNode(id = "gold_ingot", min = 16),
                    AllNode(listOf(
                        AnyNode(listOf(
                            ItemNode(id = "gold_ingot", min = 10),
                            ItemNode(id = "golden_apple")
                        )),
                        ItemNode(id = "leggings:tier::3"),
                        ItemNode(id = "chestplate:tier::3")
                    ))
                ))
            ), parse("with_nested_conditions")
        )
    }
}
