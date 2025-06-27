package com.arijit.notes

import android.content.Context
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Vibrator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth

class WelcomeActivity : AppCompatActivity() {
    private lateinit var nextBtn: CardView
    private lateinit var typewriterTextView: TextView
    private val phrases = listOf(
        "Write your ideas",
        "Store your thoughts",
        "Track your to-dos",
        "Organize your day",
        "Start something big"
    )

    private var phraseIndex = 0
    private var charIndex = 0

    private val typingDelay: Long = 100
    private val pauseBetweenPhrases: Long = 1000
    private val handler = Handler(Looper.getMainLooper())
    private val blinkHandler = Handler(Looper.getMainLooper())

    private var showCursor = true
    private var isTyping = false
    private var isTransitioning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_welcome)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        nextBtn = findViewById(R.id.next)
        typewriterTextView = findViewById(R.id.typewriter_txt)

        startCursorBlink()
        startTyping()

        nextBtn.setOnClickListener {
            vibrate()
            startActivity(Intent(this@WelcomeActivity, AuthenticationActivity::class.java))
            finish()
        }
    }

    private fun startCursorBlink() {
        blinkHandler.post(object : Runnable {
            override fun run() {
                if (isTyping && !isTransitioning) {
                    showCursor = !showCursor
                    val currentPhrase = phrases[phraseIndex]
                    val visibleText = currentPhrase.substring(0, charIndex.coerceAtMost(currentPhrase.length))
                    val cursor = if (showCursor) "|" else " "
                    typewriterTextView.text = visibleText + cursor
                }
                blinkHandler.postDelayed(this, 500)
            }
        })
    }

    private fun startTyping() {
        isTyping = true
        isTransitioning = false

        val currentPhrase = phrases[phraseIndex]

        if (charIndex <= currentPhrase.length) {
            val cursor = if (showCursor) "|" else " "
            typewriterTextView.text = currentPhrase.substring(0, charIndex) + cursor
            charIndex++
            handler.postDelayed({ startTyping() }, typingDelay)
        } else {
            isTyping = false
            handler.postDelayed({ startFadeOut() }, pauseBetweenPhrases)
        }
    }

    private fun startFadeOut() {
        isTransitioning = true

        val fadeOut = AlphaAnimation(1.0f, 0.0f).apply {
            duration = 500
            fillAfter = true
        }

        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                // Move to next phrase
                phraseIndex = (phraseIndex + 1) % phrases.size
                charIndex = 0

                startFadeIn()
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })

        typewriterTextView.startAnimation(fadeOut)
    }

    private fun startFadeIn() {
        val fadeIn = AlphaAnimation(0.0f, 1.0f).apply {
            duration = 500
            fillAfter = true
        }

        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                startTyping()
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })

        typewriterTextView.startAnimation(fadeIn)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            } else {
                vibrator.vibrate(50) // Vibrate for 50 milliseconds
            }
        }
    }
}
