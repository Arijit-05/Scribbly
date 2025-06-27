package com.arijit.notes.utils

import com.google.errorprone.annotations.Keep

@Keep
data class ChecklistItem (
    val text: String = "",
    val isChecked: Boolean = false
)