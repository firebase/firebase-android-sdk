/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.ai.type

/** Represents a type for a subject reference, specifying how it should be interpreted. */
public class ImagenSubjectReferenceType private constructor(internal val value: String) {

  public companion object {

    /** Marks the reference type as being of a person */
    @JvmField
    public val PERSON: ImagenSubjectReferenceType =
      ImagenSubjectReferenceType("SUBJECT_TYPE_PERSON")

    /** Marks the reference type as being of a animal */
    @JvmField
    public val ANIMAL: ImagenSubjectReferenceType =
      ImagenSubjectReferenceType("SUBJECT_TYPE_ANIMAL")

    /** Marks the reference type as being of a product */
    @JvmField
    public val PRODUCT: ImagenSubjectReferenceType =
      ImagenSubjectReferenceType("SUBJECT_TYPE_PRODUCT")
  }
}
