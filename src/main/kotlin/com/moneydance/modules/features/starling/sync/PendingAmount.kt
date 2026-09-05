package com.moneydance.modules.features.starling.sync

import kotlin.math.abs

object PendingAmount {
    fun changed(registerMinor: Long, starlingMinor: Long): Boolean =
        abs(registerMinor) != abs(starlingMinor)

    fun registerMinor(currentRegister: Long, starlingMinor: Long): Long {
        val mag = abs(starlingMinor)
        return when {
            currentRegister > 0L -> mag
            currentRegister < 0L -> -mag
            else -> starlingMinor
        }
    }
}
