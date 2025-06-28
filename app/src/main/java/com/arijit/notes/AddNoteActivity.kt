package com.arijit.notes

import android.Manifest
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.arijit.notes.utils.ChecklistItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder

class AddNoteActivity : AppCompatActivity() {
    private lateinit var closeBtn: ImageView
    private lateinit var pinBtn: ImageView
    private lateinit var titleTxt: TextView
    private lateinit var noteTxt: TextView
    private lateinit var addCheckList: ImageView
    private lateinit var addLabel: TextView
    private lateinit var addColor: ImageView
    private var pin: Boolean = false
    private var selectedColor: String = ""
    private val selectedLabels = mutableListOf<String>()
    private lateinit var checklistContainer: LinearLayout
    private val checklistItems = mutableListOf<ChecklistItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_note)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val labelsFromIntent = intent.getStringArrayListExtra("labels") ?: arrayListOf()
        selectedLabels.clear()
        selectedLabels.addAll(labelsFromIntent)

        val noteId = intent.getStringExtra("noteId")
        val title = intent.getStringExtra("title")
        val content = intent.getStringExtra("content")
        val isPinned = intent.getBooleanExtra("isPinned", false)
        val backgroundColor = intent.getStringExtra("backgroundColor") ?: "#FFFFFF"

        closeBtn = findViewById(R.id.close_btn)
        titleTxt = findViewById(R.id.title_txt)
        noteTxt = findViewById(R.id.note_txt)
        pinBtn = findViewById(R.id.pin_btn)
        addCheckList = findViewById(R.id.add_checklist)
        addLabel = findViewById(R.id.add_label_btn)
        addColor = findViewById(R.id.add_color)
        checklistContainer = findViewById(R.id.checklist_container)

        titleTxt.text = title
        noteTxt.text = content
        pin = isPinned
        pinBtn.setImageResource(if (pin) R.drawable.pin_filled else R.drawable.pin)

        val defaultColors = listOf(
            "#b9c7d9", "#d9bbb9", "#d7d9b9", "#d9cbb9", "#b1ffda",
            "#c1f1ec", "#f6a1a5", "#f05a5f", "#f3b18a", "#c97e5e", "#c9fced",
            "#c9f2fc", "#ffb4ae", "#ff958c", "#ff5497", "#ffca87", "#ffe7d7",
            "#ccf6e3", "#eefcf6", "#ff609d"
        )

        // Set background color based on note's color
        if (noteId != null) {
            // Existing note: use its current background color
            findViewById<View>(R.id.main).setBackgroundColor(Color.parseColor(backgroundColor))
        } else {
            // New note: assign a random color and set it as background
            val randomColor = defaultColors.random()
            findViewById<View>(R.id.main).setBackgroundColor(Color.parseColor(randomColor))
            selectedColor = randomColor // Store the selected color for saving
        }

        // Parse checklist from JSON
        val checklistJson = intent.getStringExtra("checkListJson")
        if (!checklistJson.isNullOrEmpty()) {
            val type = object :
                com.google.gson.reflect.TypeToken<List<com.arijit.notes.utils.ChecklistItem>>() {}.type
            val items: List<com.arijit.notes.utils.ChecklistItem> =
                com.google.gson.Gson().fromJson(checklistJson, type)
            checklistItems.clear()
            checklistItems.addAll(items)
            for (item in items) {
                addChecklistView(item)
            }
        }

        addLabel.setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            FirebaseFirestore.getInstance().collection("labels")
                .document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    val labels = doc.get("labels") as? List<String> ?: emptyList()
                    if (labels.isEmpty()) {
                        Toast.makeText(
                            this,
                            "No labels found! Add from main screen",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        showLabelDropdown(labels)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to fetch labels", Toast.LENGTH_SHORT).show()
                }
        }

        addColor.setOnClickListener {
            vibrate()
            val initial =
                if (selectedColor.isNotEmpty()) Color.parseColor(selectedColor) else R.color.default_note

            val dialog = com.flask.colorpicker.builder.ColorPickerDialogBuilder.with(this)
                .setTitle("Pick a color")
                .initialColor(initial)
                .wheelType(com.flask.colorpicker.ColorPickerView.WHEEL_TYPE.FLOWER)
                .density(12)
                .setPositiveButton("OK") { _, color, _ ->
                    selectedColor = String.format("#%06X", 0xFFFFFF and color)
                    pinBtn.setColorFilter(color) // indicate selection
                    Toast.makeText(this, "Color chosen!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .build()

            dialog.show()
        }

        closeBtn.setOnClickListener {
            vibrate()
            saveNoteAndFinish()
        }

        pinBtn.setOnClickListener {
            vibrate()
            togglePin()
        }

        addCheckList.setOnClickListener {
            showChecklistItemDialog()
        }
    }

    private fun showChecklistItemDialog() {
        val input = EditText(this)
        input.hint = "Enter checklist item"

        AlertDialog.Builder(this)
            .setTitle("Add Checklist Item")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val itemText = input.text.toString().trim()
                if (itemText.isNotEmpty()) {
                    val item = ChecklistItem(itemText, false)
                    checklistItems.add(item)
                    addChecklistView(item)
                    Toast.makeText(
                        this,
                        "Press the checklist button again to add more items!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addChecklistView(item: com.arijit.notes.utils.ChecklistItem) {
        val checkBox = CheckBox(this)
        checkBox.text = item.text
        checkBox.isChecked = item.isChecked

        checkBox.setTextColor(ContextCompat.getColor(this, R.color.black))
        checkBox.buttonTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.black))

        // Long press to delete checklist item
        checkBox.setOnLongClickListener {
            vibrate()
            AlertDialog.Builder(this)
                .setTitle("Delete Checklist Item")
                .setMessage("Are you sure you want to delete this item? This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    // Remove from checklistItems and UI
                    val index = checklistContainer.indexOfChild(checkBox)
                    if (index != -1) {
                        checklistItems.removeAt(index)
                        checklistContainer.removeViewAt(index)
                        // If editing an existing note, update Firebase
                        if (intent.getStringExtra("noteId") != null) {
                            saveNoteAndFinish()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        checklistContainer.addView(checkBox)
    }

    private fun togglePin(): Boolean {
        pin = !pin

        if (pin) {
            pinBtn.setImageResource(R.drawable.pin_filled)
            Toast.makeText(this, "Note pinned", Toast.LENGTH_SHORT).show()
        } else {
            pinBtn.setImageResource(R.drawable.pin)
            Toast.makeText(this, "Not unpinned", Toast.LENGTH_SHORT).show()
        }

        return pin
    }

    private fun showLabelDropdown(labels: List<String>) {
        val selected = BooleanArray(labels.size) { index ->
            selectedLabels.contains(labels[index])
        }

        AlertDialog.Builder(this)
            .setTitle("Select Labels")
            .setMultiChoiceItems(labels.toTypedArray(), selected) { _, which, isChecked ->
                if (isChecked) {
                    selectedLabels.add(labels[which])
                } else {
                    selectedLabels.remove(labels[which])
                }
            }
            .setPositiveButton("Done", null)
            .show()
    }


    private fun saveNoteAndFinish() {
        val defaultColors = listOf(
            "#b9c7d9", "#d9bbb9", "#d7d9b9", "#d9cbb9", "#b1ffda",
            "#c1f1ec", "#f6a1a5", "#f05a5f", "#f3b18a", "#c97e5e", "#c9fced",
            "#c9f2fc", "#ffb4ae", "#ff958c", "#ff5497", "#ffca87", "#ffe7d7",
            "#ccf6e3", "#eefcf6", "#ff609d"
        )

        val title = titleTxt.text.toString().trim()
        val content = noteTxt.text.toString().trim()
        val noteId = intent.getStringExtra("noteId")
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Collect checklist state from UI
        checklistItems.clear()
        for (i in 0 until checklistContainer.childCount) {
            val checkBox = checklistContainer.getChildAt(i) as? CheckBox ?: continue
            checklistItems.add(
                com.arijit.notes.utils.ChecklistItem(
                    checkBox.text.toString(),
                    checkBox.isChecked
                )
            )
        }

        val gson = com.google.gson.Gson()
        val checklistJson = gson.toJson(checklistItems)

        if (title.isEmpty() && content.isEmpty() && checklistItems.isEmpty()) {
            finish()
            return
        }

        if (noteId != null) {
            // Editing an existing note: only update if something changed
            val originalTitle = intent.getStringExtra("title") ?: ""
            val originalContent = intent.getStringExtra("content") ?: ""
            val originalIsPinned = intent.getBooleanExtra("isPinned", false)
            val originalColor = intent.getStringExtra("backgroundColor") ?: "#FFFFFF"
            val newColor = if (selectedColor.isNotEmpty()) selectedColor else originalColor
            val originalLabels = intent.getStringArrayListExtra("labels") ?: arrayListOf()
            val originalChecklistJson = intent.getStringExtra("checkListJson")
            val type = object :
                com.google.gson.reflect.TypeToken<List<com.arijit.notes.utils.ChecklistItem>>() {}.type
            val originalChecklist: List<com.arijit.notes.utils.ChecklistItem> =
                if (!originalChecklistJson.isNullOrEmpty()) com.google.gson.Gson()
                    .fromJson(originalChecklistJson, type) else emptyList()

            val changed = title != originalTitle ||
                    content != originalContent ||
                    pin != originalIsPinned ||
                    newColor != originalColor ||
                    selectedLabels.toSet() != originalLabels.toSet() ||
                    checklistItems != originalChecklist

            if (!changed) {
                finish()
                return
            }

            val noteMap = mapOf(
                "title" to title,
                "content" to content,
                "backgroundColor" to newColor,
                "isPinned" to pin,
                "timeStamp" to System.currentTimeMillis(),
                "userId" to userId,
                "labels" to selectedLabels,
                "checkList" to checklistJson
            )

            FirebaseFirestore.getInstance()
                .collection("notes")
                .document(noteId)
                .update(noteMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Note updated", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to update note", Toast.LENGTH_SHORT).show()
                }
        } else {
            val noteMap = mapOf(
                "title" to title,
                "content" to content,
                "backgroundColor" to (if (selectedColor.isNotEmpty()) selectedColor else defaultColors.random()),
                "isPinned" to pin,
                "timeStamp" to System.currentTimeMillis(),
                "userId" to userId,
                "labels" to selectedLabels,
                "checkList" to checklistJson
            )

            FirebaseFirestore.getInstance()
                .collection("notes")
                .add(noteMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to save note", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        saveNoteAndFinish()
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
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