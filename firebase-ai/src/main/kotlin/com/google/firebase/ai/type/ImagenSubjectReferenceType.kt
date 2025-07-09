package com.google.firebase.ai.type

public class ImagenSubjectReferenceType private constructor(internal val value: String) {

  public companion object {
    public val PERSON: ImagenSubjectReferenceType = ImagenSubjectReferenceType("SUBJECT_TYPE_PERSON")
    public val ANIMAL: ImagenSubjectReferenceType = ImagenSubjectReferenceType("SUBJECT_TYPE_ANIMAL")
    public val PRODUCT: ImagenSubjectReferenceType = ImagenSubjectReferenceType("SUBJECT_TYPE_PRODUCT")
  }
}
