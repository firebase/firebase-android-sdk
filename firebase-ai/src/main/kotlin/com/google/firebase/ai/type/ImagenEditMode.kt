package com.google.firebase.ai.type

public class ImagenEditMode private constructor(internal val value: String) {

  public companion object {
    public val INPAINT_INSERTION: ImagenEditMode = ImagenEditMode("EDIT_MODE_INPAINT_INSERTION")
    public val INPAINT_REMOVAL: ImagenEditMode = ImagenEditMode("EDIT_MODE_INPAINT_REMOVAL")
    public val OUTPAINT: ImagenEditMode = ImagenEditMode("EDIT_MODE_OUTPAINT")
  }
}
