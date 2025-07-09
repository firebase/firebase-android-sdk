package com.google.firebase.ai.type

@PublicPreviewAPI
public class ImagenSubjectReference(
  image: ImagenInlineImage,
  referenceId: Int? = null,
  description: String? = null,
  subjectType: ImagenSubjectReferenceType? = null,
) :
  ImagenReferenceImage(
    image = image,
    referenceId = referenceId,
    subjectConfig = ImagenSubjectConfig(description, subjectType),
  ) {}
