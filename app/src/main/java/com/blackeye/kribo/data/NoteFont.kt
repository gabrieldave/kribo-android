package com.blackeye.kribo.data

/** Per-note editorial font (#42). Stored as a name, defaulting to SANS for older notes. */
enum class NoteFont {
    SANS,
    SERIF,
    MONO,
    ;

    companion object {
        fun fromName(name: String?): NoteFont =
            entries.firstOrNull { it.name == name } ?: SANS
    }
}
