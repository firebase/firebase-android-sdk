package com.google.firebase.vertexai.type

public class ImagenGenerationConfig(
    public val negativePrompt: String? = null,
    public val numberOfImages: Int? = 1,
    public val aspectRatio: ImagenAspectRatio? = null,
    public val imageFormat: ImagenImageFormat? = null,
    public val addWatermark: Boolean? = null
) {}
