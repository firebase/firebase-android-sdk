/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.firestore.sdk34;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark a record component to be automatically populated with the document's ID
 * when the record is created from a firebase Firestore document (for example, via
 * DocumentSnapshot#toObject).
 *
 * <ul>
 *   Any of the following will throw a runtime exception:
 *   <li>This annotation is applied to a property of a type other than String or DocumentReference.
 *   <li>This annotation is applied to a property with a name that conflicts with a read document
 *       component. For example, if a record has a component `firstName` annotated by @DocumentId,
 *       and there is a property from the document named `firstName` as well, an exception is thrown
 *       when you try to read the document into the record via DocumentSnapshot#toObject or
 *       DocumentReference#get.
 *   <li>
 * </ul>
 *
 * <p>When using a record to write to a document (via DocumentReference#set or WriteBatch#set), the
 * property annotated by @DocumentId is ignored, which allows writing the record back to any
 * document, even if it's not the origin of the record.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface DocumentId {}
