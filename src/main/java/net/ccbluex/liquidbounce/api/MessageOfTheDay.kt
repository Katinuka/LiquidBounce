/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.api

import net.ccbluex.liquidbounce.api.ClientApi.requestMessageOfTheDayEndpoint
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER

val messageOfTheDay by lazy {
    try {
        requestMessageOfTheDayEndpoint()
    } catch (e: Exception) {
        LOGGER.error("Unable to receive message of the day", e)
        return@lazy null
    }
}
