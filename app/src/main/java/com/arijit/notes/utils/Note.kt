package com.arijit.notes.utils

import com.google.firebase.firestore.Exclude

data class Note (
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val isPinned: Boolean = false,
    val backgroundColor: String = "#bf625c",
    val labels: List<String>?= null,
    val timeStamp: Long = System.currentTimeMillis(),
    @get:Exclude var checkListJson: String? = null, // This is NOT saved to Firestore
    @get:Exclude var hasChecklist: Boolean = false
)