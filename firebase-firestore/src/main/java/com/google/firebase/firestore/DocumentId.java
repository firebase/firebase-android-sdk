// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore;

import com.google.firebase.annotations.PublicApi;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark an object field to be the ID of a firestore document.
 *
 * <p>This annotation is recognized by {@link com.google.firebase.firestore.util.CustomClassMapper}.
 *
 * <p>During conversions from documents to java objects, fields with this annotation will be
 * populated with the document ID being converted.
 *
 * <p>When objects with the annotation is used to create new documents, its field value must be null
 * to guarantee uniqueness, otherwise a runtime exception will be thrown.
 *
 * <p>When objects with the annotation is used to update documents, its field value must match the
 * target documents, otherwise a runtime exception will be thrown.
 *
 * <p>This annotation can only be applied to fields of String or {@link DocumentReference},
 * otherwise a runtime exception will be thrown.
 */
@PublicApi
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface DocumentId {}
