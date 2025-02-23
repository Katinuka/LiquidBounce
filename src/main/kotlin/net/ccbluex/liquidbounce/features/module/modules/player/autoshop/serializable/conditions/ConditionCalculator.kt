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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.conditions

import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.*
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.utils.betterItemsOf

/**
 * Calculates the values of condition nodes based on the [items] the player has.
 * This is not thread-safe.
 */
object ConditionCalculator {
    private val items = mutableMapOf<String, Int>()
    private val stack = mutableListOf<Pair<ConditionNode, Boolean>>()
    private val results = mutableMapOf<ConditionNode, Boolean>()

    fun items(newItems: Map<String, Int>) : ConditionCalculator {
        this.items.clear()
        this.items.putAll(newItems)
        return this
    }

    /**
     * Checks if the given item meets the conditions defined by the root node.
     * Uses Depth-First Search (DFS)
     */
    fun process(root: ConditionNode?) : Boolean {
        if (root == null) {
            return true
        }

        stack.add(root to false)

        while (stack.isNotEmpty()) {
            val (currentNode, isVisited) = stack.removeLast()

            when (currentNode) {
                is ItemNode -> processItemNode(currentNode)
                is AllNode -> processAllNode(currentNode, isVisited)
                is AnyNode -> processAnyNode(currentNode, isVisited)
            }
        }

        val result = results[root] ?: false
        results.clear()
        return result
    }

    /**
     * Evaluates whether the provided item node meets its condition.
     * The node's result is `true` if [items] contains the item in the required quantity.
     * If the item has tiers, any better item in the same amount will suffice.
     *
     * - If the item has tiers, any higher-tier item in the same quantity will also suffice.
     * - If [currentNode.max] is lower than [currentNode.min],
     *   [currentNode.min] will be capped at [currentNode.max].
     *
     */
    private fun processItemNode(currentNode: ItemNode) {
        val betterItemAmount = betterItemsOf(currentNode.id, items).values.sum()
        val itemAmount = (items[currentNode.id] ?: 0) + betterItemAmount

        val result = itemAmount <= currentNode.max &&
            itemAmount >= currentNode.min.coerceAtMost(currentNode.max)

        results[currentNode] = result
    }

    /**
     * Evaluates the value of the given "all" node.
     * This node contains multiple child nodes
     * that must all evaluate to true for the "all" condition to be satisfied.
     */
    private fun processAllNode(currentNode: AllNode, isVisited: Boolean) {
        if (currentNode.all.isEmpty()) {
            results[currentNode] = true
            return
        }

        if (isVisited) {
            results[currentNode] = currentNode.all.all { results[it] == true }
            return
        }

        stack.add(currentNode to true)
        currentNode.all.asReversed().forEach { childNode ->
            stack.add(childNode to false)
        }
    }

    /**
     * Evaluates the value of the given "any" node.
     * This node contains multiple child nodes
     * and the condition is satisfied if at least one of its child nodes evaluates to true.
     */
    private fun processAnyNode(currentNode: AnyNode, isVisited: Boolean) {
        if (currentNode.any.isEmpty()) {
            results[currentNode] = true
            return
        }

        if (isVisited) {
            results[currentNode] = currentNode.any.any { results[it] == true }
            return
        }

        stack.add(currentNode to true)
        currentNode.any.asReversed().forEach { childNode ->
            stack.add(childNode to false)
        }
    }
}
