package com.google.firebase.vertexai.type

public class ImagenGenerationResponse<T>(
  public val images: List<T>,
  public val filteredReason: String?,
) {}
