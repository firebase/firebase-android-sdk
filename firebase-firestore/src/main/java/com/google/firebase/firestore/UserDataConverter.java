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

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue.ArrayRemoveFieldValue;
import com.google.firebase.firestore.FieldValue.ArrayUnionFieldValue;
import com.google.firebase.firestore.FieldValue.DeleteFieldValue;
import com.google.firebase.firestore.FieldValue.ServerTimestampFieldValue;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.mutation.ArrayTransformOperation;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firebase.firestore.model.mutation.FieldTransform;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.PatchMutation;
import com.google.firebase.firestore.model.mutation.Precondition;
import com.google.firebase.firestore.model.mutation.ServerTimestampOperation;
import com.google.firebase.firestore.model.mutation.SetMutation;
import com.google.firebase.firestore.model.mutation.TransformMutation;
import com.google.firebase.firestore.model.value.ArrayValue;
import com.google.firebase.firestore.model.value.BlobValue;
import com.google.firebase.firestore.model.value.BooleanValue;
import com.google.firebase.firestore.model.value.DoubleValue;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.GeoPointValue;
import com.google.firebase.firestore.model.value.IntegerValue;
import com.google.firebase.firestore.model.value.NullValue;
import com.google.firebase.firestore.model.value.ObjectValue;
import com.google.firebase.firestore.model.value.ReferenceValue;
import com.google.firebase.firestore.model.value.StringValue;
import com.google.firebase.firestore.model.value.TimestampValue;
import com.google.firebase.firestore.util.Assert;
import com.google.firebase.firestore.util.CustomClassMapper;
import com.google.firebase.firestore.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Helper for parsing raw user input (provided via the API) into internal model classes.
 *
 * @hide
 */
public final class UserDataConverter {
  /** The result of parsing document data (e.g. for a setData call). */
  public static class ParsedDocumentData {
    private final ObjectValue data;
    @Nullable private final FieldMask fieldMask;
    private final List<FieldTransform> fieldTransforms;

    ParsedDocumentData(
        ObjectValue data, @Nullable FieldMask fieldMask, List<FieldTransform> fieldTransforms) {
      this.data = data;
      this.fieldMask = fieldMask;
      this.fieldTransforms = fieldTransforms;
    }

    public List<Mutation> toMutationList(DocumentKey key, Precondition precondition) {
      ArrayList<Mutation> mutations = new ArrayList<>();
      if (fieldMask != null) {
        mutations.add(new PatchMutation(key, data, fieldMask, precondition));
      } else {
        mutations.add(new SetMutation(key, data, precondition));
      }
      if (!fieldTransforms.isEmpty()) {
        mutations.add(new TransformMutation(key, fieldTransforms));
      }
      return mutations;
    }
  }

  /** The result of parsing "update" data (i.e. for an updateData call). */
  public static class ParsedUpdateData {
    private final ObjectValue data;
    private final FieldMask fieldMask;
    private final List<FieldTransform> fieldTransforms;

    ParsedUpdateData(ObjectValue data, FieldMask fieldMask, List<FieldTransform> fieldTransforms) {
      this.data = data;
      this.fieldMask = fieldMask;
      this.fieldTransforms = fieldTransforms;
    }

    public List<FieldTransform> getFieldTransforms() {
      return fieldTransforms;
    }

    public List<Mutation> toMutationList(DocumentKey key, Precondition precondition) {
      ArrayList<Mutation> mutations = new ArrayList<>();
      mutations.add(new PatchMutation(key, data, fieldMask, precondition));
      if (!fieldTransforms.isEmpty()) {
        mutations.add(new TransformMutation(key, fieldTransforms));
      }
      return mutations;
    }
  }

  /*
   * Represents what type of API method provided the data being parsed; useful for determining which
   * error conditions apply during parsing and providing better error messages.
   */
  private enum UserDataSource {
    Set,
    MergeSet,
    Update,
    /**
     * Indicates the source is a where clause, cursor bound, arrayUnion() element, etc. Of note,
     * isWrite(Argument) will return false.
     */
    Argument
  }

  private static boolean isWrite(UserDataSource dataSource) {
    switch (dataSource) {
      case Set: // fall through
      case MergeSet: // fall through
      case Update:
        return true;
      case Argument:
        return false;
      default:
        throw Assert.fail("Unexpected case for UserDataSource: %s", dataSource.name());
    }
  }

