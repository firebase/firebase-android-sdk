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

package com.google.firebase.encoders.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates to automatically generate an encoder capable to encode a given type.
 *
 * <p>All fields that need to be encoded <strong>must</strong>must have Java-bean compliant getters,
 * otherwise they will not be encoded.
 *
 * Using the generated encoder:
 * <pre>{@code
 * {@literal @}Encodable
 * public class MyType {
 *   public String getField() { return "hello"; }
 *   public boolean isConditional() { return false; }
 * }
 *
 * DataEncoder encoder = new JsonDataEncoderBuilder()
 *     .configureWith(AutoMyTypeEncoder.CONFIG)
 *     .build();
 *
 * String json = encoder.encode(new MyType);
 *
 * }<pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Encodable {

  /** Field configuration. */
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @interface Field {
    /** Specifies a custom field name for a given property of a type. */
    String name() default "";

    /**
     * Mark a field to be encoded inline in the parent context, instead of nested under its own key.
     *
     * <p>Note: if a field is inlined, its name is ignored.
     */
    boolean inline() default false;
  }

  /** Indicates the code generator to ignore a given property of a type. */
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @interface Ignore {}
}
