package net.ccbluex.liquidbounce.utils.client.autoshop.serializable

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.AutoShopConfig
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ItemInfo
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ShopConfig
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ShopElement
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.AllNode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.AnyNode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.ItemNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

object ShopConfigDeserializeTest {
    private val CONFIGS = arrayOf(
        "/autoshop/configs/blocksmc.json",
        "/autoshop/configs/cubecraft.json",
        "/autoshop/configs/dexland.json",
        "/autoshop/configs/funnymc.json",
        "/autoshop/configs/pikanetwork.json",
        "/autoshop/configs/teamholy.json",
        "/autoshop/configs/test.json",
    )

    private lateinit var shopConfigs: Map<String, JsonObject>

    @JvmStatic
    @BeforeAll
    fun init() {
        shopConfigs = CONFIGS.associate { path ->
            path.substringAfterLast("/").substringBefore(".json") to
                javaClass.getResourceAsStream(path).use { inputStream ->
                    check(inputStream != null) { "Failed to load resource: $path" }

                    JsonParser.parseReader(inputStream.reader()).asJsonObject
                }
        }
    }

    private fun parse(member: String): ShopConfig {
        return AutoShopConfig.autoShopGson.fromJson(
            shopConfigs[member],
            ShopConfig::class.java)
    }

    @Test
    fun blocksMCShopConfigTest() {
        assertEquals(
            ShopConfig(
                traderTitles = listOf(
                    "quick buy", "blocks", "melee", "armor",
                    "tools", "ranged", "potions", "utility"),
                initialCategorySlot = 0,
                tierDictionary = null,
                elements = blocksMCElements()
            ),
            parse("blocksmc")
        )
    }

    //TODO: more configs here

    @Suppress("LongMethod")
    private fun blocksMCElements() : List<ShopElement> {
        return listOf(
            ShopElement(
                item = ItemInfo(id = "wool", minAmount = 64),
                amountPerClick = 16, categorySlot = 0, itemSlot = 19,
                price = ItemInfo("iron_ingot", minAmount = 4)
            ),
            ShopElement(
                item = ItemInfo(id = "shears"),
                categorySlot = 0, itemSlot = 40,
                price = ItemInfo("iron_ingot", minAmount = 20)
            ),
            ShopElement(
                item = ItemInfo(id = "diamond_boots"),
                categorySlot = 0, itemSlot = 39,
                price = ItemInfo("emerald", minAmount = 6)
            ),
            ShopElement(
                item = ItemInfo(id = "iron_boots"),
                categorySlot = 0, itemSlot = 30,
                price = ItemInfo("gold_ingot", minAmount = 12),
                purchaseConditions = ItemNode(id = "diamond_boots", max = 0)
            ),
            ShopElement(
                item = ItemInfo(id = "chainmail_boots"),
                categorySlot = 0, itemSlot = 21,
                price = ItemInfo("iron_ingot", minAmount = 40),
                purchaseConditions = AllNode(listOf(
                    ItemNode(id = "iron_boots", max = 0),
                    ItemNode(id = "diamond_boots", max = 0),
                ))
            ),
            ShopElement(
                item = ItemInfo(id = "diamond_sword"),
                categorySlot = 0, itemSlot = 38,
                price = ItemInfo("emerald", minAmount = 3),
                purchaseConditions = ItemNode(id = "diamond_boots")
            ),
            ShopElement(
                item = ItemInfo(id = "iron_sword"),
                categorySlot = 0, itemSlot = 29,
                price = ItemInfo("gold_ingot", minAmount = 7),
                purchaseConditions = ItemNode(id = "diamond_sword", max = 0)
            ),
            ShopElement(
                item = ItemInfo(id = "stone_sword"),
                categorySlot = 0, itemSlot = 20,
                price = ItemInfo("iron_ingot", minAmount = 10),
                purchaseConditions = AllNode(listOf(
                    ItemNode(id = "iron_sword", max = 0),
                    ItemNode(id = "diamond_sword", max = 0)
                ))
            ),
            ShopElement(
                item = ItemInfo(id = "wooden_pickaxe"),
                categorySlot = 0, itemSlot = 22,
                price = ItemInfo("iron_ingot", minAmount = 10)
            ),
            ShopElement(
                item = ItemInfo(id = "wooden_axe"),
                categorySlot = 0, itemSlot = 31,
                price = ItemInfo("iron_ingot", minAmount = 10)
            ),
            ShopElement(
                item = ItemInfo(id = "golden_apple"),
                categorySlot = 0, itemSlot = 34,
                price = ItemInfo("gold_ingot", minAmount = 3)
            ),
            ShopElement(
                item = ItemInfo(id = "golden_apple", minAmount = 3),
                categorySlot = 0, itemSlot = 34,
                price = ItemInfo("gold_ingot", minAmount = 3),
                purchaseConditions = AllNode(listOf(
                    AnyNode(listOf(
                        ItemNode(id = "iron_boots"),
                        ItemNode(id = "diamond_boots")
                    )),
                    AnyNode(listOf(
                        ItemNode(id = "iron_sword"),
                        ItemNode(id = "diamond_sword")
                    ))
                ))
            ),
            ShopElement(
                item = ItemInfo(id = "potion:speed::1"),
                categorySlot = 0, itemSlot = 24,
                price = ItemInfo("emerald")
            ),
            ShopElement(
                item = ItemInfo(id = "potion:jump_boost::1"),
                categorySlot = 0, itemSlot = 33,
                price = ItemInfo("emerald")
            ),
            ShopElement(
                item = ItemInfo(id = "fireball"),
                categorySlot = 0, itemSlot = 43,
                price = ItemInfo("iron_ingot", minAmount = 40)
            ),
            ShopElement(
                item = ItemInfo(id = "bow:punch::1"),
                categorySlot = 5, itemSlot = 22,
                price = ItemInfo("emerald", minAmount = 6)
            )
        )
    }
}
