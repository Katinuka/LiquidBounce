package net.ccbluex.liquidbounce.utils.client.autoshop.serializable

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.AutoShopConfig
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.AllNode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.AnyNode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.ConditionNode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.ItemNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

object ConditionNodeDeserializeTest {
    private const val PATH = "/autoshop/condition_nodes.json"

    private lateinit var conditionNodes: JsonObject

    @JvmStatic
    @BeforeAll
    fun init() {
        javaClass.getResourceAsStream(PATH).use { inputStream ->
            check(inputStream != null) { "Failed to load resource: $PATH" }

            conditionNodes = JsonParser.parseReader(inputStream.reader()).asJsonObject
        }
    }

    private fun parse(member: String): ConditionNode {
        return AutoShopConfig.autoShopGson.fromJson(
            conditionNodes[member],
            ConditionNode::class.java)
    }

    @Test
    fun testItemConditionNode() {
        assertEquals(
            ItemNode(id="wool", min=16, max=32),
            parse("simple_item")
        )
        assertEquals(
            ItemNode(id="wool", min=1, max=32),
            parse("without_min_value")
        )
        assertEquals(
            ItemNode(id="potion:speed::1", min=2, max=Int.MAX_VALUE),
            parse("without_max_value")
        )
        assertEquals(
            ItemNode(id="wool", min=48, max=Int.MAX_VALUE),
            parse("with_extra_fields")
        )
        assertEquals(
            ItemNode(id="iron_sword", min=1, max=0),
            parse("no_item")
        )
        assertEquals(
            ItemNode(id="sword:tier::1", min=1, max=Int.MAX_VALUE),
            parse("at_least_one")
        )
    }

    @Test
    fun testAllConditionNode() {
        assertEquals(
            AllNode(listOf(
                ItemNode(id="wool", min=32, max=64),
                ItemNode(id="golden_apple", min=2, max=6)
            )),
            parse("simple_all")
        )
        assertEquals(
            AllNode(emptyList()),
            parse("empty_all")
        )
        assertEquals(
            AllNode(listOf(
                ItemNode(id="stone_sword", min = 1, max = Int.MAX_VALUE)
            )),
            parse("single_element_all")
        )
        assertEquals(
            AllNode(listOf(
                ItemNode(id="golden_apple", min = 2),
                AllNode(listOf(
                    ItemNode(id="chainmail_chestplate"),
                    ItemNode(id="chainmail_leggings"),
                )),
                ItemNode(id="iron_boots"),
                ItemNode(id="diamond_sword", max = 0)
            )),
            parse("nested_all")
        )
    }

    @Test
    fun testAnyConditionNode() {
        assertEquals(
            AnyNode(listOf(
                ItemNode(id="wool", min = 32, max = 128),
                ItemNode(id="fireball", max = 10),
                ItemNode(id="axe:tier::1"),
            )),
            parse("simple_any")
        )
        assertEquals(
            AnyNode(listOf(
                ItemNode(id="wool", min = 32, max = 128),
                ItemNode(id="fireball", max = 10),
                ItemNode(id="axe:tier::1"),
            )),
            parse("simple_any")
        )
        assertEquals(
            AnyNode(emptyList()),
            parse("empty_any")
        )
        assertEquals(
            AnyNode(listOf(
                ItemNode(id="diamond_sword:sharpness::1")
            )),
            parse("single_element_any")
        )
        assertEquals(
            AnyNode(listOf(
                AnyNode(listOf(
                    ItemNode(id="diamond_sword"),
                    ItemNode(id="iron_sword"),
                )),
                AnyNode(listOf(
                    ItemNode(id="diamond_chestplate"),
                    ItemNode(id="iron_chestplate"),
                )),
                ItemNode(id="emerald", min=4)
            )),
            parse("nested_any")
        )
    }

    @Test
    fun testMixedConditionNode() {
        assertEquals(
            AnyNode(listOf(
                ItemNode(id="gold_ingot", min=16),
                AllNode(listOf(
                    AnyNode(listOf(
                        ItemNode(id="gold_ingot", min=10),
                        ItemNode(id="golden_apple")
                    )),
                    AnyNode(listOf(
                        ItemNode(id="diamond_leggings"),
                        ItemNode(id="iron_leggings")
                    )),
                    AnyNode(listOf(
                        ItemNode(id="diamond_chestplate"),
                        ItemNode(id="iron_chestplate")
                    )),
                ))
            )),
            parse("mixed")
        )

        // TODO: this test should fail in the future!!
        //  These nested conditions should not exist when there are less than 2 elements
        //  Also, {all: [{"id": "wool", "min": 16}, {"id": "wool", "min": 32}]}
        //  should be condensed into just {"id": "wool", "min": 32}
        //  The whole process of config optimization should be created one day
        assertEquals(
            AnyNode(listOf(
                AllNode(listOf(
                    AnyNode(listOf(
                        AllNode(listOf(
                            ItemNode(id="obsidian", min = 4)
                        ))
                    ))
                ))
            )),
            parse("overly_nested")
        )
    }
}
