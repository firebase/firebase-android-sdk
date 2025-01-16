package com.google.firebase.vertexai.type

@Suppress("EnumEntryName")
public enum class ImagenAspectRatio(internal val internalVal: String) {
    SQUARE_1x1("1:1"),
    PORTRAIT_3x4("3:4"),
    LANDSCAPE_4x3("4:3"),
    PORTRAIT_9x16("9:16"),
    LANDSCAPE_16x9("16:9")
}
