package net.ccbluex.liquidbounce.features.module.modules.player.autoshop

import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.utils.TIER_ID

fun String.isItemWithTiers() : Boolean {
    return this.contains(TIER_ID)
}

fun String.tierCategory() : String {
    return this.split(TIER_ID)[0]   // example: sword:tier::2 -> sword
}

fun String.autoShopItemTier() : Int {
    if (!isItemWithTiers()) {
        return 0
    }

    // example: sword:tier::2 -> 2
    return this.split(TIER_ID)[1].toIntOrNull() ?: 0
}


fun actualTierItem(item: String, itemsWithTiers: Map<String, List<String>> =
    ModuleAutoShop.currentConfig.tierDictionary ?: emptyMap()) : String {
    val tiers = itemsWithTiers[item.tierCategory()] ?: return item
    val tier = item.autoShopItemTier()

    // example: sword:tier::2 -> iron_sword
    return tiers.getOrElse(tier - 1) { item }
}

