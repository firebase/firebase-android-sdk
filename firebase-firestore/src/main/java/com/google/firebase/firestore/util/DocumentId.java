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

package com.google.firebase.firestore.util;

import com.google.firebase.annotations.PublicApi;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark a POJO field to be automatically populated with the document's ID when
 * the POJO is created from a Firestore document (e.g. via {@link DocumentSnapshot#toObject}).
 *
 * <p>This annotation can only be applied to fields of String or {@link DocumentReference},
 * otherwise a runtime exception will be thrown.
 *
 * <p>When writing a POJO to Firestore, the @DocumentId-annotated field will be ignored, to allow
 * writing to any documents that are not the origin of the POJO.
 */
@PublicApi
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface DocumentId {}
