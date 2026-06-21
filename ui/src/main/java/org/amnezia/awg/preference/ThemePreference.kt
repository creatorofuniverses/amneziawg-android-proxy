/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.preference

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButtonToggleGroup
import org.amnezia.awg.R
import org.amnezia.awg.util.ThemeMode
import org.amnezia.awg.util.UserKnobs
import org.amnezia.awg.util.applicationScope
import kotlinx.coroutines.launch

/**
 * Settings row that lets the user force System / Light / Dark via a 3-way
 * segmented button. Selecting an option persists it to [UserKnobs.themeMode];
 * Application observes that and applies the night mode app-wide (recreating
 * activities), which re-binds this row with the new selection.
 */
class ThemePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    init {
        layoutResource = R.layout.preference_awg_theme
        isPersistent = false
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val group = holder.findViewById(R.id.theme_toggle) as MaterialButtonToggleGroup
        val caption = holder.findViewById(R.id.theme_caption) as TextView

        val mode = ThemeMode.fromNightMode(AppCompatDelegate.getDefaultNightMode())
        val buttonId = when (mode) {
            ThemeMode.LIGHT -> R.id.btn_theme_light
            ThemeMode.DARK -> R.id.btn_theme_dark
            else -> R.id.btn_theme_system
        }

        // Reflect current state without firing the listener.
        group.clearOnButtonCheckedListeners()
        group.check(buttonId)
        updateCaption(caption, mode)

        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val selected = when (checkedId) {
                R.id.btn_theme_light -> ThemeMode.LIGHT
                R.id.btn_theme_dark -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            updateCaption(caption, selected)
            applicationScope.launch { UserKnobs.setThemeMode(selected) }
        }
    }

    private fun updateCaption(caption: TextView, mode: String) {
        caption.setText(
            when (mode) {
                ThemeMode.LIGHT -> R.string.theme_caption_light
                ThemeMode.DARK -> R.string.theme_caption_dark
                else -> R.string.theme_caption_system
            }
        )
    }
}
