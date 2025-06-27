package com.arijit.notes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.Log.e
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.airbnb.lottie.LottieAnimationView
import com.arijit.notes.fragments.LoginFragment
import com.arijit.notes.fragments.RegisterFragment
import com.arijit.notes.utils.AuthListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.core.view.isVisible

class AuthenticationActivity : AppCompatActivity(), AuthListener {
    private lateinit var image: ImageView
    private lateinit var selectorTxt: TextView
    private lateinit var frameLayout: FrameLayout
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var loadingAnim: LottieAnimationView
    private var firstTime = true
    var register: Boolean = true
    var login: Boolean = false
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_authentication)
        auth = FirebaseAuth.getInstance()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        image = findViewById(R.id.image)
        selectorTxt = findViewById(R.id.selector_txt)
        frameLayout = findViewById(R.id.frame_layout)
        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingAnim = findViewById(R.id.loading_animation)

        loadFragment()
    }

    fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            loadingAnim.playAnimation()
        } else {
            loadingAnim.cancelAnimation()
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect =
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            } else {
                vibrator.vibrate(50) // Vibrate for 50 milliseconds
            }
        }
    }

    private fun loadFragment() {
        val duration = 300L

        if (firstTime) {
            // Skip fade out — just fade in directly after setting initial content
            if (register) {
                login = false
                image.setImageResource(R.drawable.register)
                selectorTxt.text = "Already have an account?"
                selectorTxt.setOnClickListener {
                    vibrate()
                    register = false
                    login = true
                    loadFragment()
                }
                supportFragmentManager.beginTransaction()
                    .replace(R.id.frame_layout, RegisterFragment())
                    .commit()
            } else {
                register = false
                image.setImageResource(R.drawable.login)
                selectorTxt.text = "New user? Create an account!"
                selectorTxt.setOnClickListener {
                    vibrate()
                    register = true
                    login = false
                    loadFragment()
                }
                supportFragmentManager.beginTransaction()
                    .replace(R.id.frame_layout, LoginFragment())
                    .commit()
            }

            // Fade in once after content is set
            selectorTxt.animate().alpha(1f).setDuration(duration).start()
            image.animate().alpha(1f).setDuration(duration).start()

            firstTime = false
            return
        }

        // Regular fade-out + swap + fade-in for later transitions
        selectorTxt.animate().alpha(0f).setDuration(duration).start()
        image.animate().alpha(0f).setDuration(duration).withEndAction {
            if (register) {
                login = false
                image.setImageResource(R.drawable.register)
                selectorTxt.text = "Already have an account?"
                selectorTxt.setOnClickListener {
                    vibrate()
                    register = false
                    login = true
                    loadFragment()
                }
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                    .replace(R.id.frame_layout, RegisterFragment())
                    .commit()
            } else {
                register = false
                image.setImageResource(R.drawable.login)
                selectorTxt.text = "New user? Create an account!"
                selectorTxt.setOnClickListener {
                    vibrate()
                    register = true
                    login = false
                    loadFragment()
                }
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                    .replace(R.id.frame_layout, LoginFragment())
                    .commit()
            }

            selectorTxt.animate().alpha(1f).setDuration(duration).start()
            image.animate().alpha(1f).setDuration(duration).start()
        }.start()
    }

    override fun onRegisterSubmit(name: String, email: String, pin: String) {
        showLoading(true) // Show loader

        val paddedPin = "##$pin##"
        auth.createUserWithEmailAndPassword(email, paddedPin)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val uid = user?.uid
                    val db = FirebaseFirestore.getInstance()
                    val userData = hashMapOf("name" to name, "email" to email)

                    uid?.let {
                        db.collection("users").document(uid).set(userData)
                            .addOnSuccessListener {
                                showLoading(false) // hide on success
                                Toast.makeText(this, "Name saved!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@AuthenticationActivity, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener {
                                showLoading(false)
                                Toast.makeText(this, "Failed to save name", Toast.LENGTH_LONG).show()
                            }
                    }
                } else {
                    showLoading(false)
                    Toast.makeText(this, "Registration failed ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onLoginSubmit(email: String, pin: String) {
        showLoading(true)
        val paddedPin = "##$pin##"
        auth.signInWithEmailAndPassword(email, paddedPin)
            .addOnCompleteListener { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    Toast.makeText(this, "Logged in successfully!", Toast.LENGTH_SHORT).show()
                    val user = auth.currentUser
                    startActivity(Intent(this@AuthenticationActivity, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        if (loadingOverlay.isVisible) {
            return
        }
        super.onBackPressed()
    }

}
