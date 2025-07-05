package com.futo.platformplayer.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.graphics.toColorInt
import kotlin.math.*
import kotlin.random.Random

class TargetTapLoaderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var expectedDurationMs: Long? = null
    private var startTime: Long = 0L
    private var loaderFinished = false
    private var forceIndeterminate = false

    private val isIndeterminate: Boolean
        get() = forceIndeterminate || expectedDurationMs == null || expectedDurationMs == 0L

    private val targets = mutableListOf<Target>()
    private val particles = mutableListOf<Particle>()
    private var score = 0

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 48f
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
        typeface = Typeface.DEFAULT_BOLD
    }
    private val progressBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#2D63ED".toColorInt()
    }
    private val spinnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#2D63ED".toColorInt()
        strokeWidth = 12f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val middleRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 0, 0, 0)
    }
    private val backgroundPaint = Paint()

    private var spinnerAngle = 0f

    private val frameRunnable = object : Runnable {
        override fun run() {
            invalidate()
            if (!loaderFinished) postDelayed(this, 16L)
        }
    }

    init {
        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                handleTap(event.x, event.y)
            }
            true
        }
    }

    fun startLoader(durationMs: Long? = null) {
        expectedDurationMs = durationMs?.takeIf { it > 0L }
        forceIndeterminate = expectedDurationMs == null
        loaderFinished = false
        startTime = System.currentTimeMillis()
        score = 0
        targets.clear()
        particles.clear()
        removeCallbacks(frameRunnable)
        post(frameRunnable)
        post { spawnTarget() }

        if (!isIndeterminate) {
            postDelayed({
                if (!loaderFinished) {
                    forceIndeterminate = true
                    startTime = System.currentTimeMillis()
                    spawnTarget()
                }
            }, expectedDurationMs!!)
        }
    }

    fun finishLoader() {
        loaderFinished = true
        invalidate()
    }

    fun stopAndResetLoader() {
        loaderFinished = true
        targets.clear()
        particles.clear()
        removeCallbacks(frameRunnable)
        invalidate()
    }

    private fun handleTap(x: Float, y: Float) {
        val now = System.currentTimeMillis()
        val hitIndex = targets.indexOfFirst { t -> !t.hit && hypot(x - t.x, y - t.y) <= t.radius }
        if (hitIndex >= 0) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val target = targets[hitIndex]
            target.hit = true
            target.hitTime = now
            score += if (!isIndeterminate) 10 else 5
            spawnParticles(target.x, target.y, target.radius)
        }
    }

    private fun spawnTarget() {
        if (loaderFinished) return
        val radius = Random.nextInt(40, 80).toFloat()
        val x = Random.nextFloat() * (width - 2 * radius) + radius
        val y = Random.nextFloat() * (height - 2 * radius - 60f) + radius
        targets.add(Target(x, y, radius, System.currentTimeMillis()))

        val delay = if (isIndeterminate) 1400L else 700L
        postDelayed({ spawnTarget() }, delay)
    }

    private fun spawnParticles(cx: Float, cy: Float, radius: Float) {
        repeat(12) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = Random.nextFloat() * 5f + 2f
            val vx = cos(angle) * speed
            val vy = sin(angle) * speed
            particles.add(Particle(cx, cy, vx, vy, 255, System.currentTimeMillis()))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas)

        val now = System.currentTimeMillis()
        drawTargets(canvas, now)
        drawParticles(canvas, now)

        if (!loaderFinished) {
            if (isIndeterminate) drawIndeterminateSpinner(canvas)
            else drawDeterministicProgressBar(canvas, now)
        }

        canvas.drawText("Score: $score", width / 2f, height - 80f, textPaint)

        if (loaderFinished) {
            canvas.drawText("Loading Complete!", width / 2f, height / 2f, textPaint)
        }
    }

    private fun drawBackground(canvas: Canvas) {
        val gradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.rgb(20, 20, 40), Color.BLACK,
            Shader.TileMode.CLAMP
        )
        backgroundPaint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
    }

    private fun drawTargets(canvas: Canvas, now: Long) {
        val expireMs = if (isIndeterminate) 2500L else 1500L
        targets.removeAll { it.hit && now - it.hitTime > 300L }
        targets.removeAll { !it.hit && now - it.spawnTime > expireMs }

        for (t in targets) {
            val scale = if (t.hit) 1f - ((now - t.hitTime) / 300f).coerceIn(0f, 1f) else 1f
            val alpha = if (t.hit) ((1f - scale) * 255).toInt().coerceAtMost(255) else 255
            val safeRadius = (t.radius * scale).coerceAtLeast(1f)
            val glowPaint = Paint().apply {
                shader = RadialGradient(t.x, t.y, safeRadius, Color.YELLOW, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(t.x, t.y, safeRadius * 1.2f, glowPaint)
            canvas.drawCircle(t.x + 4f, t.y + 4f, safeRadius, shadowPaint)
            outerRingPaint.alpha = alpha
            middleRingPaint.alpha = alpha
            centerDotPaint.alpha = alpha
            canvas.drawCircle(t.x, t.y, safeRadius, outerRingPaint)
            canvas.drawCircle(t.x, t.y, safeRadius * 0.66f, middleRingPaint)
            canvas.drawCircle(t.x, t.y, safeRadius * 0.33f, centerDotPaint)
        }
    }

    private fun drawParticles(canvas: Canvas, now: Long) {
        val lifespan = 400L
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            val age = now - p.startTime
            if (age > lifespan) {
                iterator.remove()
                continue
            }
            val alpha = ((1f - (age / lifespan.toFloat())) * 255).toInt()
            val paint = Paint().apply {
                color = Color.YELLOW
                this.alpha = alpha
            }
            p.x += p.vx
            p.y += p.vy
            canvas.drawCircle(p.x, p.y, 6f, paint)
        }
    }

    private fun drawDeterministicProgressBar(canvas: Canvas, now: Long) {
        val duration = expectedDurationMs ?: return
        val rawProgress = ((now - startTime).toFloat() / duration).coerceIn(0f, 1f)
        val easedProgress = AccelerateDecelerateInterpolator().getInterpolation(rawProgress)

        val barHeight = 20f
        val barRadius = 10f
        val barWidth = width * easedProgress

        val rect = RectF(0f, height - barHeight, barWidth, height.toFloat())
        canvas.drawRoundRect(rect, barRadius, barRadius, progressBarPaint)
    }

    private fun drawIndeterminateSpinner(canvas: Canvas) {
        spinnerAngle = (spinnerAngle + 6f) % 360f
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 6f
        val sweepAngle = 270f

        val glowPaint = Paint(spinnerPaint).apply {
            maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.SOLID)
        }

        val shader = SweepGradient(cx, cy, intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.TRANSPARENT), floatArrayOf(0f, 0.5f, 1f))
        spinnerPaint.shader = shader

        canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius, spinnerAngle, sweepAngle, false, glowPaint)
        canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius, spinnerAngle, sweepAngle, false, spinnerPaint)
    }

    private data class Target(
        val x: Float,
        val y: Float,
        val radius: Float,
        val spawnTime: Long,
        var hit: Boolean = false,
        var hitTime: Long = 0L
    )

    private data class Particle(
        var x: Float,
        var y: Float,
        val vx: Float,
        val vy: Float,
        var alpha: Int,
        val startTime: Long
    )
}