  /** A "context" object passed around while parsing user data. */
  private class ParseContext {

    private final Pattern reservedFieldRegex = Pattern.compile("^__.*__$");

    /** The current path being parsed. */
    // TODO: path should never be null, but we don't support array paths right now.
    @Nullable private final FieldPath path;

    /** Whether or not this context corresponds to an element of an array. */
    private final boolean arrayElement;

    /**
     * What type of API method provided the data being parsed; useful for determining which error
     * conditions apply during parsing and providing better error messages.
     */
    private final UserDataSource dataSource;

    /** Accumulates a list of field transforms found while parsing the data. */
    private final ArrayList<FieldTransform> fieldTransforms;

    /** Accumulates a list of the field paths found while parsing the data. */
    private final SortedSet<FieldPath> fieldMask;

    /**
     * Initializes a ParseContext with the given source and path.
     *
     * @param dataSource Indicates what kind of API method this data came from.
     * @param path A path within the object being parsed. This could be an empty path (in which case
     *     the context represents the root of the data being parsed), or a nonempty path (indicating
     *     the context represents a nested location within the data).
     *     <p>TODO: We don't support array paths right now, so path can be null to indicate the
     *     context represents any location within an array (in which case certain features will not
     *     work and errors will be somewhat compromised).
     * @param arrayElement Whether or not this context corresponds to an element of an array.
     * @param fieldTransforms A mutable list of field transforms encountered while parsing the data.
     * @param fieldMask A mutable list of field paths encountered while parsing the data.
     */
    private ParseContext(
        UserDataSource dataSource,
        @Nullable FieldPath path,
        boolean arrayElement,
        ArrayList<FieldTransform> fieldTransforms,
        SortedSet<FieldPath> fieldMask) {
      this.dataSource = dataSource;
      this.path = path;
      this.arrayElement = arrayElement;
      this.fieldTransforms = fieldTransforms;
      this.fieldMask = fieldMask;
    }

    ParseContext(UserDataSource dataSource, @Nullable FieldPath path) {
      this(dataSource, path, /*arrayElement=*/ false, new ArrayList<>(), new TreeSet<>());
      validatePath();
    }

    ParseContext childContext(String fieldName) {
      FieldPath childPath = path == null ? null : path.append(fieldName);
      ParseContext context =
          new ParseContext(
              dataSource, childPath, /*arrayElement=*/ false, fieldTransforms, fieldMask);
      context.validatePathSegment(fieldName);
      return context;
    }

    ParseContext childContext(FieldPath fieldPath) {
      FieldPath childPath = path == null ? null : path.append(fieldPath);
      ParseContext context =
          new ParseContext(
              dataSource, childPath, /*arrayElement=*/ false, fieldTransforms, fieldMask);
      context.validatePath();
      return context;
    }

    ParseContext childContext(int arrayIndex) {
      // TODO: We don't support array paths right now; so make path null.
      return new ParseContext(
          dataSource, /*path=*/ null, /*arrayElement=*/ true, fieldTransforms, fieldMask);
    }

    /** Creates an error including the given reason and the current field path. */
    RuntimeException createError(String reason) {
      String fieldDescription =
          (this.path == null || this.path.isEmpty())
              ? ""
              : " (found in field " + this.path.toString() + ")";
      return new IllegalArgumentException("Invalid data. " + reason + fieldDescription);
    }

    /** Returns 'true' if 'fieldPath' was traversed when creating this context. */
    boolean contains(FieldPath fieldPath) {
      for (FieldPath field : fieldMask) {
        if (fieldPath.isPrefixOf(field)) {
          return true;
        }
      }

      for (FieldTransform fieldTransform : fieldTransforms) {
        if (fieldPath.isPrefixOf(fieldTransform.getFieldPath())) {
          return true;
        }
      }

      return false;
    }

    private void validatePath() {
      // TODO: Remove null check once we have proper paths for fields within arrays.
      if (this.path == null) {
        return;
      }
      for (int i = 0; i < this.path.length(); i++) {
        this.validatePathSegment(this.path.getSegment(i));
      }
    }

