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

package com.example;

import java.lang.Class;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.annotation.Annotation;

public final class AtEmptyAnnotation {
  private static final AtEmptyAnnotation BUILDER = new AtEmptyAnnotation();

  private static final EmptyAnnotation INSTANCE = new EmptyAnnotationImpl();

  private AtEmptyAnnotation() {
  }

  public static AtEmptyAnnotation builder() {
    return BUILDER;
  }

  public EmptyAnnotation build() {
    return INSTANCE;
  }

  private static final class EmptyAnnotationImpl implements EmptyAnnotation {
    EmptyAnnotationImpl() {
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return EmptyAnnotation.class;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof EmptyAnnotation)) return false;
      EmptyAnnotation that = (EmptyAnnotation) other;

      return true;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("@com.example.EmptyAnnotation");
      return sb.toString();
    }
  }
}