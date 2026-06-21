/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.util

import androidx.appcompat.app.AppCompatDelegate

/**
 * The three user-selectable theme modes and their mapping to AppCompatDelegate
 * night modes. Persisted as a string in [UserKnobs.themeMode].
 */
object ThemeMode {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    fun toNightMode(mode: String): Int = when (mode) {
        LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun fromNightMode(nightMode: Int): String = when (nightMode) {
        AppCompatDelegate.MODE_NIGHT_NO -> LIGHT
        AppCompatDelegate.MODE_NIGHT_YES -> DARK
        else -> SYSTEM
    }
}
