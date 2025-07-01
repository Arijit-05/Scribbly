package com.arijit.notes

import android.Manifest
import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.animation.AnimatorListenerAdapter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.arijit.notes.utils.Note
import com.arijit.notes.utils.NoteAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Rect
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.RelativeLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.airbnb.lottie.LottieAnimationView
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.gson.Gson
import kotlin.collections.emptyList

class MainActivity : AppCompatActivity() {
    private lateinit var notesRecyclerView: RecyclerView
    private lateinit var addNoteBtn: CardView
    private lateinit var searchHintText: TextView
    private lateinit var settingsIcon: ImageView
    private lateinit var header: RelativeLayout
    private lateinit var searchBar: EditText
    private val phrases = mutableListOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private val fadeDuration = 500L
    private val labelList = mutableListOf("All Notes")
    private lateinit var labelContainer: LinearLayout
    private lateinit var addLabelBtn: TextView
    private var selectedLabel: String = "All Notes"
    private lateinit var noteAdapter: NoteAdapter
    private val allNotes = mutableListOf<Note>()
    private lateinit var addNoteLauncher: ActivityResultLauncher<Intent>
    private var nightMode: Boolean = false
    // Request code constants for SAF
    private val EXPORT_REQUEST_CODE = 201
    private val IMPORT_REQUEST_CODE = 202

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Only request WRITE_EXTERNAL_STORAGE for Android 9 and below
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

        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            nightMode = true
        } else
            nightMode = false

        addNoteBtn = findViewById(R.id.add_btn)
        searchHintText = findViewById(R.id.search_hint_text)
        settingsIcon = findViewById(R.id.settings_btn)
        addLabelBtn = findViewById(R.id.add_label_btn)
        labelContainer = findViewById(R.id.label_container)
        notesRecyclerView = findViewById(R.id.notes_recycler_view)
        header = findViewById(R.id.header)
        searchBar = findViewById(R.id.search_bar)
        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        val lottieRefresh = findViewById<LottieAnimationView>(R.id.lottie_refresh)

        swipeRefreshLayout.setOnRefreshListener {
            lottieRefresh.visibility = View.VISIBLE
            lottieRefresh.playAnimation()
            fetchNotes() // your existing function
            Handler(Looper.getMainLooper()).postDelayed({
                swipeRefreshLayout.isRefreshing = false
                lottieRefresh.cancelAnimation()
                lottieRefresh.visibility = View.GONE
            }, 1500)
            Toast.makeText(this, "Notes refreshed", Toast.LENGTH_SHORT).show()
        }

        // Register the launcher for AddNoteActivity
        addNoteLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                fetchNotes()
            }

        header.setOnClickListener {
            searchBar.visibility = View.VISIBLE
            searchBar.requestFocus()
            showKeyboard(searchBar)
            vibrate()
        }

        searchBar.addTextChangedListener {
            val query = it.toString().lowercase()
            val filteredNotes = if (query.isEmpty()) {
                allNotes
            } else {
                allNotes.filter { note ->
                    note.title.lowercase().contains(query) ||
                            note.content.lowercase().contains(query) ||
                            (note.labels?.any { label -> label.lowercase().contains(query) } ?: false)
                }
            }
            noteAdapter.updateNotes(filteredNotes)
        }

        noteAdapter = NoteAdapter(allNotes, { note: Note ->
            val doc = FirebaseFirestore.getInstance().collection("notes").document(note.id)
            doc.get().addOnSuccessListener { snapshot ->
                val checklistField = snapshot.get("checkList")
                val checklistJson: String = when (checklistField) {
                    is String -> checklistField
                    is List<*> -> Gson().toJson(checklistField)
                    else -> "[]"
                }
                val intent = Intent(this, AddNoteActivity::class.java).apply {
                    putExtra("noteId", note.id)
                    putExtra("title", note.title)
                    putExtra("content", note.content)
                    putExtra("isPinned", note.isPinned)
                    putExtra("backgroundColor", note.backgroundColor)
                    putStringArrayListExtra("labels", ArrayList(note.labels ?: emptyList()))
                    putExtra("checkListJson", checklistJson)
                }
                addNoteLauncher.launch(intent)
            }
        }, {
            // onNoteDeleted callback - refresh the notes list
            fetchNotesFromFirebase()
        })

        notesRecyclerView.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        notesRecyclerView.adapter = noteAdapter

        val spacingInPx = (8 * resources.displayMetrics.density).toInt()
        notesRecyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val position = parent.getChildAdapterPosition(view)
                val column = position % 2
                outRect.left = if (column == 1) spacingInPx / 2 else spacingInPx
                outRect.right = if (column == 0) spacingInPx / 2 else spacingInPx
                outRect.top = spacingInPx
                outRect.bottom = spacingInPx
            }
        })

        addLabelBtn.setOnClickListener {
            vibrate()
            val input = EditText(this)
            AlertDialog.Builder(this)
                .setTitle("Add label")
                .setView(input)
                .setPositiveButton("Add") { _, _ ->
                    val label = input.text.toString().trim()
                    if (label.isNotEmpty() && !labelList.contains(label)) {
                        labelList.add(label)
                        renderLabels()
                        saveLabelsToFirebase()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        addNoteBtn.setOnClickListener {
            vibrate()
            addNoteLauncher.launch(Intent(this@MainActivity, AddNoteActivity::class.java))
        }

        settingsIcon.setOnClickListener {
            vibrate()
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            // Set welcome note in search bar
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .get().addOnSuccessListener { document ->
                    val name = document.getString("name") ?: "User"
                    phrases.clear()
                    phrases.add("Welcome back, $name!")
                    phrases.add("Search your notes")
                    animateTextOnce()
                }

            // Fetch notes from Firebase
            fetchNotesFromFirebase()

            // Fetch labels
            FirebaseFirestore.getInstance().collection("labels")
                .document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    val labelsFromFirebase = doc.get("labels") as? List<String> ?: emptyList()
                    labelList.clear()
                    labelList.add("All Notes")
                    labelList.addAll(labelsFromFirebase)
                    renderLabels()
                }

        }

        FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()

    }

    override fun onResume() {
        super.onResume()
    }

    private fun renderLabels() {
        labelContainer.removeAllViews()
        labelList.forEachIndexed { index, label ->
            val labelView = TextView(this).apply {
                text = label
                if (nightMode) setTextColor(getResources().getColor(R.color.white))
                else setTextColor(getResources().getColor(R.color.black))
                setPadding(40, 20, 40, 20)
                textSize = 12f
                typeface = ResourcesCompat.getFont(this@MainActivity, R.font.poppins_regular)
                setBackgroundResource(
                    if (label == selectedLabel) {
                        if (!nightMode) R.drawable.label_bg_solid
                        else R.drawable.label_bg_solid_dark
                    } else {
                        if (!nightMode) R.drawable.label_bg_outline
                        else R.drawable.label_bg_outline_dark
                    }
                )

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                if (index != 0) params.marginStart = dpToPx(10)
                layoutParams = params

                setOnClickListener {
                    selectedLabel = label
                    renderLabels() // Refresh visuals
                    filterNotesByLabel(label)
                }

                if (label != "All Notes") {
                    setOnLongClickListener {
                        val options = arrayOf("Edit", "Delete")
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Choose Action")
                            .setItems(options) { dialog, which ->
                                when (which) {
                                    0 -> { // Edit
                                        val editText = EditText(this@MainActivity).apply {
                                            setText(label)
                                        }
                                        AlertDialog.Builder(this@MainActivity)
                                            .setTitle("Edit Label")
                                            .setView(editText)
                                            .setPositiveButton("Save") { _, _ ->
                                                val newLabel = editText.text.toString().trim()
                                                if (newLabel.isNotEmpty() && newLabel != label) {
                                                    labelList[index] = newLabel
                                                    if (selectedLabel == label) selectedLabel = newLabel
                                                    renderLabels()
                                                    filterNotesByLabel(selectedLabel)
                                                    saveLabelsToFirebase()
                                                }
                                            }
                                            .setNegativeButton("Cancel", null)
                                            .show()
                                    }

                                    1 -> { // Delete
                                        AlertDialog.Builder(this@MainActivity)
                                            .setTitle("Delete Label")
                                            .setMessage("Do you want to delete '$label'?")
                                            .setPositiveButton("Delete") { _, _ ->
                                                labelList.removeAt(index)
                                                if (selectedLabel == label) selectedLabel = "All Notes"
                                                renderLabels()
                                                filterNotesByLabel(selectedLabel)
                                                saveLabelsToFirebase()
                                            }
                                            .setNegativeButton("Cancel", null)
                                            .show()
                                    }
                                }
                            }
                            .show()
                        true
                    }
                }
            }
            labelContainer.addView(labelView)
        }
    }

    private fun saveLabelsToFirebase() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val labelsToSave = labelList.filter { it != "All Notes" }

        FirebaseFirestore.getInstance().collection("labels")
            .document(uid)
            .set(mapOf("labels" to labelsToSave))
    }

    private fun dpToPx(dp: Int): Int {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    private fun filterNotesByLabel(label: String) {
        val filtered = if (label == "All Notes") {
            allNotes
        } else {
            vibrate()
            allNotes.filter { it.labels?.contains(label) == true }
        }
        noteAdapter.updateNotes(filtered)
    }

    private fun animateTextOnce() {
        searchHintText.text = phrases[0]
        searchHintText.alpha = 0f

        val fadeInHello = ObjectAnimator.ofFloat(searchHintText, "alpha", 0f, 1f).apply {
            duration = fadeDuration
        }

        val fadeOutHello = ObjectAnimator.ofFloat(searchHintText, "alpha", 1f, 0f).apply {
            duration = fadeDuration
        }

        val fadeInSearch = ObjectAnimator.ofFloat(searchHintText, "alpha", 0f, 1f).apply {
            duration = fadeDuration
        }

        fadeInHello.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                handler.postDelayed({
                    fadeOutHello.start()
                }, 1200)
            }
        })

        fadeOutHello.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                searchHintText.text = phrases[1]
                fadeInSearch.start()
            }
        })

        fadeInHello.start()
    }

    private fun fetchNotes() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance().collection("notes")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val notes = querySnapshot.documents.map { doc ->
                    val note = doc.toObject(Note::class.java)!!.copy(id = doc.id)
                    val checklistField = doc.get("checkList")
                    val checklistJson: String = when (checklistField) {
                        is String -> checklistField
                        is List<*> -> Gson().toJson(checklistField)
                        else -> "[]"
                    }
                    note.checkListJson = checklistJson
                    note
                }
                    .sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.timeStamp })

                allNotes.clear()
                allNotes.addAll(notes)
                noteAdapter.updateNotes(allNotes)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load notes", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchNotesFromFirebase() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        FirebaseFirestore.getInstance().collection("notes")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val notes = querySnapshot.documents.map { doc ->
                    val note = doc.toObject(Note::class.java)!!.copy(id = doc.id)
                    val checklistField = doc.get("checkList")
                    val checklistJson: String = when (checklistField) {
                        is String -> checklistField
                        is List<*> -> Gson().toJson(checklistField)
                        else -> "[]"
                    }
                    note.checkListJson = checklistJson
                    note
                }
                    .sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.timeStamp })

                allNotes.clear()
                allNotes.addAll(notes)
                noteAdapter.updateNotes(allNotes)
            }
    }

    override fun onBackPressed() {
        if (searchBar.visibility == View.VISIBLE) {
            searchBar.setText("")
            searchBar.visibility = View.GONE
            hideKeyboard()
            noteAdapter.updateNotes(allNotes) // Restore full list
        } else {
            super.onBackPressed()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Storage permission denied. Some features may not work.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchBar.windowToken, 0)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun Context.vibrate() {
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

    // Call this to export notes (e.g., from a button)
    private fun exportNotesWithSAF() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "notes_backup.json")
        }
        startActivityForResult(intent, EXPORT_REQUEST_CODE)
    }

    // Call this to import notes (e.g., from a button)
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
                // Export allNotes as JSON
                val notesJson = Gson().toJson(allNotes)
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(notesJson.toByteArray())
                    Toast.makeText(this, "Notes exported successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (requestCode == IMPORT_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val json = inputStream.bufferedReader().use { it.readText() }
                    // Parse and import notes (replace or merge as needed)
                    val importedNotes = Gson().fromJson(json, Array<Note>::class.java).toList()
                    allNotes.clear()
                    allNotes.addAll(importedNotes)
                    noteAdapter.updateNotes(allNotes)
                    Toast.makeText(this, "Notes imported successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
