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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.RestrictTo;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue.ArrayRemoveFieldValue;
import com.google.firebase.firestore.FieldValue.ArrayUnionFieldValue;
import com.google.firebase.firestore.FieldValue.DeleteFieldValue;
import com.google.firebase.firestore.FieldValue.ServerTimestampFieldValue;
import com.google.firebase.firestore.core.UserData;
import com.google.firebase.firestore.core.UserData.ParseAccumulator;
import com.google.firebase.firestore.core.UserData.ParseContext;
import com.google.firebase.firestore.core.UserData.ParsedSetData;
import com.google.firebase.firestore.core.UserData.ParsedUpdateData;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.mutation.ArrayTransformOperation;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firebase.firestore.model.mutation.NumericIncrementTransformOperation;
import com.google.firebase.firestore.model.mutation.ServerTimestampOperation;
import com.google.firebase.firestore.model.value.ArrayValue;
import com.google.firebase.firestore.model.value.BlobValue;
import com.google.firebase.firestore.model.value.BooleanValue;
import com.google.firebase.firestore.model.value.DoubleValue;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.GeoPointValue;
import com.google.firebase.firestore.model.value.IntegerValue;
import com.google.firebase.firestore.model.value.NullValue;
import com.google.firebase.firestore.model.value.NumberValue;
import com.google.firebase.firestore.model.value.ObjectValue;
import com.google.firebase.firestore.model.value.ReferenceValue;
import com.google.firebase.firestore.model.value.StringValue;
import com.google.firebase.firestore.model.value.TimestampValue;
import com.google.firebase.firestore.util.Assert;
import com.google.firebase.firestore.util.CustomClassMapper;
import com.google.firebase.firestore.util.Util;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;

