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

public class GroupSnapshot extends AggregateSnapshot {

  @NonNull
  public Map<FieldPath, Object> getFields() {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public Map<FieldPath, Object> getFields(
      @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    throw new RuntimeException("not implemented");
  }

  public boolean contains(@NonNull FieldPath field) {
    throw new RuntimeException("not implemented");
  }

  public boolean contains(@NonNull String field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Object get(@NonNull FieldPath field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Object get(@NonNull String field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Object get(
      @NonNull FieldPath field, @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Object get(
      @NonNull String field, @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public <T> T get(@NonNull FieldPath field, @NonNull Class<T> valueType) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public <T> T get(@NonNull String field, @NonNull Class<T> valueType) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public <T> T get(
      @NonNull FieldPath field,
      @NonNull Class<T> valueType,
      @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public <T> T get(
      @NonNull String field,
      @NonNull Class<T> valueType,
      @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Boolean getBoolean(@NonNull FieldPath field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Boolean getBoolean(@NonNull String field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Double getDouble(@NonNull FieldPath field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Double getDouble(@NonNull String field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public String getString(@NonNull FieldPath field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public String getString(@NonNull String field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Long getLong(@NonNull FieldPath field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Long getLong(@NonNull String field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Date getDate(@NonNull FieldPath field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Date getDate(@NonNull String field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Date getDate(
      @NonNull FieldPath field, @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Date getDate(
      @NonNull String field, @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Timestamp getTimestamp(@NonNull FieldPath field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Timestamp getTimestamp(@NonNull String field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Timestamp getTimestamp(
      @NonNull FieldPath field, @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Timestamp getTimestamp(
      @NonNull String field, @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Blob getBlob(@NonNull FieldPath field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public Blob getBlob(@NonNull String field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public GeoPoint getGeoPoint(@NonNull FieldPath field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public GeoPoint getGeoPoint(@NonNull String field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public DocumentReference getDocumentReference(@NonNull FieldPath field) {
    throw new RuntimeException("not implemented");
  }

  @Nullable
  public DocumentReference getDocumentReference(@NonNull String field) {
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
