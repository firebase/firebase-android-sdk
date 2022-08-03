// Copyright 2022 Google LLC
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior;
import java.util.Date;
import java.util.Map;
import javax.annotation.Nonnull;

public class AggregateSnapshot {

  AggregateSnapshot() {}

  @NonNull
  public Map<AggregateField, Object> getAggregations() {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public Map<AggregateField, Object> getAggregations(
      @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    throw new RuntimeException("not implemented");
  }

  public boolean contains(@NonNull AggregateField field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Object get(@NonNull AggregateField field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Object get(
      @NonNull AggregateField field, @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public <T> T get(@NonNull AggregateField field, @NonNull Class<T> valueType) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public <T> T get(
      @NonNull AggregateField field,
      @NonNull Class<T> valueType,
      @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    throw new RuntimeException("not implemented");
  }

  // Overload get() specifically for COUNT since it has a well-defined type (i.e. long).
  @Nullable
  public Long get(@Nonnull AggregateField.CountAggregateField field) {
    throw new RuntimeException("not implemented");
  }

  // Overload get() specifically for SUM since it has a well-defined type (i.e. double).
  @Nullable
  public Double get(@Nonnull AggregateField.SumAggregateField field) {
    throw new RuntimeException("not implemented");
  }

  // Overload get() specifically for AVERAGE since it has a well-defined type (i.e. double).
  @Nullable
  public Double get(@Nonnull AggregateField.AverageAggregateField field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Boolean getBoolean(@NonNull AggregateField field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Double getDouble(@NonNull AggregateField field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public String getString(@NonNull AggregateField field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Long getLong(@NonNull AggregateField field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Date getDate(@NonNull AggregateField field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Date getDate(
      @NonNull AggregateField field, @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Timestamp getTimestamp(@NonNull AggregateField field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Timestamp getTimestamp(
      @NonNull AggregateField field, @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Blob getBlob(@NonNull AggregateField field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public GeoPoint getGeoPoint(@NonNull AggregateField field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public DocumentReference getDocumentReference(@NonNull AggregateField field) {
    throw new RuntimeException("not implemented");
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    throw new RuntimeException("not implemented");
  }

  @Override
  public int hashCode() {
    throw new RuntimeException("not implemented");
  }

  @Override
  public String toString() {
    throw new RuntimeException("not implemented");
  }
}
