package com.recital.scavengerhunt.ui.theme

import androidx.compose.ui.graphics.Color
import com.recital.scavengerhunt.location.HotColdLevel

object HuntColors {
    val success = Color(0xFF39FF14)
    val accent = Color(0xFFFF6B9D)
    val cyan = Color(0xFF00D4FF)
    val gold = Color(0xFFFFD93D)
    val card = Color(0xFF3D2066)
    val cardDeep = Color(0xFF2A1245)

    fun hotCold(level: HotColdLevel?): Color = when (level) {
        HotColdLevel.ARRIVED -> Color(0xFF39FF14)
        HotColdLevel.HOT -> Color(0xFFFF3366)
        HotColdLevel.WARM -> Color(0xFFFF8C42)
        HotColdLevel.COOL -> Color(0xFF00D4FF)
        HotColdLevel.COLD -> Color(0xFF7B68EE)
        HotColdLevel.FREEZING -> Color(0xFF5B7CFA)
        null -> cyan
    }
}