    private void validatePathSegment(String segment) {
      if (isWrite(dataSource) && reservedFieldRegex.matcher(segment).find()) {
        throw this.createError("Document fields cannot begin and end with __");
      }
    }
  }

  private final DatabaseId databaseId;

  public UserDataConverter(DatabaseId databaseId) {
    this.databaseId = databaseId;
  }

  /** Parse document data from a non-merge set() call. */
  public ParsedDocumentData parseSetData(Map<String, Object> input) {
    ParseContext context = new ParseContext(UserDataSource.Set, FieldPath.EMPTY_PATH);
    FieldValue parsed = parseData(input, context);
    hardAssert(parsed instanceof ObjectValue, "Parse result should be an object.");

    return new ParsedDocumentData(
        (ObjectValue) parsed,
        /* fieldMask= */ null,
        Collections.unmodifiableList(context.fieldTransforms));
  }

  /** Parse document data from a set() call with SetOptions.merge() set. */
  public ParsedDocumentData parseMergeData(
      Map<String, Object> input, @Nullable FieldMask fieldMask) {
    ParseContext context = new ParseContext(UserDataSource.MergeSet, FieldPath.EMPTY_PATH);
    FieldValue parsed = parseData(input, context);
    hardAssert(parsed instanceof ObjectValue, "Parse result should be an object.");

    List<FieldTransform> fieldTransforms;

    if (fieldMask == null) {
      fieldMask = FieldMask.fromCollection(context.fieldMask);
      fieldTransforms = context.fieldTransforms;
    } else {
      // Verify that all elements specified in the field mask are part of the parsed context.
      for (FieldPath field : fieldMask.getMask()) {
        if (!context.contains(field)) {
          throw new IllegalArgumentException(
              "Field '"
                  + field.toString()
                  + "' is specified in your field mask but not in your input data.");
        }
      }

      fieldTransforms = new ArrayList<>();

      for (FieldTransform parsedTransform : context.fieldTransforms) {
        if (fieldMask.covers(parsedTransform.getFieldPath())) {
          fieldTransforms.add(parsedTransform);
        }
      }
    }

    return new ParsedDocumentData((ObjectValue) parsed, fieldMask, fieldTransforms);
  }

  /** Parse update data from an update() call. */
  public ParsedUpdateData parseUpdateData(Map<String, Object> data) {
    checkNotNull(data, "Provided update data must not be null.");
    ArrayList<FieldPath> fieldMaskPaths = new ArrayList<>();
    ObjectValue updateData = ObjectValue.emptyObject();

    ParseContext context = new ParseContext(UserDataSource.Update, FieldPath.EMPTY_PATH);
    for (Entry<String, Object> entry : data.entrySet()) {
      FieldPath fieldPath =
          com.google.firebase.firestore.FieldPath.fromDotSeparatedPath(entry.getKey())
              .getInternalPath();
      Object fieldValue = entry.getValue();

      if (fieldValue instanceof DeleteFieldValue) {
        // Add it to the field mask, but don't add anything to updateData.
        fieldMaskPaths.add(fieldPath);
      } else {
        @Nullable FieldValue parsedValue = parseData(fieldValue, context.childContext(fieldPath));
        if (parsedValue != null) {
          fieldMaskPaths.add(fieldPath);
          updateData = updateData.set(fieldPath, parsedValue);
        }
      }
    }

    FieldMask mask = FieldMask.fromCollection(fieldMaskPaths);
    return new ParsedUpdateData(
        updateData, mask, Collections.unmodifiableList(context.fieldTransforms));
  }

