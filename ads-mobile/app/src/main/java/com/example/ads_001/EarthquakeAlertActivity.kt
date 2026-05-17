package com.example.ads_001

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class EarthquakeAlertActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SOURCE = "earthquake_source"
        const val EXTRA_LOCAL_INTENSITY_SCORE = "local_intensity_score"
        const val EXTRA_LOCAL_INTENSITY_LABEL = "local_intensity_label"
        const val EXTRA_LOCAL_INTENSITY_LEVEL = "local_intensity_level"
        // Auto-dismiss after 30 seconds
        private const val AUTO_DISMISS_MS = 30_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on, show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val source = intent.getStringExtra(EXTRA_SOURCE) ?: "Bilinmeyen"
        val localIntensity = readLocalIntensitySnapshot()
        val root = buildUI(source, localIntensity)
        setContentView(root)

        // Auto dismiss
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, AUTO_DISMISS_MS)
    }

    private fun buildUI(source: String, localIntensity: SeismicIntensityStatus): View {
        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager

        // Seismic wave animation layer (behind text)
        val waveView = SeismicWaveView(this)
        frame.addView(waveView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val intensityColor = Color.parseColor(colorForLevel(localIntensity.level))
        val intensityStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(4), dp(24), dp(4))

            addView(TextView(context).apply {
                text = "YEREL SARSINTI"
                setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small)
                setTextColor(Color.argb(220, 255, 255, 255))
                gravity = android.view.Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                includeFontPadding = false
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            })

            addView(TextView(context).apply {
                text = String.format("%.1f", localIntensity.score)
                setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large)
                setTextColor(intensityColor)
                gravity = android.view.Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setShadowLayer(18f, 0f, 0f, Color.BLACK)
                includeFontPadding = false
                maxLines = 1
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(4)
            })

            addView(TextView(context).apply {
                text = "${String.format("%.1f", localIntensity.score)} · ${localIntensity.label}"
                setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium)
                setTextColor(intensityColor)
                gravity = android.view.Gravity.CENTER
                setShadowLayer(10f, 0f, 0f, Color.BLACK)
                includeFontPadding = false
                maxLines = 2
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        val intensityParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            topMargin = if (keyguardManager.isKeyguardLocked) dp(112) else dp(40)
        }
        frame.addView(intensityStack, intensityParams)

        // DEPREM text
        val depremText = TextView(this).apply {
            text = "DEPREM"
            setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large)
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setShadowLayer(20f, 0f, 0f, Color.RED)
        }
        val depremParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
            bottomMargin = 80
        }
        frame.addView(depremText, depremParams)

        // Pulsing animation on text
        val pulseAnim = ValueAnimator.ofFloat(1f, 1.15f, 1f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val scale = it.animatedValue as Float
                depremText.scaleX = scale
                depremText.scaleY = scale
            }
        }
        pulseAnim.start()

        // Source / info text
        val infoText = TextView(this).apply {
            text = "Yeni uyarı: $source\n\nYeni deprem bildirimi alındı.\nKapatmak için ekrana dokunun."
            setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small)
            setTextColor(Color.argb(200, 255, 180, 180))
            gravity = android.view.Gravity.CENTER
            setPadding(40, 0, 40, 0)
        }
        val infoParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = 60
        }
        frame.addView(infoText, infoParams)

        if (keyguardManager.isKeyguardLocked) {
            val unlockText = TextView(this).apply {
                text = "Bağlantının tamamlanması için\nlütfen ekran kilidini açın."
                setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium)
                setTextColor(Color.YELLOW)
                gravity = android.view.Gravity.CENTER
                setShadowLayer(10f, 0f, 0f, Color.BLACK)
            }
            val unlockParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                topMargin = 120
            }
            frame.addView(unlockText, unlockParams)

            // Auto-prompt fingerprint/PIN screen
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Handler(Looper.getMainLooper()).postDelayed({
                    keyguardManager.requestDismissKeyguard(this, null)
                }, 1000)
            }
        }

        frame.setOnClickListener {
            if (keyguardManager.isKeyguardLocked && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                keyguardManager.requestDismissKeyguard(this, null)
            } else {
                finish()
            }
        }
        return frame
    }

    private fun readLocalIntensitySnapshot(): SeismicIntensityStatus {
        val score = intent.getFloatExtra(EXTRA_LOCAL_INTENSITY_SCORE, Float.NaN)
        val label = intent.getStringExtra(EXTRA_LOCAL_INTENSITY_LABEL)
        val levelName = intent.getStringExtra(EXTRA_LOCAL_INTENSITY_LEVEL)
        if (!score.isNaN() && !label.isNullOrBlank() && !levelName.isNullOrBlank()) {
            val level = runCatching { SeismicIntensityLevel.valueOf(levelName) }
                .getOrDefault(SeismicIntensityLevel.CALM)
            return SeismicIntensityStatus(score = score, label = label, level = level)
        }
        return SeismicIntensityTracker.getCurrentStatus(this)
    }

    private fun colorForLevel(level: SeismicIntensityLevel): String = when (level) {
        SeismicIntensityLevel.CALM -> "#9E9E9E"
        SeismicIntensityLevel.LIGHT -> "#69F0AE"
        SeismicIntensityLevel.MODERATE -> "#FFEE58"
        SeismicIntensityLevel.STRONG -> "#FFB300"
        SeismicIntensityLevel.SEVERE -> "#FF5252"
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

/**
 * Custom view that draws expanding seismic waves (rings) and radiating lines from center.
 */
class SeismicWaveView(context: Context) : View(context) {

    private data class Wave(var radius: Float, var alpha: Float)

    private val waves = mutableListOf<Wave>()
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val bgPaint = Paint().apply {
        // Radial gradient background — dark center, dark-red edges
        // Will be set after view size is known
    }

    private var animator: ValueAnimator? = null
    private var globalTime = 0f
    private var spawnTimer = 0f

    // 12 radial lines at 30° each
    private val LINE_COUNT = 12
    private val LINE_MAX_FRACTION = 0.85f   // fraction of maxRadius

    // Wave speed: fraction of max-radius per second
    private val WAVE_SPEED = 0.4f
    private val SPAWN_INTERVAL = 0.5f       // new wave every 0.5s

    init {
        startAnimation()
    }

    private fun startAnimation() {
        animator = ValueAnimator.ofFloat(0f, Float.MAX_VALUE).apply {
            duration = Long.MAX_VALUE
            interpolator = LinearInterpolator()
            var lastTime = System.currentTimeMillis()
            addUpdateListener {
                val now = System.currentTimeMillis()
                val dt = (now - lastTime) / 1000f
                lastTime = now
                globalTime += dt
                spawnTimer += dt

                // Spawn new wave
                if (spawnTimer >= SPAWN_INTERVAL) {
                    spawnTimer = 0f
                    waves.add(Wave(0f, 1f))
                }

                val maxR = maxRadius()
                // Update existing waves
                val iter = waves.iterator()
                while (iter.hasNext()) {
                    val w = iter.next()
                    w.radius += WAVE_SPEED * maxR * dt
                    w.alpha = (1f - w.radius / maxR).coerceAtLeast(0f)
                    if (w.alpha <= 0f) iter.remove()
                }

                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun maxRadius(): Float = min(width, height) * 0.9f

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxR = maxRadius()

        // Dark radial background
        val shader = RadialGradient(cx, cy, maxR,
            intArrayOf(Color.parseColor("#220000"), Color.parseColor("#550000"), Color.BLACK),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP)
        bgPaint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Radiating lines
        for (i in 0 until LINE_COUNT) {
            val angle = Math.toRadians((i * 360.0 / LINE_COUNT) + globalTime * 5.0)
            val lineLen = maxR * LINE_MAX_FRACTION
            val endX = cx + cos(angle).toFloat() * lineLen
            val endY = cy + sin(angle).toFloat() * lineLen

            // Gradient: bright near center, fades out
            val lineShader = LinearGradient(cx, cy, endX, endY,
                Color.argb(200, 255, 40, 40),
                Color.argb(0, 200, 0, 0),
                Shader.TileMode.CLAMP)
            linePaint.shader = lineShader
            linePaint.strokeWidth = 2f
            canvas.drawLine(cx, cy, endX, endY, linePaint)
        }

        // Expanding rings
        linePaint.shader = null
        for (wave in waves) {
            wavePaint.alpha = (wave.alpha * 255).toInt()
            wavePaint.strokeWidth = 3f + (1f - wave.alpha) * 4f
            canvas.drawCircle(cx, cy, wave.radius, wavePaint)
        }

        // Bright red center dot
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            val pulse = 0.6f + 0.4f * sin(globalTime * 6f).toFloat()
            color = Color.argb((pulse * 255).toInt(), 255, 60, 60)
            setShadowLayer(30f, 0f, 0f, Color.RED)
        }
        canvas.drawCircle(cx, cy, 24f, dotPaint)
    }
}
