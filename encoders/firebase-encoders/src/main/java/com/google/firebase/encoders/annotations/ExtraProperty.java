// Copyright 2020 Google LLC
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
 * Meta-annotation used by custom field annotations.
 *
 * <p>Annotations annotated with {@code @ExtraProperty} are automatically recognized by encoders and
 * are propagated into {@link com.google.firebase.encoders.FieldDescriptor}s.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface ExtraProperty {
  /**
   * Field types that can be annotated with this annotation.
   *
   * <p>Encoders will throw if they encounter a annotated field whose type is not one mentioned
   * here.
   */
  Class<?>[] allowedTypes() default {};
}
