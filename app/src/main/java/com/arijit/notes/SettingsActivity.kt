package com.arijit.notes

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SettingsActivity : AppCompatActivity() {
    private lateinit var logoutBtn: CardView
    private lateinit var deleteNotesBtn: CardView
    private lateinit var githubBtn: CardView
    private lateinit var arijit: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        logoutBtn = findViewById(R.id.log_out)
        logoutBtn.setOnClickListener {
            vibrate(100)
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Confirm Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes") { dialog, _ ->
                    FirebaseAuth.getInstance().signOut()
                    Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this, AuthenticationActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finishAffinity()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        deleteNotesBtn = findViewById(R.id.delete_all_notes)
        deleteNotesBtn.setOnClickListener {
            vibrate(200)
            showDeleteConfirmationDialog()
        }

        githubBtn = findViewById(R.id.github)
        githubBtn.setOnClickListener {
            vibrate(100)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Arijit-05/Scribbly"))
            startActivity(intent)
        }

        arijit = findViewById(R.id.arijit)
        arijit.setOnClickListener {
            vibrate(100)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://arijit-05.github.io/website/"))
            startActivity(intent)
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate(ms: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            } else {
                vibrator.vibrate(ms) // Vibrate for 50 milliseconds
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete All Notes")
            .setMessage("Are you sure you want to delete all your notes? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteAllNotes()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAllNotes() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("notes")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val batch = FirebaseFirestore.getInstance().batch()
                for (document in querySnapshot.documents) {
                    batch.delete(document.reference)
                }
                batch.commit().addOnSuccessListener {
                    Toast.makeText(this, "All notes deleted", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    Toast.makeText(this, "Failed to delete notes", Toast.LENGTH_SHORT).show()
                }
            }
    }
}