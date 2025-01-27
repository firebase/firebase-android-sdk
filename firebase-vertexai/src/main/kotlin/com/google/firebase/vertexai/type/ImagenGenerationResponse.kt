package com.google.firebase.vertexai.type

public class ImagenGenerationResponse<T>
internal constructor(public val images: List<T>, public val filteredReason: String?) {}
