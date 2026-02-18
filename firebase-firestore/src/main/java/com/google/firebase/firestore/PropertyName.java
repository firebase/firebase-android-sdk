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
 * Marks a field to be renamed when serialized.
 *
 * <h3>Kotlin Note</h3>
 * When applying this annotation to a property of a Kotlin class, both the {@code @get} and
 * {@code @set} use-site targets should be used.
 * <p>
 * Here is an example of a class that can both be written into and read from Firestore whose
 * {@code foo} property will be stored into and read from a field named {@code my_foo} in the
 * Firestore document:
 * <pre>
 * data class Pojo(@get:PropertyName("my_foo") @set:PropertyName("my_foo") var foo: String? = null) {
 *   constructor() : this(null) // Used by Firestore to create new instances
 * }
 * </pre>
 * <p>
 * If the class only needs to be <em>written</em> into Firestore (and not read from Firestore) then
 * the class can be simplified as follows:
 * <pre>
 * data class Pojo(@get:PropertyName("my_foo") val foo: String? = null)
 * </pre>
 * That is, {@code var} can be tightened to {@code val}, the secondary no-argument constructor can
 * be omitted, and the {@code @set} use-site target can be omitted.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface PropertyName {
  String value();
}
