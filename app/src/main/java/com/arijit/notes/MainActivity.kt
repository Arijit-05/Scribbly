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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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

            // Fetch notes from uid
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
        // fetchNotes() // No longer needed here
    }

    private fun renderLabels() {
        labelContainer.removeAllViews()
        labelList.forEachIndexed { index, label ->
            val labelView = TextView(this).apply {
                text = label
                setPadding(40, 20, 40, 20)
                textSize = 12f
                typeface = ResourcesCompat.getFont(this@MainActivity, R.font.poppins_regular)
                setBackgroundResource(
                    if (label == selectedLabel) R.drawable.label_bg_solid
                    else R.drawable.label_bg_outline
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
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Delete Label")
                            .setMessage("Do you want to delete '$label'?")
                            .setPositiveButton("Delete") { _, _ ->
                                labelList.remove(label)
                                if (selectedLabel == label) selectedLabel = "All Notes"
                                renderLabels()
                                filterNotesByLabel(selectedLabel)
                                saveLabelsToFirebase()
                            }
                            .setNegativeButton("Cancel", null)
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
}
