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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

/**
 * A node used to describe a condition.
 * The condition is the presence of the specified item in certain amount.
 *
 * Example: {"id": "wool", "min": 16, "max": 32}
 *
 * If the player has 16 to 32 wool blocks, both ends are inclusive,
 * the result of this node will be true when it's been calculated.
 * Otherwise, it's false.
 *
 * If the player shouldn't have an item, this example can be used:
 *
 * {"id": "iron_sword", "max": 0}
 *
 * If the player should have at least 1 item, this example can be used:
 *
 * {"id": "iron_sword"}
 */
data class ItemNode(
    val id: String,
    val min: Int = 1,
    val max: Int = Int.MAX_VALUE
) : ConditionNode

/**
 * A custom deserializer for [ItemNode], responsible for
 * converting a JSON representation into an [ItemNode] instance.
 * Ensures required fields are present and applies default values
 * where necessary.
 */
class ItemNodeDeserializer : JsonDeserializer<ItemNode> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type,
        context: JsonDeserializationContext): ItemNode {

        if (json == null || !json.isJsonObject) {
            throw JsonParseException("Invalid JSON: Expected a JsonObject")
        }

        val jsonObject = json.asJsonObject

        if (!jsonObject.has("id")) {
            throw JsonParseException("Invalid JSON: Missing 'id' property")
        }
        val id = jsonObject["id"].asString
        val min = jsonObject["min"]?.asInt ?: 1
        val max = jsonObject["max"]?.asInt ?: Int.MAX_VALUE

        return ItemNode(id, min, max)
    }
}