/**
 * Helper for parsing raw user input (provided via the API) into internal model classes.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class UserDataConverter {

  private final DatabaseId databaseId;

  public UserDataConverter(DatabaseId databaseId) {
    this.databaseId = databaseId;
  }

  /**
   * Parse document data from a non-merge set() call.
   *
   * @param input A map or POJO object representing document data.
   */
  public ParsedSetData parseSetData(Object input) {
    ParseAccumulator accumulator = new ParseAccumulator(UserData.Source.Set);
    ObjectValue updateData = convertAndParseDocumentData(input, accumulator.rootContext());
    return accumulator.toSetData(updateData);
  }

  /**
   * Parse document data from a set() call with SetOptions.merge() set.
   *
   * @param input A map or POJO object representing document data.
   * @param fieldMask A {@link FieldMask} object representing the fields to be merged.
   */
  public ParsedSetData parseMergeData(Object input, @Nullable FieldMask fieldMask) {
    ParseAccumulator accumulator = new ParseAccumulator(UserData.Source.MergeSet);
    ObjectValue updateData = convertAndParseDocumentData(input, accumulator.rootContext());

    if (fieldMask != null) {
      // Verify that all elements specified in the field mask are part of the parsed context.
      for (FieldPath field : fieldMask.getMask()) {
        if (!accumulator.contains(field)) {
          throw new IllegalArgumentException(
              "Field '"
                  + field.toString()
                  + "' is specified in your field mask but not in your input data.");
        }
      }

      return accumulator.toMergeData(updateData, fieldMask);
    } else {
      return accumulator.toMergeData(updateData);
    }
  }

  /** Parse update data from an update() call. */
  public ParsedUpdateData parseUpdateData(Map<String, Object> data) {
    checkNotNull(data, "Provided update data must not be null.");

    ParseAccumulator accumulator = new ParseAccumulator(UserData.Source.Update);
    ParseContext context = accumulator.rootContext();
    ObjectValue updateData = ObjectValue.emptyObject();

    for (Entry<String, Object> entry : data.entrySet()) {
      FieldPath fieldPath =
          com.google.firebase.firestore.FieldPath.fromDotSeparatedPath(entry.getKey())
              .getInternalPath();
      Object fieldValue = entry.getValue();

      if (fieldValue instanceof DeleteFieldValue) {
        // Add it to the field mask, but don't add anything to updateData.
        context.addToFieldMask(fieldPath);
      } else {
        @Nullable
        FieldValue parsedValue =
            convertAndParseFieldData(fieldValue, context.childContext(fieldPath));
        if (parsedValue != null) {
          context.addToFieldMask(fieldPath);
          updateData = updateData.set(fieldPath, parsedValue);
        }
      }
    }

    return accumulator.toUpdateData(updateData);
  }

  /**
   * Parses the update data from the update(field, value, field, value...) varargs call, accepting
   * both strings and FieldPaths.
   */
  public ParsedUpdateData parseUpdateData(List<Object> fieldsAndValues) {
    // fieldsAndValues.length and alternating types should already be validated by
    // Util.collectUpdateArguments().
    hardAssert(
        fieldsAndValues.size() % 2 == 0,
        "Expected fieldAndValues to contain an even number of elements");

    ParseAccumulator accumulator = new ParseAccumulator(UserData.Source.Update);
    ParseContext context = accumulator.rootContext();
    ObjectValue updateData = ObjectValue.emptyObject();

    Iterator<Object> iterator = fieldsAndValues.iterator();
    while (iterator.hasNext()) {
      Object fieldPath = iterator.next();
      Object fieldValue = iterator.next();

      hardAssert(
          fieldPath instanceof String
              || fieldPath instanceof com.google.firebase.firestore.FieldPath,
          "Expected argument to be String or FieldPath.");

      FieldPath parsedField;

      if (fieldPath instanceof String) {
        parsedField =
            com.google.firebase.firestore.FieldPath.fromDotSeparatedPath((String) fieldPath)
                .getInternalPath();
      } else {
        parsedField = ((com.google.firebase.firestore.FieldPath) fieldPath).getInternalPath();
      }

      if (fieldValue instanceof DeleteFieldValue) {
        // Add it to the field mask, but don't add anything to updateData.
        context.addToFieldMask(parsedField);
      } else {
        FieldValue parsedValue =
            convertAndParseFieldData(fieldValue, context.childContext(parsedField));
        if (parsedValue != null) {
          context.addToFieldMask(parsedField);
          updateData = updateData.set(parsedField, parsedValue);
        }
      }
    }

    return accumulator.toUpdateData(updateData);
  }

  /** Parse a "query value" (e.g. value in a where filter or a value in a cursor bound). */
  public FieldValue parseQueryValue(Object input) {
    ParseAccumulator accumulator = new ParseAccumulator(UserData.Source.Argument);

    @Nullable FieldValue parsed = convertAndParseFieldData(input, accumulator.rootContext());
    hardAssert(parsed != null, "Parsed data should not be null.");
    hardAssert(
        accumulator.getFieldTransforms().isEmpty(),
        "Field transforms should have been disallowed.");
    return parsed;
  }

  /** Converts a POJO to native types and then parses it into model types. */
  private FieldValue convertAndParseFieldData(Object input, ParseContext context) {
    Object converted = CustomClassMapper.convertToPlainJavaTypes(input);
    return parseData(converted, context);
  }

  /**
   * Converts a POJO to native types and then parses it into model types. It expects the input to
   * conform to document data (i.e. it must parse into an ObjectValue model type) and will throw an
   * appropriate error otherwise.
   */
  private ObjectValue convertAndParseDocumentData(Object input, ParseContext context) {
    String badDocReason =
        "Invalid data. Data must be a Map<String, Object> or a suitable POJO object, but it was ";

    // Check Array before calling CustomClassMapper since it'll give you a confusing message
    // to use List instead, which also won't work in a set().
    if (input.getClass().isArray()) {
      throw new IllegalArgumentException(badDocReason + "an array");
    }

    Object converted = CustomClassMapper.convertToPlainJavaTypes(input);
    FieldValue value = parseData(converted, context);

    if (!(value instanceof ObjectValue)) {
      throw new IllegalArgumentException(badDocReason + "of type: " + Util.typeName(input));
    }
    return (ObjectValue) value;
  }

  /**
   * Recursive helper for parsing user data.
   *
   * @param input Data to be parsed.
   * @param context A context object representing the current path being parsed, the source of the
   *     data being parsed, etc.
   * @return The parsed value, or null if the value was a FieldValue sentinel that should not be
   *     included in the resulting parsed data.
   */
  @Nullable
  private FieldValue parseData(Object input, ParseContext context) {
    if (input instanceof Map) {
      return parseMap((Map<?, ?>) input, context);

    } else if (input instanceof com.google.firebase.firestore.FieldValue) {
      // FieldValues usually parse into transforms (except FieldValue.delete()) in which case we do
      // not want to include this field in our parsed data (as doing so will overwrite the field
      // directly prior to the transform trying to transform it). So we don't add this location to
      // context.fieldMask and we return null as our parsing result.
      this.parseSentinelFieldValue((com.google.firebase.firestore.FieldValue) input, context);
      return null;

    } else {
      // If the context path is null we are inside an array and we don't support field mask paths
      // more granular than the top-level array.
      if (context.getPath() != null) {
        context.addToFieldMask(context.getPath());
      }

      if (input instanceof List) {
        // TODO: Include the path containing the array in the error message.
        if (context.isArrayElement()) {
          throw context.createError("Nested arrays are not supported");
        }
        return parseList((List<?>) input, context);
      } else {
        return parseScalarValue(input, context);
      }
    }
  }

  private <K, V> ObjectValue parseMap(Map<K, V> map, ParseContext context) {
    Map<String, FieldValue> result = new HashMap<>();

    if (map.isEmpty()) {
      if (context.getPath() != null && !context.getPath().isEmpty()) {
        context.addToFieldMask(context.getPath());
      }
      return ObjectValue.emptyObject();
    } else {
      for (Entry<K, V> entry : map.entrySet()) {
        if (!(entry.getKey() instanceof String)) {
          throw context.createError(
              String.format("Non-String Map key (%s) is not allowed", entry.getValue()));
        }
        String key = (String) entry.getKey();
        @Nullable FieldValue parsedValue = parseData(entry.getValue(), context.childContext(key));
        if (parsedValue != null) {
          result.put(key, parsedValue);
        }
      }
    }
    return ObjectValue.fromMap(result);
  }

  private <T> ArrayValue parseList(List<T> list, ParseContext context) {
    List<FieldValue> result = new ArrayList<>(list.size());
    int entryIndex = 0;
    for (T entry : list) {
      @Nullable FieldValue parsedEntry = parseData(entry, context.childContext(entryIndex));
      if (parsedEntry == null) {
        // Just include nulls in the array for fields being replaced with a sentinel.
        parsedEntry = NullValue.nullValue();
      }
      result.add(parsedEntry);
      entryIndex++;
    }
    return ArrayValue.fromList(result);
  }

  /**
   * "Parses" the provided FieldValue, adding any necessary transforms to context.fieldTransforms.
   */
  private void parseSentinelFieldValue(
      com.google.firebase.firestore.FieldValue value, ParseContext context) {
    // Sentinels are only supported with writes, and not within arrays.
    if (!context.isWrite()) {
      throw context.createError(
          String.format("%s() can only be used with set() and update()", value.getMethodName()));
    }
    if (context.getPath() == null) {
      throw context.createError(
          String.format("%s() is not currently supported inside arrays", value.getMethodName()));
    }

    if (value instanceof DeleteFieldValue) {
      if (context.getDataSource() == UserData.Source.MergeSet) {
        // No transform to add for a delete, but we need to add it to our
        // fieldMask so it gets deleted.
        context.addToFieldMask(context.getPath());
      } else if (context.getDataSource() == UserData.Source.Update) {
        hardAssert(
            context.getPath().length() > 0,
            "FieldValue.delete() at the top level should have already been handled.");
        throw context.createError(
            "FieldValue.delete() can only appear at the top level of your update data");
      } else {
        // We shouldn't encounter delete sentinels for queries or non-merge set() calls.
        throw context.createError(
            "FieldValue.delete() can only be used with update() and "
                + "set() with SetOptions.merge()");
      }
    } else if (value instanceof ServerTimestampFieldValue) {
      context.addToFieldTransforms(context.getPath(), ServerTimestampOperation.getInstance());

    } else if (value instanceof ArrayUnionFieldValue) {
      List<FieldValue> parsedElements =
          parseArrayTransformElements(((ArrayUnionFieldValue) value).getElements());
      ArrayTransformOperation arrayUnion = new ArrayTransformOperation.Union(parsedElements);
      context.addToFieldTransforms(context.getPath(), arrayUnion);

    } else if (value instanceof ArrayRemoveFieldValue) {
      List<FieldValue> parsedElements =
          parseArrayTransformElements(((ArrayRemoveFieldValue) value).getElements());
      ArrayTransformOperation arrayRemove = new ArrayTransformOperation.Remove(parsedElements);
      context.addToFieldTransforms(context.getPath(), arrayRemove);

    } else if (value
        instanceof com.google.firebase.firestore.FieldValue.NumericIncrementFieldValue) {
      com.google.firebase.firestore.FieldValue.NumericIncrementFieldValue
          numericIncrementFieldValue =
              (com.google.firebase.firestore.FieldValue.NumericIncrementFieldValue) value;
      NumberValue operand = (NumberValue) parseQueryValue(numericIncrementFieldValue.getOperand());
      NumericIncrementTransformOperation incrementOperation =
          new NumericIncrementTransformOperation(operand);
      context.addToFieldTransforms(context.getPath(), incrementOperation);

    } else {
      throw Assert.fail("Unknown FieldValue type: %s", Util.typeName(value));
    }
  }

  /**
   * Helper to parse a scalar value (i.e. not a Map or List)
   *
   * @return The parsed value, or null if the value was a FieldValue sentinel that should not be
   *     included in the resulting parsed data.
   */
  @Nullable
  private FieldValue parseScalarValue(Object input, ParseContext context) {
    if (input == null) {
      return NullValue.nullValue();
    } else if (input instanceof Integer) {
      return IntegerValue.valueOf(((Integer) input).longValue());
    } else if (input instanceof Long) {
      return IntegerValue.valueOf(((Long) input));
    } else if (input instanceof Float) {
      return DoubleValue.valueOf(((Float) input).doubleValue());
    } else if (input instanceof Double) {
      return DoubleValue.valueOf((Double) input);
    } else if (input instanceof Boolean) {
      return BooleanValue.valueOf((Boolean) input);
    } else if (input instanceof String) {
      return StringValue.valueOf((String) input);
    } else if (input instanceof Date) {
      return TimestampValue.valueOf(new Timestamp((Date) input));
    } else if (input instanceof Timestamp) {
      Timestamp timestamp = (Timestamp) input;
      long seconds = timestamp.getSeconds();
      // Firestore backend truncates precision down to microseconds. To ensure offline mode works
      // the same with regards to truncation, perform the truncation immediately without waiting for
      // the backend to do that.
      int truncatedNanoseconds = timestamp.getNanoseconds() / 1000 * 1000;
      return TimestampValue.valueOf(new Timestamp(seconds, truncatedNanoseconds));
    } else if (input instanceof GeoPoint) {
      return GeoPointValue.valueOf((GeoPoint) input);
    } else if (input instanceof Blob) {
      return BlobValue.valueOf((Blob) input);
    } else if (input instanceof DocumentReference) {
      DocumentReference ref = (DocumentReference) input;
      // TODO: Rework once pre-converter is ported to Android.
      if (ref.getFirestore() != null) {
        DatabaseId otherDb = ref.getFirestore().getDatabaseId();
        if (!otherDb.equals(databaseId)) {
          throw context.createError(
              String.format(
                  "Document reference is for database %s/%s but should be for database %s/%s",
                  otherDb.getProjectId(),
                  otherDb.getDatabaseId(),
                  databaseId.getProjectId(),
                  databaseId.getDatabaseId()));
        }
      }
      return ReferenceValue.valueOf(databaseId, ref.getKey());
    } else if (input.getClass().isArray()) {
      throw context.createError("Arrays are not supported; use a List instead");
    } else {
      throw context.createError("Unsupported type: " + Util.typeName(input));
    }
  }

  private List<FieldValue> parseArrayTransformElements(List<Object> elements) {
    ParseAccumulator accumulator = new ParseAccumulator(UserData.Source.Argument);

    ArrayList<FieldValue> result = new ArrayList<>(elements.size());
    for (int i = 0; i < elements.size(); i++) {
      Object element = elements.get(i);
      // Although array transforms are used with writes, the actual elements
      // being unioned or removed are not considered writes since they cannot
      // contain any FieldValue sentinels, etc.
      ParseContext context = accumulator.rootContext();
      result.add(convertAndParseFieldData(element, context.childContext(i)));
    }
    return result;
  }
}
