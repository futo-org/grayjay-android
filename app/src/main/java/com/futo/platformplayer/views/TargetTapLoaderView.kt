package com.futo.platformplayer.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import kotlin.math.*
import kotlin.random.Random
import com.futo.platformplayer.UIDialogs

class TargetTapLoaderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val primaryColor = "#2D63ED".toColorInt()
    private val inactiveGlobalAlpha = 110
    private val idleSpeedMultiplier = .015f
    private val overshootInterpolator = OvershootInterpolator(1.5f)
    private val floatAccel = .03f
    private val idleMaxSpeed = .35f
    private val idleInitialTargets = 10
    private val idleHintText = "Waiting for media to become available"

    private var expectedDurationMs: Long? = null
    private var loadStartTime = 0L
    private var playStartTime = 0L
    private var loaderFinished = false
    private var forceIndeterminate= false
    private var lastFrameTime = System.currentTimeMillis()

    private var score = 0
    private var isPlaying = false

    private val targets = mutableListOf<Target>()
    private val particles = mutableListOf<Particle>()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18f, resources.displayMetrics)
        textAlign = Paint.Align.LEFT
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
        typeface = Typeface.DEFAULT_BOLD
    }
    private val idleHintPaint = Paint(textPaint).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        typeface = Typeface.DEFAULT
        setShadowLayer(2f, 0f, 0f, Color.BLACK)
    }
    private val progressBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryColor }
    private val spinnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor; strokeWidth = 12f
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val middleRingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(50, 0, 0, 0) }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint()
    private var spinnerShader: SweepGradient? = null
    private var spinnerAngle = 0f
    private val MIN_SPAWN_RATE = 1f
    private val MAX_SPAWN_RATE = 20.0f
    private val HIT_RATE_INCREMENT = 0.15f
    private val MISS_RATE_DECREMENT = 0.09f
    private var spawnRate = MIN_SPAWN_RATE

    private val frameRunnable = object : Runnable {
        override fun run() { invalidate(); if (!loaderFinished) postDelayed(this, 16L) }
    }

    init { setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_DOWN) handleTap(e.x, e.y); true } }

    fun startLoader(durationMs: Long? = null) {
        val alreadyRunning = !loaderFinished
        if (alreadyRunning && durationMs == null) {
            expectedDurationMs = null
            forceIndeterminate = true
            return
        }

        expectedDurationMs = durationMs?.takeIf { it > 0 }
        forceIndeterminate = expectedDurationMs == null
        loaderFinished = false
        isPlaying = false
        score = 0
        particles.clear()
        spawnRate = MIN_SPAWN_RATE

        post { if (targets.isEmpty()) prepopulateIdleTargets() }

        loadStartTime = System.currentTimeMillis()
        playStartTime = 0
        removeCallbacks(frameRunnable)
        post(frameRunnable)

        if (!isIndeterminate) {
            postDelayed({
                if (!loaderFinished) {
                    forceIndeterminate = true
                    expectedDurationMs = null
                }
            }, expectedDurationMs!!)
        }
    }

    fun finishLoader() {
        loaderFinished = true
        particles.clear()
        isPlaying = false
        invalidate()
    }

    fun stopAndResetLoader() {
        if (score > 0) {
            val elapsed = (System.currentTimeMillis() - (if (playStartTime > 0) playStartTime else loadStartTime)) / 1000.0
            UIDialogs.toast("Nice! score $score | ${"%.1f".format(score / elapsed)} / s")
            score = 0
        }
        loaderFinished = true
        isPlaying = false
        targets.clear()
        particles.clear()
        removeCallbacks(frameRunnable)
        invalidate()
    }

    private val isIndeterminate get() = forceIndeterminate || expectedDurationMs == null || expectedDurationMs == 0L

    private fun handleTap(x: Float, y: Float) {
        val idx = targets.indexOfFirst { !it.hit && hypot(x - it.x, y - it.y) <= it.radius }
        if (idx >= 0) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val t = targets[idx]
            t.hit = true; t.hitTime = System.currentTimeMillis()
            accelerateSpawnRate()
            score += if (!isIndeterminate) 10 else 5
            spawnParticles(t.x, t.y, t.radius)

            if (!isPlaying) {
                isPlaying = true
                playStartTime  = System.currentTimeMillis()
                score = 0
                spawnRate = MIN_SPAWN_RATE
                targets.retainAll { it === t }
                spawnTarget()
            }
        } else if (isPlaying) decelerateSpawnRate()
    }

    private inline fun accelerateSpawnRate() {
        spawnRate = (spawnRate + HIT_RATE_INCREMENT).coerceAtMost(MAX_SPAWN_RATE)
    }

    private inline fun decelerateSpawnRate() {
        spawnRate = (spawnRate - MISS_RATE_DECREMENT).coerceAtLeast(MIN_SPAWN_RATE)
    }

    private fun spawnTarget() {
        if (loaderFinished || width == 0 || height == 0) {
            postDelayed({ spawnTarget() }, 200L); return
        }

        if (!isPlaying) {
            postDelayed({ spawnTarget() }, 500L); return
        }

        val radius = Random.nextInt(40, 80).toFloat()
        val x = Random.nextFloat() * (width  - 2 * radius) + radius
        val y = Random.nextFloat() * (height - 2 * radius - 60f) + radius

        val baseSpeed = Random.nextFloat() + .1f
        val speed = baseSpeed
        val angle = Random.nextFloat() * TAU
        val vx = cos(angle) * speed
        val vy = sin(angle) * speed
        val alpha = Random.nextInt(150, 255)

        targets += Target(x, y, radius, System.currentTimeMillis(), baseAlpha = alpha, vx = vx, vy = vy)

        val delay = (1000f / spawnRate).roundToLong()
        postDelayed({ spawnTarget() }, delay)
    }

    private fun prepopulateIdleTargets() {
        if (width == 0 || height == 0) {
            post { prepopulateIdleTargets() }
            return
        }
        repeat(idleInitialTargets) {
            val radius = Random.nextInt(40, 80).toFloat()
            val x = Random.nextFloat() * (width  - 2 * radius) + radius
            val y = Random.nextFloat() * (height - 2 * radius - 60f) + radius
            val angle = Random.nextFloat() * TAU
            val speed = (Random.nextFloat() * .3f + .05f) * idleSpeedMultiplier
            val vx = cos(angle) * speed
            val vy = sin(angle) * speed
            val alpha = Random.nextInt(60, 110)
            targets += Target(x, y, radius, System.currentTimeMillis(), baseAlpha = alpha, vx = vx, vy = vy)
        }
    }

    private fun spawnParticles(cx: Float, cy: Float, radius: Float) {
        repeat(12) {
            val angle = Random.nextFloat() * TAU
            val speed = Random.nextFloat() * 5f + 2f
            val vx = cos(angle) * speed
            val vy = sin(angle) * speed
            val col = ColorUtils.setAlphaComponent(primaryColor, Random.nextInt(120, 255))
            particles += Particle(cx, cy, vx, vy, System.currentTimeMillis(), col)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now     = System.currentTimeMillis()
        val deltaMs = now - lastFrameTime
        lastFrameTime = now

        drawBackground(canvas)
        drawTargets(canvas, now)
        drawParticles(canvas, now)

        if (!loaderFinished) {
            if (isIndeterminate) drawIndeterminateSpinner(canvas, deltaMs)
            else drawDeterministicProgressBar(canvas, now)
        }

        if (isPlaying) {
            val margin = 24f
            val scoreTxt = "Score $score"
            val speedTxt = "Speed ${"%.2f".format(spawnRate)}/s"
            val maxWidth = width - margin
            val needRight = max(textPaint.measureText(scoreTxt), textPaint.measureText(speedTxt)) > maxWidth

            val alignX = if (needRight) (width - margin) else margin
            textPaint.textAlign = if (needRight) Paint.Align.RIGHT else Paint.Align.LEFT

            canvas.drawText(scoreTxt, alignX, textPaint.textSize + margin, textPaint)
            canvas.drawText(speedTxt, alignX, 2*textPaint.textSize + margin + 4f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }
        else if (loaderFinished)
            canvas.drawText("Loading Complete!", width/2f, height/2f, textPaint.apply { textAlign = Paint.Align.CENTER })
        else {
            idleHintPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(idleHintText, width / 2f, height - 48f, idleHintPaint)
        }
    }

    private fun drawBackground(canvas: Canvas) {
        val colors = intArrayOf(
            Color.rgb(20, 20, 40),
            Color.rgb(15, 15, 30),
            Color.rgb(10, 10, 20),
            Color.rgb( 5,  5, 10),
            Color.BLACK
        )
        val pos = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)

        if (backgroundPaint.shader == null) {
            backgroundPaint.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                colors, pos,
                Shader.TileMode.CLAMP
            )
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
    }

    private fun drawTargets(canvas: Canvas, now: Long) {
        val expireMsActive = if (isIndeterminate) 2500L else 1500L
        val it = targets.iterator()
        while (it.hasNext()) {
            val t = it.next()
            if (t.hit && now - t.hitTime > 300L) { it.remove(); continue }
            if (isPlaying && !t.hit && now - t.spawnTime > expireMsActive) {
                it.remove(); decelerateSpawnRate(); continue
            }
            t.x += t.vx; t.y += t.vy
            t.vx += (Random.nextFloat() - .5f) * floatAccel
            t.vy += (Random.nextFloat() - .5f) * floatAccel
            val speedCap = if (isPlaying) Float.MAX_VALUE else idleMaxSpeed
            val mag = hypot(t.vx, t.vy)
            if (mag > speedCap) {
                val s = speedCap / mag
                t.vx *= s; t.vy *= s
            }
            if (t.x - t.radius < 0 || t.x + t.radius > width)  t.vx *= -1
            if (t.y - t.radius < 0 || t.y + t.radius > height) t.vy *= -1
            val scale = if (t.hit) 1f - ((now - t.hitTime) / 300f).coerceIn(0f,1f)
            else {
                val e = now - t.spawnAnimStart
                if (e < 300L) overshootInterpolator.getInterpolation(e/300f)
                else 1f + .02f * sin(((now - t.spawnAnimStart)/1000f)*TAU + t.pulseOffset)
            }
            val animAlpha = if (t.hit) ((1f - scale)*255).toInt() else 255
            val globalAlpha = if (isPlaying) 255 else inactiveGlobalAlpha
            val alpha = (animAlpha * t.baseAlpha /255f * globalAlpha/255f).toInt().coerceAtMost(255)
            val r = max(1f, t.radius*scale)
            val outerCol = ColorUtils.setAlphaComponent(primaryColor, alpha)
            val midCol = ColorUtils.setAlphaComponent(primaryColor, (alpha*.7f).toInt())
            val innerCol = ColorUtils.setAlphaComponent(primaryColor, (alpha*.4f).toInt())
            outerRingPaint.color = outerCol; middleRingPaint.color = midCol; centerDotPaint.color = innerCol

            glowPaint.shader = RadialGradient(t.x, t.y, r, outerCol, Color.TRANSPARENT, Shader.TileMode.CLAMP)

            canvas.drawCircle(t.x, t.y, r*1.2f, glowPaint)
            canvas.drawCircle(t.x+4f, t.y+4f, r, shadowPaint)
            canvas.drawCircle(t.x, t.y, r, outerRingPaint)
            canvas.drawCircle(t.x, t.y, r*.66f, middleRingPaint)
            canvas.drawCircle(t.x, t.y, r*.33f, centerDotPaint)
        }
    }

    private fun drawParticles(canvas: Canvas, now: Long) {
        val lifespan = 400L
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            val age = now - p.startTime
            if (age > lifespan) { it.remove(); continue }
            val a = ((1f - age/lifespan.toFloat())*255).toInt()
            particlePaint.color = ColorUtils.setAlphaComponent(p.baseColor, a)
            p.x += p.vx; p.y += p.vy
            canvas.drawCircle(p.x, p.y, 6f, particlePaint)
        }
    }

    private fun drawDeterministicProgressBar(canvas: Canvas, now: Long) {
        val dur = expectedDurationMs ?: return
        val prog = ((now - loadStartTime) / dur.toFloat()).coerceIn(0f, 1f)
        val eased = AccelerateDecelerateInterpolator().getInterpolation(prog)
        val h = 20f; val r=10f
        canvas.drawRoundRect(RectF(0f, height-h, width*eased, height.toFloat()), r, r, progressBarPaint)
    }

    private fun drawIndeterminateSpinner(canvas: Canvas, dt: Long) {
        val cx=width/2f; val cy=height/2f; val r=min(width,height)/6f
        spinnerAngle = (spinnerAngle + .25f*dt)%360f
        if(spinnerShader == null) spinnerShader = SweepGradient(cx,cy,intArrayOf(Color.TRANSPARENT,Color.WHITE,Color.TRANSPARENT),floatArrayOf(0f,.5f,1f))
        spinnerPaint.shader = spinnerShader
        val glow = Paint(spinnerPaint).apply{ maskFilter = BlurMaskFilter(15f,BlurMaskFilter.Blur.SOLID) }
        val sweep = 270f
        canvas.drawArc(cx-r,cy-r,cx+r,cy+r,spinnerAngle,sweep,false,glow)
        canvas.drawArc(cx-r,cy-r,cx+r,cy+r,spinnerAngle,sweep,false,spinnerPaint)
    }

    private data class Target(
        var x: Float,
        var y: Float,
        val radius: Float,
        val spawnTime: Long,
        var hit: Boolean = false,
        var hitTime: Long = 0L,
        val baseAlpha: Int = 255,
        var vx: Float=0f,
        var vy:Float=0f,
        val spawnAnimStart: Long = System.currentTimeMillis(),
        val pulseOffset: Float = Random.nextFloat() * TAU
    )
    private data class Particle(
        var x:Float,
        var y:Float,
        val vx:Float,
        val vy:Float,
        val startTime:Long,
        val baseColor:Int
    )

    private companion object { private const val TAU = (2 * Math.PI).toFloat() }
}
