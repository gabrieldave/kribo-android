package com.blackeye.kribo.ui.note

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackeye.kribo.data.NoteRepository
import kotlinx.coroutines.launch

class NoteEditorViewModel(
    private val repository: NoteRepository,
    private val noteId: Long?,
) : ViewModel() {

    var content by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(noteId != null)
        private set

    private var createdAt: Long? = null

    init {
        if (noteId != null) {
            viewModelScope.launch {
                repository.getNote(noteId)?.let { note ->
                    content = note.content
                    createdAt = note.createdAt
                }
                isLoading = false
            }
        }
    }

    val canSave: Boolean
        get() = content.isNotBlank() && !isLoading

    fun onContentChange(value: String) {
        content = value
    }

    fun save(onSaved: () -> Unit) {
        val text = content.trim()
        if (text.isEmpty() || isLoading) return
        viewModelScope.launch {
            if (noteId == null) {
                repository.insertNote(text)
            } else {
                repository.updateNote(
                    id = noteId,
                    content = text,
                    createdAt = createdAt ?: System.currentTimeMillis(),
                )
            }
            onSaved()
        }
    }
}
