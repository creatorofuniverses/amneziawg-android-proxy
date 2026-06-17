/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.preference

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors

/**
 * Draws each contiguous run of rows inside a PreferenceCategory as a single
 * rounded surfaceContainer card (top corners on the first row, bottom on the
 * last) with a 1dp outlineVariant stroke and inner dividers. Category headers
 * are skipped. The card is drawn behind transparent rows.
 */
class PreferenceGroupCardDecoration(context: Context) : RecyclerView.ItemDecoration() {
    private val density = context.resources.displayMetrics.density
    private val radius = 16f * density
    private val stroke = 1f * density
    private val groupGap = (12f * density).toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainer, 0)
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutlineVariant, 0)
    }

    private fun adapter(parent: RecyclerView) = parent.adapter as? PreferenceGroupAdapter
    private fun itemAt(parent: RecyclerView, pos: Int): Preference? {
        val a = adapter(parent) ?: return null
        if (pos < 0 || pos >= a.itemCount) return null
        return a.getItem(pos)
    }
    private fun isRow(p: Preference?) = p != null && p !is PreferenceCategory

    override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        // Add the inter-group gap above the first row of each card.
        if (isRow(itemAt(parent, pos)) && !isRow(itemAt(parent, pos - 1))) {
            outRect.top = groupGap
        }
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        // Card spans the list's content box; the 16dp outer margin comes from the
        // listView horizontal padding and the inner padding from the row's own padding.
        val left = parent.paddingLeft.toFloat()
        val right = (parent.width - parent.paddingRight).toFloat()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            val pref = itemAt(parent, pos)
            if (!isRow(pref)) continue
            val first = !isRow(itemAt(parent, pos - 1))
            val last = !isRow(itemAt(parent, pos + 1))
            val top = child.top.toFloat()
            val bottom = child.bottom.toFloat()
            val rect = RectF(left, top, right, bottom)
            val rTop = if (first) radius else 0f
            val rBot = if (last) radius else 0f
            val path = Path().apply {
                addRoundRect(
                    rect,
                    floatArrayOf(rTop, rTop, rTop, rTop, rBot, rBot, rBot, rBot),
                    Path.Direction.CW
                )
            }
            c.drawPath(path, fill)
            // Inner divider under non-last rows.
            if (!last) c.drawLine(left + radius, bottom, right - radius, bottom, line)
            // Outer stroke.
            val half = stroke / 2f
            val strokeRect = RectF(left + half, top + half, right - half, bottom - half)
            val rTopS = if (first) radius else 0f
            val rBotS = if (last) radius else 0f
            val strokePath = Path().apply {
                addRoundRect(
                    strokeRect,
                    floatArrayOf(rTopS, rTopS, rTopS, rTopS, rBotS, rBotS, rBotS, rBotS),
                    Path.Direction.CW
                )
            }
            c.drawPath(strokePath, line)
        }
    }
}
