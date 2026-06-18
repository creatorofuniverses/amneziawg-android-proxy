/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import com.journeyapps.barcodescanner.ViewfinderView

/**
 * Network-Teal viewfinder overlay for the QR scanner. Dims the area outside a
 * centered rounded window and draws four teal corner brackets — intentionally no
 * red laser line. Swapped in for ZXing's stock [ViewfinderView] via
 * `awg_barcode_scanner.xml`.
 */
class AwgViewfinderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ViewfinderView(context, attrs) {

    private val dp = resources.displayMetrics.density
    private val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xA6060809.toInt() }
    private val teal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2DD4BF.toInt(); style = Paint.Style.STROKE
        strokeWidth = 3 * dp; strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        // Centered square window, ~64% of the smaller edge, clamped.
        val side = (minOf(width, height) * 0.64f).coerceIn(180 * dp, 320 * dp)
        val r = 24 * dp
        val l = (width - side) / 2f
        val t = (height - side) / 2.35f
        val win = RectF(l, t, l + side, t + side)

        // Dim everything outside the rounded window (even-odd punch-out).
        val path = Path().apply {
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addRoundRect(win, r, r, Path.Direction.CCW)
            fillType = Path.FillType.EVEN_ODD
        }
        canvas.drawPath(path, mask)

        // Four teal corner brackets.
        val a = 34 * dp
        fun corner(cx: Float, cy: Float, sx: Int, sy: Int) {
            val p = Path()
            p.moveTo(cx, cy + sy * a); p.lineTo(cx, cy + sy * r)
            p.quadTo(cx, cy, cx + sx * r, cy); p.lineTo(cx + sx * a, cy)
            canvas.drawPath(p, teal)
        }
        corner(win.left, win.top, +1, +1)
        corner(win.right, win.top, -1, +1)
        corner(win.left, win.bottom, +1, -1)
        corner(win.right, win.bottom, -1, -1)
        // No laser line — intentionally omitted.
    }
}
