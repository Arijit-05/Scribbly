package com.arijit.notes

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.arijit.notes.utils.Note
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SettingsActivity : AppCompatActivity() {
    private lateinit var logoutBtn: CardView
    private lateinit var deleteNotesBtn: CardView
    private lateinit var githubBtn: CardView
    private lateinit var exportNotesBtn: CardView
    private lateinit var importNotesBtn: CardView
    private lateinit var arijit: TextView
    private val EXPORT_REQUEST_CODE = 201
    private val IMPORT_REQUEST_CODE = 202

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    101
                )
            }
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

        exportNotesBtn = findViewById(R.id.export_notes)
        exportNotesBtn.setOnClickListener {
            vibrate(100)
            exportNotesWithSAF()
        }

        importNotesBtn = findViewById(R.id.import_notes)
        importNotesBtn.setOnClickListener {
            vibrate(100)
            importNotesWithSAF()
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun exportNotesWithSAF() {
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formatted = current.format(formatter)

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "notes_backup_${formatted}.json")
        }
        startActivityForResult(intent, EXPORT_REQUEST_CODE)
    }

    private fun importNotesWithSAF() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, IMPORT_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EXPORT_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                exportNotesToUri(uri)
            }
        } else if (requestCode == IMPORT_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                // (Optional) handle import here
            }
        }
    }

    private fun exportNotesToUri(uri: android.net.Uri) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("notes")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                val notes = documents.map { it.toObject(Note::class.java) }
                val json = Gson().toJson(notes)
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                    Toast.makeText(this, "Notes exported successfully!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to export notes", Toast.LENGTH_SHORT).show()
            }
    }
}