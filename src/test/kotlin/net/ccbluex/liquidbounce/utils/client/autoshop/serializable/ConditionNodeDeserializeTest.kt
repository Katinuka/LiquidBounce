package net.ccbluex.liquidbounce.utils.client.autoshop.serializable;

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.AutoShopConfig
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.AllConditionNode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.AnyConditionNode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.ConditionNode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions.ItemConditionNode
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
    fun itemConditionNodeTest() {
        assertEquals(
            ItemConditionNode(id="wool", min=16, max=32),
            parse("simple_item")
        )
        assertEquals(
            ItemConditionNode(id="wool", min=1, max=32),
            parse("without_min_value")
        )
        assertEquals(
            ItemConditionNode(id="potion:speed", min=2, max=Int.MAX_VALUE),
            parse("without_max_value")
        )
        assertEquals(
            ItemConditionNode(id="wool", min=48, max=Int.MAX_VALUE),
            parse("with_extra_fields")
        )
        assertEquals(
            ItemConditionNode(id="iron_sword", min=1, max=0),
            parse("no_item")
        )
        assertEquals(
            ItemConditionNode(id="sword:tier:1", min=1, max=Int.MAX_VALUE),
            parse("at_least_one")
        )
    }

    @Test
    fun allConditionNodeTest() {
        assertEquals(
            AllConditionNode(listOf(
                ItemConditionNode(id="wool", min=32, max=64),
                ItemConditionNode(id="golden_apple", min=2, max=6)
            )),
            parse("simple_all")
        )
        assertEquals(
            AllConditionNode(emptyList()),
            parse("empty_all")
        )
        assertEquals(
            AllConditionNode(listOf(
                ItemConditionNode(id="stone_sword", min = 1, max = Int.MAX_VALUE)
            )),
            parse("single_element_all")
        )
        assertEquals(
            AllConditionNode(listOf(
                ItemConditionNode(id="golden_apple", min = 2),
                AllConditionNode(listOf(
                    ItemConditionNode(id="chainmail_chestplate"),
                    ItemConditionNode(id="chainmail_leggings"),
                )),
                ItemConditionNode(id="iron_boots"),
                ItemConditionNode(id="diamond_sword", max = 0)
            )),
            parse("nested_all")
        )
    }

    @Test
    fun anyConditionNodeTest() {
        assertEquals(
            AnyConditionNode(listOf(
                ItemConditionNode(id="wool", min = 32, max = 128),
                ItemConditionNode(id="fireball", max = 10),
                ItemConditionNode(id="axe:tier:1"),
            )),
            parse("simple_any")
        )
        assertEquals(
            AnyConditionNode(listOf(
                ItemConditionNode(id="wool", min = 32, max = 128),
                ItemConditionNode(id="fireball", max = 10),
                ItemConditionNode(id="axe:tier:1"),
            )),
            parse("simple_any")
        )
        assertEquals(
            AnyConditionNode(),
            parse("empty_any")
        )
        assertEquals(
            AnyConditionNode(listOf(
                ItemConditionNode(id="diamond_sword:sharpness:1")
            )),
            parse("single_element_any")
        )
        assertEquals(
            AnyConditionNode(listOf(
                AnyConditionNode(listOf(
                    ItemConditionNode(id="diamond_sword"),
                    ItemConditionNode(id="iron_sword"),
                )),
                AnyConditionNode(listOf(
                    ItemConditionNode(id="diamond_chestplate"),
                    ItemConditionNode(id="iron_chestplate"),
                )),
                ItemConditionNode(id="emerald", min=4)
            )),
            parse("nested_any")
        )
    }

    @Test
    fun mixedConditionNodeTest() {
        assertEquals(
            AnyConditionNode(listOf(
                ItemConditionNode(id="gold_ingot", min=16),
                AllConditionNode(listOf(
                    AnyConditionNode(listOf(
                        ItemConditionNode(id="gold_ingot", min=10),
                        ItemConditionNode(id="golden_apple")
                    )),
                    AnyConditionNode(listOf(
                        ItemConditionNode(id="diamond_leggings"),
                        ItemConditionNode(id="iron_leggings")
                    )),
                    AnyConditionNode(listOf(
                        ItemConditionNode(id="diamond_chestplate"),
                        ItemConditionNode(id="iron_chestplate")
                    )),
                ))
            )),
            parse("mixed")
        )

        // TODO: this test should fail in the future!!
        //  These nested conditions should not exist when there are less than 2 elements
        assertEquals(
            AnyConditionNode(listOf(
                AllConditionNode(listOf(
                    AnyConditionNode(listOf(
                        AllConditionNode(listOf(
                            ItemConditionNode(id="obsidian", min = 4)
                        ))
                    ))
                ))
            )),
            parse("overly_nested")
        )
    }
}
