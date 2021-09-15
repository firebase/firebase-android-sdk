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

package com.google.firebase.firestore.core;

import static java.util.Collections.unmodifiableList;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firebase.firestore.model.mutation.FieldTransform;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.PatchMutation;
import com.google.firebase.firestore.model.mutation.Precondition;
import com.google.firebase.firestore.model.mutation.SetMutation;
import com.google.firebase.firestore.model.mutation.TransformOperation;
import com.google.firebase.firestore.util.Assert;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserData {
  private UserData() {} // Do not instantiate

  /*
   * Represents what type of API method provided the data being parsed; useful for determining which
   * error conditions apply during parsing and providing better error messages.
   */
  public enum Source {
    /** The data comes from a regular Set operation, without merge. */
    Set,
    /** The data comes from a Set operation with merge enabled. */
    MergeSet,
    /** The data comes from an Update operation. */
    Update,
    /**
     * Indicates the source is a where clause, cursor bound, arrayUnion() element, etc. Of note,
     * ParseContext.isWrite() will return false.
     */
    Argument,
    /**
     * Indicates that the source is an Argument that may directly contain nested arrays (e.g. the
     * operand of a `whereIn` query).
     */
    ArrayArgument
  }

  /**
   * Accumulates the side-effect results of parsing user input. These include:
   *
   * <ul>
   *   <li>The field mask naming all the fields that have values.
   *   <li>The transform operations that must be applied in the batch to implement server-generated
   *       behavior. In the wire protocol these are encoded separately from the Value.
   * </ul>
   */
  public static class ParseAccumulator {
    /**
     * What type of API method provided the data being parsed; useful for determining which error
     * conditions apply during parsing and providing better error messages.
     */
    private final Source dataSource;

    /** Accumulates a set of the field paths found while parsing the data. */
    private final Set<FieldPath> fieldMask;

    /** Accumulates a list of field transforms found while parsing the data. */
    private final ArrayList<FieldTransform> fieldTransforms;

    /** @param dataSource Indicates what kind of API method this data came from. */
    public ParseAccumulator(Source dataSource) {
      this.dataSource = dataSource;
      this.fieldMask = new HashSet<>();
      this.fieldTransforms = new ArrayList<>();
    }

    public Source getDataSource() {
      return dataSource;
    }

    public List<FieldTransform> getFieldTransforms() {
      return fieldTransforms;
    }

    /** Returns a new ParseContext representing the root of a user document. */
    public ParseContext rootContext() {
      return new ParseContext(this, FieldPath.EMPTY_PATH, /* arrayElement= */ false);
    }

    /**
     * Returns {@code true} if the given {@code fieldPath} was encountered in the current document.
     */
    public boolean contains(FieldPath fieldPath) {
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

    /** Adds the given {@code fieldPath} to the accumulated FieldMask. */
    void addToFieldMask(FieldPath fieldPath) {
      fieldMask.add(fieldPath);
    }

    /** Adds a transformation for the given field path. */
    void addToFieldTransforms(FieldPath fieldPath, TransformOperation transformOperation) {
      fieldTransforms.add(new FieldTransform(fieldPath, transformOperation));
    }

    /**
     * Wraps the given {@code data} along with any accumulated field mask and transforms into a
     * ParsedSetData representing a user-issued merge.
     *
     * @return ParsedSetData that wraps the contents of this ParseAccumulator.
     */
    public ParsedSetData toMergeData(ObjectValue data) {
      return new ParsedSetData(
          data, FieldMask.fromSet(fieldMask), unmodifiableList(fieldTransforms));
    }

    /**
     * Wraps the given {@code data} and {@code userFieldMask} along with any accumulated transforms
     * that are covered by the given field mask into a ParsedSetData that represents a user-issued
     * merge.
     *
     * @param data The converted user data.
     * @param userFieldMask The user-supplied field mask that masks out any changes that have been
     *     accumulated so far.
     * @return ParsedSetData that wraps the contents of this ParseAccumulator. The field mask in the
     *     result will be the userFieldMask and only transforms that are covered by the mask will be
     *     included.
     */
    public ParsedSetData toMergeData(ObjectValue data, FieldMask userFieldMask) {

      ArrayList<FieldTransform> coveredFieldTransforms = new ArrayList<>();

      for (FieldTransform parsedTransform : fieldTransforms) {
        if (userFieldMask.covers(parsedTransform.getFieldPath())) {
          coveredFieldTransforms.add(parsedTransform);
        }
      }

      return new ParsedSetData(data, userFieldMask, unmodifiableList(coveredFieldTransforms));
    }

    /**
     * Wraps the given {@code data} along with any accumulated transforms into a ParsedSetData that
     * represents a user-issued Set.
     *
     * @return ParsedSetData that wraps the contents of this ParseAccumulator.
     */
    public ParsedSetData toSetData(ObjectValue data) {
      return new ParsedSetData(data, /* fieldMask= */ null, unmodifiableList(fieldTransforms));
    }

    /**
     * Wraps the given {@code data} along with any accumulated field mask and transforms into a
     * ParsedUpdateData that represents a user-issued Update.
     *
     * @return ParsedSetData that wraps the contents of this ParseAccumulator.
     */
    public ParsedUpdateData toUpdateData(ObjectValue data) {
      return new ParsedUpdateData(
          data, FieldMask.fromSet(fieldMask), unmodifiableList(fieldTransforms));
    }
  }

  /**
   * A "context" object that wraps a ParseAccumulator and refers to a specific location in a
   * user-supplied document. Instances are created and passed around while traversing user data
   * during parsing in order to conveniently accumulate data in the ParseAccumulator.
   */
  public static class ParseContext {

    private final ParseAccumulator accumulator;

    /** The current path being parsed. */
    // TODO: path should never be null, but we don't support array paths right now.
    @Nullable private final FieldPath path;

    /** Whether or not this context corresponds to an element of an array. */
    private final boolean arrayElement;

    /**
     * Initializes a ParseContext with the given source and path.
     *
     * @param accumulator The ParseAccumulator on which to add results.
     * @param path A path within the object being parsed. This could be an empty path (in which case
     *     the context represents the root of the data being parsed), or a nonempty path (indicating
     *     the context represents a nested location within the data).
     *     <p>TODO: We don't support array paths right now, so path can be null to indicate the
     *     context represents any location within an array (in which case certain features will not
     *     work and errors will be somewhat compromised).
     * @param arrayElement Whether or not this context corresponds to an element of an array.
     */
    private ParseContext(
        ParseAccumulator accumulator, @Nullable FieldPath path, boolean arrayElement) {
      this.accumulator = accumulator;
      this.path = path;
      this.arrayElement = arrayElement;
    }

    /** Whether or not this context corresponds to an element of an array. */
    public boolean isArrayElement() {
      return arrayElement;
    }

    /**
     * What type of API method provided the data being parsed; useful for determining which error
     * conditions apply during parsing and providing better error messages.
     */
    public Source getDataSource() {
      return accumulator.dataSource;
    }

    @Nullable
    public FieldPath getPath() {
      return path;
    }

    /** Returns true for the non-query parse contexts (Set, MergeSet and Update). */
    public boolean isWrite() {
      switch (accumulator.dataSource) {
        case Set: // fall through
        case MergeSet: // fall through
        case Update:
          return true;
        case Argument:
        case ArrayArgument:
          return false;
        default:
          throw Assert.fail(
              "Unexpected case for UserDataSource: %s", accumulator.dataSource.name());
      }
    }

    public ParseContext childContext(String fieldName) {
      FieldPath childPath = path == null ? null : path.append(fieldName);
      ParseContext context = new ParseContext(accumulator, childPath, /*arrayElement=*/ false);
      context.validatePathSegment(fieldName);
      return context;
    }

    public ParseContext childContext(FieldPath fieldPath) {
      FieldPath childPath = path == null ? null : path.append(fieldPath);
      ParseContext context = new ParseContext(accumulator, childPath, /*arrayElement=*/ false);
      context.validatePath();
      return context;
    }

    @SuppressWarnings("unused")
    public ParseContext childContext(int arrayIndex) {
      // TODO: We don't support array paths right now; so make path null.
      return new ParseContext(accumulator, /*path=*/ null, /*arrayElement=*/ true);
    }

    /** Adds the given {@code fieldPath} to the accumulated FieldMask. */
    public void addToFieldMask(FieldPath fieldPath) {
      accumulator.addToFieldMask(fieldPath);
    }

    /** Adds a transformation for the given field path. */
    public void addToFieldTransforms(FieldPath fieldPath, TransformOperation transformOperation) {
      accumulator.addToFieldTransforms(fieldPath, transformOperation);
    }

    /** Creates an error including the given reason and the current field path. */
    public RuntimeException createError(String reason) {
      String fieldDescription =
          (this.path == null || this.path.isEmpty())
              ? ""
              : " (found in field " + this.path.toString() + ")";
      return new IllegalArgumentException("Invalid data. " + reason + fieldDescription);
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

    private static final String RESERVED_FIELD_DESIGNATOR = "__";

    private void validatePathSegment(String segment) {
      if (segment.isEmpty()) {
        throw this.createError("Document fields must not be empty");
      }

      if (isWrite()
          && segment.startsWith(RESERVED_FIELD_DESIGNATOR)
          && segment.endsWith(RESERVED_FIELD_DESIGNATOR)) {
        throw this.createError("Document fields cannot begin and end with \"__\"");
      }
    }
  }

  /** The result of parsing document data (e.g. for a setData call). */
  public static class ParsedSetData {
    private final ObjectValue data;
    @Nullable private final FieldMask fieldMask;
    private final List<FieldTransform> fieldTransforms;

    ParsedSetData(
        ObjectValue data, @Nullable FieldMask fieldMask, List<FieldTransform> fieldTransforms) {
      this.data = data;
      this.fieldMask = fieldMask;
      this.fieldTransforms = fieldTransforms;
    }

    public ObjectValue getData() {
      return data;
    }

    @Nullable
    public FieldMask getFieldMask() {
      return fieldMask;
    }

    public List<FieldTransform> getFieldTransforms() {
      return fieldTransforms;
    }

    public Mutation toMutation(DocumentKey key, Precondition precondition) {
      if (fieldMask != null) {
        return new PatchMutation(key, data, fieldMask, precondition, fieldTransforms);
      } else {
        return new SetMutation(key, data, precondition, fieldTransforms);
      }
    }
  }

  /** The result of parsing "update" data (i.e. for an updateData call). */
  public static class ParsedUpdateData {
    private final ObjectValue data;
    // The fieldMask does not include document transforms.
    private final FieldMask fieldMask;
    private final List<FieldTransform> fieldTransforms;

    ParsedUpdateData(ObjectValue data, FieldMask fieldMask, List<FieldTransform> fieldTransforms) {
      this.data = data;
      this.fieldMask = fieldMask;
      this.fieldTransforms = fieldTransforms;
    }

    public ObjectValue getData() {
      return data;
    }

    public FieldMask getFieldMask() {
      return fieldMask;
    }

    public List<FieldTransform> getFieldTransforms() {
      return fieldTransforms;
    }

    public Mutation toMutation(DocumentKey key, Precondition precondition) {
      return new PatchMutation(key, data, fieldMask, precondition, fieldTransforms);
    }
  }
}
