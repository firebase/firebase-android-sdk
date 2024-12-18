package com.google.firebase.vertexai.type

public class ImagenGenerationConfig(
    public val negativePrompt: String? = null,
    public val numberOfImages: Int = 1,
    public val aspectRatio: ImagenAspectRatio? = null,
) {}
