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

package com.google.firebase.decoders;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.lang.annotation.Annotation;

/**
 * {@link AnnotatedFieldHandler} provide a way to handle field values based on its annotation.
 *
 * <p>Each annotation type should be registered along with associated {@link AnnotatedFieldHandler},
 * non-registered annotation will be skipped in the process of decoding.
 */
public interface AnnotatedFieldHandler<U extends Annotation> {
  /**
   * This method will be applied after annotated field is decoded, the return value of this method
   * will be used as the decoded value of the annotated field.
   *
   * <p>If a field is annotated with multiple annotations, each annotation along with its {@link
   * AnnotatedFieldHandler} will be applied, the return value of the last executed {@link
   * AnnotatedFieldHandler} will be used as the decoded value of the annotated field.
   */
  @Nullable
  <T> T apply(@NonNull U annotation, @Nullable T fieldDecodedResult, @NonNull Class<T> type);
}
