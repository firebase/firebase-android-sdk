package com.google.firebase.firestore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.core.CompositeFilter;
import com.google.firebase.firestore.core.FieldFilter;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import java.util.Arrays;

public abstract class Filter {
  @NonNull
  public static Filter equalTo(@NonNull String field, @Nullable Object value) {
    return equalTo(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter equalTo(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return FieldFilter.create(fieldPath.getInternalPath(), FieldFilter.Operator.EQUAL, value);
  }

  @NonNull
  public static Filter notEqualTo(@NonNull String field, @Nullable Object value) {
    return notEqualTo(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter notEqualTo(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return FieldFilter.create(fieldPath.getInternalPath(), FieldFilter.Operator.NOT_EQUAL, value);
  }

  @NonNull
  public static Filter greaterThan(@NonNull String field, @Nullable Object value) {
    return greaterThan(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter greaterThan(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return FieldFilter.create(
        fieldPath.getInternalPath(), FieldFilter.Operator.GREATER_THAN, value);
  }

  @NonNull
  public static Filter greaterThanOrEqualTo(@NonNull String field, @Nullable Object value) {
    return greaterThanOrEqualTo(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter greaterThanOrEqualTo(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return FieldFilter.create(
        fieldPath.getInternalPath(), FieldFilter.Operator.GREATER_THAN_OR_EQUAL, value);
  }

  @NonNull
  public static Filter lessThan(@NonNull String field, @Nullable Object value) {
    return lessThan(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter lessThan(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return FieldFilter.create(fieldPath.getInternalPath(), FieldFilter.Operator.LESS_THAN, value);
  }

  @NonNull
  public static Filter lessThanOrEqualTo(@NonNull String field, @Nullable Object value) {
    return lessThanOrEqualTo(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter lessThanOrEqualTo(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return FieldFilter.create(
        fieldPath.getInternalPath(), FieldFilter.Operator.LESS_THAN_OR_EQUAL, value);
  }

  @NonNull
  public static Filter arrayContains(@NonNull String field, @Nullable Object value) {
    return arrayContains(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter arrayContains(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return FieldFilter.create(
        fieldPath.getInternalPath(), FieldFilter.Operator.ARRAY_CONTAINS, value);
  }

  @NonNull
  public static Filter arrayContainsAny(@NonNull String field, @Nullable Object value) {
    return arrayContainsAny(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter arrayContainsAny(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return FieldFilter.create(
        fieldPath.getInternalPath(), FieldFilter.Operator.ARRAY_CONTAINS_ANY, value);
  }

  @NonNull
  public static Filter in(@NonNull String field, @Nullable Object value) {
    return in(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter in(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return FieldFilter.create(fieldPath.getInternalPath(), FieldFilter.Operator.IN, value);
  }

  @NonNull
  public static Filter notIn(@NonNull String field, @Nullable Object value) {
    return notIn(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter notIn(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return FieldFilter.create(fieldPath.getInternalPath(), FieldFilter.Operator.NOT_IN, value);
  }

  @NonNull
  public static Filter or(Filter... filters) {
    return new CompositeFilter(Arrays.asList(filters), /*isAnd*/ false);
  }

  @NonNull
  public static Filter and(Filter... filters) {
    return new CompositeFilter(Arrays.asList(filters), /*isAnd*/ true);
  }

  public abstract Query apply(Query query, FirebaseFirestore firestore);

  public abstract boolean matches(Document doc);

  public abstract String getCanonicalId();
}