  /**
   * Parses the update data from the update(field, value, field, value...) varargs call, accepting
   * both strings and FieldPaths.
   */
  public ParsedUpdateData parseUpdateData(List<Object> fieldsAndValues) {
    ParseContext context = new ParseContext(UserDataSource.Update, FieldPath.EMPTY_PATH);
    ArrayList<FieldPath> fieldMaskPaths = new ArrayList<>();
    ObjectValue updateData = ObjectValue.emptyObject();

    // fieldsAndValues.length and alternating types should already be validated by
    // Util.collectUpdateArguments().
    hardAssert(
        fieldsAndValues.size() % 2 == 0,
        "Expected fieldAndValues to contain an even number of elements");

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
        fieldMaskPaths.add(parsedField);
      } else {
        FieldValue parsedValue = parseData(fieldValue, context.childContext(parsedField));
        if (parsedValue != null) {
          fieldMaskPaths.add(parsedField);
          updateData = updateData.set(parsedField, parsedValue);
        }
      }
    }

    FieldMask mask = FieldMask.fromCollection(fieldMaskPaths);
    return new ParsedUpdateData(updateData, mask, context.fieldTransforms);
  }

  /** Parse a "query value" (e.g. value in a where filter or a value in a cursor bound). */
  public FieldValue parseQueryValue(Object input) {
    ParseContext context = new ParseContext(UserDataSource.Argument, FieldPath.EMPTY_PATH);
    @Nullable FieldValue parsed = parseData(input, context);
    hardAssert(parsed != null, "Parsed data should not be null.");
    hardAssert(
        context.fieldTransforms.size() == 0, "Field transforms should have been disallowed.");
    return parsed;
  }

  /**
   * Converts a POJO into a Map, throwing appropriate errors if it wasn't actually a proper POJO.
   */
  public Map<String, Object> convertPOJO(Object pojo) {
    checkNotNull(pojo, "Provided data must not be null.");
    String reason =
        "Invalid data. Data must be a Map<String, Object> or a suitable POJO object, but it was ";

    // Check Array before calling CustomClassMapper since it'll give you a confusing message
    // to use List instead, which also won't work.
    if (pojo.getClass().isArray()) {
      throw new IllegalArgumentException(reason + "an array");
    }

    Object converted = CustomClassMapper.convertToPlainJavaTypes(pojo);
    if (!(converted instanceof Map)) {
      throw new IllegalArgumentException(reason + "of type: " + Util.typeName(pojo));
    }

    @SuppressWarnings("unchecked") // CustomClassMapper promises to map keys to Strings.
    Map<String, Object> map = (Map<String, Object>) converted;
    return map;
  }

  /**
   * Internal helper for parsing user data.
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
      // If context.path is null we are inside an array and we don't support field mask paths more
      // granular than the top-level array.
      if (context.path != null) {
        context.fieldMask.add(context.path);
      }

      if (input instanceof List) {
        // TODO: Include the path containing the array in the error message.
        if (context.arrayElement) {
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
    if (!isWrite(context.dataSource)) {
      throw context.createError(
          String.format("%s() can only be used with set() and update()", value.getMethodName()));
    }
    if (context.path == null) {
      throw context.createError(
          String.format("%s() is not currently supported inside arrays", value.getMethodName()));
    }

    if (value instanceof DeleteFieldValue) {
      if (context.dataSource == UserDataSource.MergeSet) {
        // No transform to add for a delete, but we need to add it to our
        // fieldMask so it gets deleted.
        context.fieldMask.add(context.path);
      } else if (context.dataSource == UserDataSource.Update) {
        hardAssert(
            context.path.length() > 0,
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
      context.fieldTransforms.add(
          new FieldTransform(context.path, ServerTimestampOperation.getInstance()));
    } else if (value instanceof ArrayUnionFieldValue) {
      List<FieldValue> parsedElements =
          parseArrayTransformElements(((ArrayUnionFieldValue) value).getElements());
      ArrayTransformOperation arrayUnion = new ArrayTransformOperation.Union(parsedElements);
      context.fieldTransforms.add(new FieldTransform(context.path, arrayUnion));
    } else if (value instanceof ArrayRemoveFieldValue) {
      List<FieldValue> parsedElements =
          parseArrayTransformElements(((ArrayRemoveFieldValue) value).getElements());
      ArrayTransformOperation arrayRemove = new ArrayTransformOperation.Remove(parsedElements);
      context.fieldTransforms.add(new FieldTransform(context.path, arrayRemove));
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
    ArrayList<FieldValue> result = new ArrayList<>(elements.size());
    for (int i = 0; i < elements.size(); i++) {
      Object element = elements.get(i);
      // Although array transforms are used with writes, the actual elements
      // being unioned or removed are not considered writes since they cannot
      // contain any FieldValue sentinels, etc.
      ParseContext context = new ParseContext(UserDataSource.Argument, FieldPath.EMPTY_PATH);
      result.add(parseData(element, context.childContext(i)));
    }
    return result;
  }
}
