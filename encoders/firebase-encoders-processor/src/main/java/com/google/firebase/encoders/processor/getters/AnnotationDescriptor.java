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

package com.google.firebase.encoders.processor.getters;

import com.google.auto.value.AutoValue;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;

/** Represents an annotation with its explicitly set properties. */
@AutoValue
public abstract class AnnotationDescriptor {

  /** Annotation type. */
  public abstract AnnotationMirror type();

  /** List of annotation properties. */
  public abstract List<AnnotationProperty> properties();

  public static AnnotationDescriptor create(
      AnnotationMirror type, List<AnnotationProperty> properties) {
    return new AutoValue_AnnotationDescriptor(type, properties);
  }
}
