package com.arijit.notes.utils

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.arijit.notes.R
import com.google.firebase.firestore.FirebaseFirestore

class NoteAdapter(
    private var notes: List<Note>,
    private val onNoteClick: (Note) -> Unit,
    private val onNoteDeleted: () -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.note_title)
        val content: TextView = itemView.findViewById(R.id.note_content)
        val checklistLabel: TextView = itemView.findViewById(R.id.checklist_label)
        val card: CardView = itemView as CardView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.note_item, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]

        val hasChecklist = !note.checkListJson.isNullOrBlank() && note.checkListJson != "[]"

        if (hasChecklist) {
            holder.checklistLabel.visibility = View.VISIBLE
        } else {
            holder.checklistLabel.visibility = View.GONE
        }

        // Title visibility
        if (note.title.isNullOrBlank()) {
            holder.title.visibility = View.GONE
        } else {
            holder.title.visibility = View.VISIBLE
            holder.title.text = note.title
        }

        // Content visibility
        if (note.content.isNullOrBlank()) {
            holder.content.visibility = View.GONE
        } else {
            holder.content.visibility = View.VISIBLE
            holder.content.text = note.content
        }

        // Background color
        val bgColor = try {
            if (note.backgroundColor.isNullOrBlank()) Color.parseColor("#FFFFFF")
            else Color.parseColor(note.backgroundColor)
        } catch (e: Exception) {
            Color.parseColor("#FFFFFF")
        }
        holder.card.setCardBackgroundColor(bgColor)

        // Note click
        holder.itemView.setOnClickListener {
            onNoteClick(note)
        }

        // Long press to delete
        holder.itemView.setOnLongClickListener {
            showDeleteDialog(holder.itemView.context, note)
            true
        }
    }

    private fun showDeleteDialog(context: Context, note: Note) {
        vibrate(context)
        
        AlertDialog.Builder(context)
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to delete this note? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteNoteFromFirebase(context, note)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            } else {
                vibrator.vibrate(300) // Vibrate for 300 milliseconds
            }
        }
    }

    private fun deleteNoteFromFirebase(context: Context, note: Note) {
        val db = FirebaseFirestore.getInstance()
        
        db.collection("notes")
            .document(note.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(context, "Note deleted successfully", Toast.LENGTH_SHORT).show()
                onNoteDeleted() // Notify the activity to refresh the notes list
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to delete note: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun getItemCount(): Int = notes.size

    fun updateNotes(newNotes: List<Note>) {
        notes = newNotes
        notifyDataSetChanged()
    }
}
