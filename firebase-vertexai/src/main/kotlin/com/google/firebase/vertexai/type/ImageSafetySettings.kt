package com.google.firebase.vertexai.type

public class ImageSafetySettings
internal constructor(
  internal val safetyFilterLevel: ImagenSafetyFilter,
  internal val personFilterLevel: ImagenPersonFilter,
) {}
