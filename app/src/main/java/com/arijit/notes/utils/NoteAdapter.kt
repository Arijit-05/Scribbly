package com.arijit.notes.utils

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.arijit.notes.R

class NoteAdapter(
    private var notes: List<Note>,
    private val onNoteClick: (Note) -> Unit
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
    }

    override fun getItemCount(): Int = notes.size

    fun updateNotes(newNotes: List<Note>) {
        notes = newNotes
        notifyDataSetChanged()
    }
}
