// Copyright 2018 Google LLC
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark a timestamp field to be populated with a server timestamp. If a POJO
 * being written contains {@code null} for a @ServerTimestamp-annotated field, it will be replaced
 * with a server-generated timestamp.
 *
 * <h3>Kotlin Note</h3>
 * When applying this annotation to a property of a Kotlin class, the {@code @get} use-site target
 * should always be used. There is no need to use the {@code @set} use-site target as this
 * annotation is <em>only</em> considered when <em>writing</em> instances into Firestore, and is
 * ignored when <em>reading</em> instances from Firestore.
 * <p>
 * Here is an example of a class that can both be written into and read from Firestore whose
 * {@code foo} property will be populated with the server timestamp in Firestore if its value is
 * null:
 * <pre>
 * data class Pojo(@get:ServerTimestamp var foo: Timestamp? = null) {
 *   constructor() : this(null) // Used by Firestore to create new instances
 * }
 * </pre>
 * <p>
 * If the class only needs to be <em>written</em> into Firestore (and not read from Firestore) then
 * the class can be simplified as follows:
 * <pre>
 * data class Pojo(@get:ServerTimestamp val foo: Timestamp? = null)
 * </pre>
 * That is, {@code var} can be tightened to {@code val} and the secondary no-argument constructor
 * can be omitted.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface ServerTimestamp {}
