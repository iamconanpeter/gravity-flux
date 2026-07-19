/*
 * GravityFluxView.kt
 *
 * Custom Canvas View that renders a GravityFluxEngine chamber: neon walls, a
 * glowing flux orb (RadialGradient), pulsing NODEs, flickering HAZARDs, a
 * swirling EXIT portal, and the START marker. The orb slides between cells via
 * a ValueAnimator; all Paint objects are pre-allocated in init to avoid
 * per-frame allocations.
 */

package com.iamconanpeter.gravityflux.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.core.animation.doOnEnd
import com.iamconanpeter.gravityflux.engine.Cell
import com.iamconanpeter.gravityflux.engine.GravityFluxEngine
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class GravityFluxView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var engine: GravityFluxEngine? = null

    // Animation state for the orb slide.
    private var animFromRow = 0
    private var animFromCol = 0
    private var animToRow = 0
    private var animToCol = 0
    private var animProgress = 1f // 1 == settled on orbRow/orbCol
    private var slideAnimator: ValueAnimator? = null

    // Density scale so sizing works across screen densities.
    private val density = context.resources.displayMetrics.density

    // ---- Pre-allocated paints (no per-frame allocation) ----------------

    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1B2A4A.toInt()
        style = Paint.Style.FILL
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A0F1E.toInt()
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF24365F.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3A4A6B.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        style = Paint.Style.FILL
    }
    private val nodeCollectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1B6B73.toInt()
        style = Paint.Style.FILL
    }
    private val hazardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF3B6B.toInt()
        style = Paint.Style.FILL
    }
    private val exitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB46BFF.toInt()
        style = Paint.Style.FILL
    }
    private val orbCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val orbGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // ---- Public API ----------------------------------------------------

    fun setEngine(engine: GravityFluxEngine) {
        this.engine = engine
        animProgress = 1f
        slideAnimator?.cancel()
        requestLayout()
        render()
    }

    /** Force a redraw (e.g. after undo/reset). */
    fun render() {
        animProgress = 1f
        invalidate()
    }

    /**
     * Animate the orb from one cell to another, then invoke onEnd.
     * If from == to, onEnd is still called (state changed but no movement).
     */
    fun animateSlide(
        fromRow: Int,
        fromCol: Int,
        toRow: Int,
        toCol: Int,
        onEnd: () -> Unit
    ) {
        animFromRow = fromRow
        animFromCol = fromCol
        animToRow = toRow
        animToCol = toCol

        slideAnimator?.cancel()
        slideAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180L
            addUpdateListener { anim ->
                animProgress = anim.animatedValue as Float
                invalidate()
            }
            doOnEnd {
                animProgress = 1f
                onEnd()
            }
            start()
        }
    }

    // ---- Layout --------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val eng = engine
        if (eng == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val avail = min(
            MeasureSpec.getSize(widthMeasureSpec).toFloat(),
            MeasureSpec.getSize(heightMeasureSpec).toFloat()
        )
        val side = maxOf(200f * density, avail)
        setMeasuredDimension(side.toInt(), side.toInt())
    }

    // ---- Drawing -------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val eng = engine ?: return
        val chamber = eng.chamberView

        val w = width.toFloat()
        val h = height.toFloat()
        val cols = chamber.cols
        val rows = chamber.rows
        val cell = min(w / cols, h / rows)
        val offsetX = (w - cell * cols) / 2f
        val offsetY = (h - cell * rows) / 2f

        val now = SystemClock.uptimeMillis()

        // Background.
        canvas.drawColor(0xFF05080F.toInt())

        // Cells.
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = offsetX + c * cell
                val y = offsetY + r * cell
                val cx = x + cell / 2f
                val cy = y + cell / 2f
                val cellType = chamber.grid[r][c]

                when (cellType) {
                    Cell.WALL -> canvas.drawRect(x, y, x + cell, y + cell, wallPaint)
                    Cell.EMPTY, Cell.START -> {
                        canvas.drawRect(x, y, x + cell, y + cell, emptyPaint)
                        canvas.drawRect(x, y, x + cell, y + cell, gridPaint)
                        if (cellType == Cell.START) {
                            canvas.drawCircle(cx, cy, cell * 0.22f, startPaint)
                        }
                    }
                    Cell.NODE -> {
                        canvas.drawRect(x, y, x + cell, y + cell, emptyPaint)
                        canvas.drawRect(x, y, x + cell, y + cell, gridPaint)
                        val collected = eng.collectedNodes.contains(r to c)
                        val pulse = 0.5f + 0.5f * sin(now / 220.0 + (r + c)).toFloat()
                        val radius = cell * (0.18f + 0.08f * pulse)
                        canvas.drawCircle(
                            cx, cy, radius,
                            if (collected) nodeCollectedPaint else nodePaint
                        )
                    }
                    Cell.HAZARD -> {
                        canvas.drawRect(x, y, x + cell, y + cell, emptyPaint)
                        canvas.drawRect(x, y, x + cell, y + cell, gridPaint)
                        val flicker = if (((now / 120) + (r * 3 + c)) % 2 == 0L) 1f else 0.5f
                        val hp = Paint(hazardPaint).apply { alpha = (255 * flicker).toInt() }
                        canvas.drawRect(
                            x + cell * 0.2f, y + cell * 0.2f,
                            x + cell * 0.8f, y + cell * 0.8f, hp
                        )
                    }
                    Cell.EXIT -> {
                        canvas.drawRect(x, y, x + cell, y + cell, emptyPaint)
                        canvas.drawRect(x, y, x + cell, y + cell, gridPaint)
                        val spin = now / 400.0
                        val rr = cell * 0.32f
                        for (i in 0..2) {
                            val a = (spin + i * (Math.PI * 2 / 3)).toFloat()
                            val px = cx + cos(a) * rr * 0.6f
                            val py = cy + sin(a) * rr * 0.6f
                            canvas.drawCircle(px, py, cell * 0.1f, exitPaint)
                        }
                        canvas.drawCircle(cx, cy, cell * 0.14f, exitPaint)
                    }
                }
            }
        }

        // Orb (interpolated between from and to during a slide).
        val orbRowF = lerp(animFromRow.toFloat(), animToRow.toFloat(), animProgress)
        val orbColF = lerp(animFromCol.toFloat(), animToCol.toFloat(), animProgress)
        val orbX = offsetX + (orbColF + 0.5f) * cell
        val orbY = offsetY + (orbRowF + 0.5f) * cell
        val glowRadius = cell * 0.55f
        orbGlowPaint.shader = RadialGradient(
            orbX, orbY, glowRadius,
            0xFF00E5FF.toInt(), 0x0000E5FF, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(orbX, orbY, glowRadius, orbGlowPaint)
        canvas.drawCircle(orbX, orbY, cell * 0.18f, orbCorePaint)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
