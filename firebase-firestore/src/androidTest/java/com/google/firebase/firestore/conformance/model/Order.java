// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.conformance.model;

import androidx.annotation.NonNull;
import com.google.auto.value.AutoValue;
import com.google.firestore.v1.StructuredQuery;

/** An order on a field. */
@AutoValue
public abstract class Order {

  /** Name of the field to order by. */
  public abstract String getField();

  /** The direction to order by. */
  public abstract StructuredQuery.Direction getDirection();

  /** Builder for {@link Order}. */
  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public static Order.Builder builder() {
      return new AutoValue_Order.Builder();
    }

    @NonNull
    public abstract Order.Builder setField(@NonNull String field);

    @NonNull
    public abstract Order.Builder setDirection(@NonNull StructuredQuery.Direction direction);

    @NonNull
    public abstract Order build();
  }
}
