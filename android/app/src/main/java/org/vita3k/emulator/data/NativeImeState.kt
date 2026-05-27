package org.vita3k.emulator.data

data class NativeImeState(
    val sceImeActive: Boolean,
    val dialogActive: Boolean,
    val text: String,
    val preeditStart: Int,
    val preeditLength: Int,
    val caretIndex: Int,
    val imeType: Int,
    val imeKeyboardMode: Int,
    val multiline: Boolean,
    val enterLabel: String
)
