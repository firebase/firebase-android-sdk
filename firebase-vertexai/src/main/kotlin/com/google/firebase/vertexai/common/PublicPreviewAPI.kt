package com.google.firebase.vertexai.common

@Retention(AnnotationRetention.BINARY)
@RequiresOptIn(
  level = RequiresOptIn.Level.ERROR,
  message =
    "This API is currently experimental and in public preview and may change in behavior in " +
      "backwards-incompatible ways without notice.",
)
public annotation class PublicPreviewAPI()
