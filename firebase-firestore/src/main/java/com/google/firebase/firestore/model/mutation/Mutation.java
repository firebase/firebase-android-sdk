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

package com.google.firebase.firestore.model.mutation;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.Assert;
import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a Mutation of a document. Mutations with different {@code MutationType} will perform
 * different kinds of changes to a base document. For example, a SET replaces the value of a
 * document and a DELETE deletes a document.
 *
 * <p>In addition to the value of the document mutations also operate on the version. For local
 * mutations (mutations that haven't been committed yet), we preserve the existing version for Set
 * and Patch mutations. For local deletes, we reset the version to 0.
 *
 * <p>Here's the expected transition table.
 *
 * <table>
 * <th><td>MUTATION</td><td>APPLIED TO</td><td>RESULTS IN</td></th>
 * <tr><td>SET</td><td>Document(v3)</td><td>Document(v3)</td></tr>
 * <tr><td>SET</td><td>NoDocument(v3)</td><td>Document(v0)</td></tr>
 * <tr><td>SET</td><td>null</td><td>Document(v0)</td></tr>
 * <tr><td>PATCH</td><td>Document(v3)</td><td>Document(v3)</td></tr>
 * <tr><td>PATCH</td><td>NoDocument(v3)</td><td>NoDocument(v3)</td></tr>
 * <tr><td>PATCH</td><td>null</td><td>null</td></tr>
 * <tr><td>DELETE</td><td>Document(v3)</td><td>NoDocument(v0)</td></tr>
 * <tr><td>DELETE</td><td>NoDocument(v3)</td><td>NoDocument(v0)</td></tr>
 * <tr><td>DELETE</td><td>null</td><td>NoDocument(v0)</td></tr>
 * </table>
 *
 * For acknowledged mutations, we use the updateTime of the WriteResponse as the resulting version
 * for Set and Patch mutations. As deletes have no explicit update time, we use the commitTime of
 * the WriteResponse for acknowledged deletes.
 *
 * <p>If a mutation is acknowledged by the backend but fails the precondition check locally, we
 * return an `UnknownDocument` and rely on Watch to send us the updated version.
 *
 * <p>Field transforms are used only with Patch and Set Mutations. We use the `updateTransforms`
 * field to store transforms, rather than the `transforms` message.
 */
public final class Mutation {
  public enum MutationType {
    /**
     * A mutation that creates or replaces the document at the given key with the object value
     * contents.
     */
    SET,
    /** Represents a Delete operation */
    DELETE,
    /**
     * A mutation that modifies fields of the document at the given key with the given values. The
     * values are applied through a field mask:
     *
     * <ul>
     *   <li>When a field is in both the mask and the values, the corresponding field is updated.
     *   <li>When a field is in neither the mask nor the values, the corresponding field is
     *       unmodified.
     *   <li>When a field is in the mask but not in the values, the corresponding field is deleted.
     *   <li>When a field is not in the mask but is in the values, the values map is ignored.
     * </ul>
     */
    PATCH,
    /**
     * A mutation that verifies the existence of the document at the given key with the provided
     * precondition.
     *
     * <p>The `verify` operation is only used in Transactions, and this class serves primarily to
     * facilitate serialization into protos.
     */
    VERIFY,
  }

  private final DocumentKey key;
  /** The precondition for the mutation. */
  private final Precondition precondition;

  private final MutationType type;

  /** Values to update the document, used by SET and PATCH. */
  private final ObjectValue value;
  /** Mask to indicate which fields should be considered to patch, used by PATCH only. */
  private final FieldMask mask;
  /** Field transforms to apply to the document, used by SET and PATCH. */
  private final List<FieldTransform> fieldTransforms;

  private Mutation(
      DocumentKey key,
      Precondition precondition,
      MutationType type,
      ObjectValue value,
      FieldMask mask,
      List<FieldTransform> fieldTransforms) {
    this.key = key;
    this.precondition = precondition;
    this.type = type;
    this.value = value;
    this.mask = mask;
    this.fieldTransforms = fieldTransforms;
  }

  public static Mutation newSet(DocumentKey key, ObjectValue value) {
    return new Mutation(key, Precondition.NONE, MutationType.SET, value, null, new ArrayList<>());
  }

  public static Mutation newSet(
      DocumentKey key,
      ObjectValue value,
      Precondition precondition,
      List<FieldTransform> fieldTransforms) {
    return new Mutation(key, precondition, MutationType.SET, value, null, fieldTransforms);
  }

  public static Mutation newDelete(DocumentKey key) {
    return new Mutation(key, Precondition.NONE, MutationType.DELETE, null, null, new ArrayList<>());
  }

  public static Mutation newDelete(DocumentKey key, Precondition precondition) {
    return new Mutation(key, precondition, MutationType.DELETE, null, null, new ArrayList<>());
  }

  public static Mutation newPatch(
      DocumentKey key, ObjectValue value, FieldMask mask, Precondition precondition) {
    return new Mutation(key, precondition, MutationType.PATCH, value, mask, new ArrayList<>());
  }

  public static Mutation newPatch(
      DocumentKey key,
      ObjectValue value,
      FieldMask mask,
      Precondition precondition,
      List<FieldTransform> fieldTransforms) {
    return new Mutation(key, precondition, MutationType.PATCH, value, mask, fieldTransforms);
  }

  public static Mutation newVerify(DocumentKey key, Precondition precondition) {
    return new Mutation(key, precondition, MutationType.VERIFY, null, null, new ArrayList<>());
  }

  public DocumentKey getKey() {
    return key;
  }

  public Precondition getPrecondition() {
    return precondition;
  }

  public MutationType getMutationType() {
    return type;
  }

  public ObjectValue getValue() {
    return value;
  }

  public FieldMask getMask() {
    return mask;
  }

  public List<FieldTransform> getFieldTransforms() {
    return fieldTransforms;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Mutation that = (Mutation) o;
    return this.type.equals(that.type)
        && hasSameKeyAndPrecondition(that)
        && hasSameObjectValue(that)
        && hasSameFieldMask(that)
        && hasSameTransforms(that);
  }

  @Override
  public int hashCode() {
    int hashCode = keyAndPreconditionHashCode();
    hashCode = hashCode * 31 + type.hashCode();
    hashCode = hashCode * 31 + valueHashCode();
    hashCode = hashCode * 31 + maskHashCode();
    hashCode = hashCode * 31 + transformsHashCode();

    return hashCode;
  }

  @Override
  public String toString() {
    switch (type) {
      case DELETE:
        {
          return "Mutation{ type=DELETE; " + keyAndPreconditionToString() + "}";
        }
        // TODO(Overlay): Add transforms here and below.
      case SET:
        {
          return "Mutation{ type=SET; " + keyAndPreconditionToString() + ", value=" + value + "}";
        }
      case PATCH:
        {
          return "Mutation{ type=PATCH; "
              + keyAndPreconditionToString()
              + ", mask="
              + mask
              + ", value="
              + value
              + "}";
        }
      case VERIFY:
        {
          return "Mutation{ type=VERIRY; " + keyAndPreconditionToString();
        }
    }
    hardAssert(false, "Unreachable");
    return null;
  }

  private boolean hasSameKeyAndPrecondition(Mutation other) {
    return key.equals(other.key) && precondition.equals(other.precondition);
  }

  private int keyAndPreconditionHashCode() {
    return getKey().hashCode() * 31 + precondition.hashCode();
  }

  private String keyAndPreconditionToString() {
    return "key=" + key + ", precondition=" + precondition;
  }

  private boolean hasSameObjectValue(Mutation other) {
    return (this.value == null && other.value == null)
        || (this.value != null && this.value.equals(other.value));
  }

  private int valueHashCode() {
    return value == null ? 0 : value.hashCode();
  }

  private boolean hasSameFieldMask(Mutation other) {
    return (this.mask == null && other.mask == null)
        || (this.mask != null && this.mask.equals(other.mask));
  }

  private int maskHashCode() {
    return mask == null ? 0 : mask.hashCode();
  }

  private boolean hasSameTransforms(Mutation other) {
    return (this.fieldTransforms == null && other.fieldTransforms == null)
        || (this.fieldTransforms != null && this.fieldTransforms.equals(other.fieldTransforms));
  }

  private int transformsHashCode() {
    return fieldTransforms == null ? 0 : fieldTransforms.hashCode();
  }

  /**
   * Applies this mutation to the given Document for the purposes of computing a new remote document
   * If the input document doesn't match the expected state (e.g. it is invalid or outdated), the
   * document state may transition to unknown.
   *
   * @param document The document to mutate.
   * @param mutationResult The result of applying the mutation from the backend.
   */
  public void applyToRemoteDocument(MutableDocument document, MutationResult mutationResult) {
    verifyKeyMatches(document);

    switch (type) {
      case DELETE:
        hardAssert(
            mutationResult.getTransformResults().isEmpty(),
            "Transform results received by Mutation.DELETE.");

        // Unlike applyToLocalView, if we're applying a mutation to a remote document the server has
        // accepted the mutation so the precondition must have held.

        // We store the deleted document at the commit version of the delete. Any document version
        // that the server sends us before the delete was applied is discarded
        document.convertToNoDocument(mutationResult.getVersion()).setHasCommittedMutations();
        break;

      case SET:
        {
          // Unlike applyToLocalView, if we're applying a mutation to a remote document the server
          // has
          // accepted the mutation so the precondition must have held.
          ObjectValue newData = value.clone();
          Map<FieldPath, Value> transformResults =
              serverTransformResults(document, mutationResult.getTransformResults());
          newData.setAll(transformResults);
          document
              .convertToFoundDocument(mutationResult.getVersion(), newData)
              .setHasCommittedMutations();
          break;
        }

      case PATCH:
        {
          if (!this.getPrecondition().isValidFor(document)) {
            // Since the mutation was not rejected, we know that the precondition matched on the
            // backend.
            // We therefore must not have the expected version of the document in our cache and
            // return an
            // UnknownDocument with the known updateTime.
            document.convertToUnknownDocument(mutationResult.getVersion());
            return;
          }

          Map<FieldPath, Value> transformResults =
              serverTransformResults(document, mutationResult.getTransformResults());
          ObjectValue value = document.getData();
          value.setAll(getPatch());
          value.setAll(transformResults);
          document
              .convertToFoundDocument(mutationResult.getVersion(), document.getData())
              .setHasCommittedMutations();
          break;
        }
      case VERIFY:
        {
          throw Assert.fail("VerifyMutation should only be used in Transactions.");
        }
    }
  }

  /**
   * Applies this mutation to the given Document for the purposes of computing the new local view of
   * a document. If the input document doesn't match the expected state, the document is not
   * modified.
   *
   * @param document The document to mutate.
   * @param localWriteTime A timestamp indicating the local write time of the batch this mutation is
   *     a part of.
   */
  public void applyToLocalView(MutableDocument document, Timestamp localWriteTime) {
    verifyKeyMatches(document);

    if (!this.getPrecondition().isValidFor(document)) {
      return;
    }

    switch (type) {
      case DELETE:
        {
          document.convertToNoDocument(SnapshotVersion.NONE);
          break;
        }
      case SET:
        {
          Map<FieldPath, Value> transformResults = localTransformResults(localWriteTime, document);
          ObjectValue localValue = value.clone();
          localValue.setAll(transformResults);
          document
              .convertToFoundDocument(getPostMutationVersion(document), localValue)
              .setHasLocalMutations();
          break;
        }
      case PATCH:
        {
          Map<FieldPath, Value> transformResults = localTransformResults(localWriteTime, document);
          ObjectValue value = document.getData();
          value.setAll(getPatch());
          value.setAll(transformResults);
          document
              .convertToFoundDocument(getPostMutationVersion(document), document.getData())
              .setHasLocalMutations();
          break;
        }
      case VERIFY:
        {
          throw Assert.fail("VerifyMutation should only be used in Transactions.");
        }
    }
  }

  private Map<FieldPath, Value> getPatch() {
    hardAssert(
        type.equals(MutationType.PATCH), "Trying to get patch values for a different mutation");
    Map<FieldPath, Value> result = new HashMap<>();
    for (FieldPath path : mask.getMask()) {
      if (!path.isEmpty()) {
        result.put(path, value.get(path));
      }
    }
    return result;
  }

  void verifyKeyMatches(MutableDocument document) {
    hardAssert(
        document.getKey().equals(getKey()),
        "Can only apply a mutation to a document with the same key");
  }

  /**
   * Returns the version from the given document for use as the result of a mutation. Mutations are
   * defined to return the version of the base document only if it is an existing document. Deleted
   * and unknown documents have a post-mutation version of {@code SnapshotVersion.NONE}.
   */
  private static SnapshotVersion getPostMutationVersion(MutableDocument document) {
    if (document.isFoundDocument()) {
      return document.getVersion();
    } else {
      return SnapshotVersion.NONE;
    }
  }

  /**
   * Creates a list of "transform results" (a transform result is a field value representing the
   * result of applying a transform) for use after a mutation containing transforms has been
   * acknowledged by the server.
   *
   * @param mutableDocument The current state of the document after applying all previous mutations.
   * @param serverTransformResults The transform results received by the server.
   * @return A map of fields to transform results.
   */
  private Map<FieldPath, Value> serverTransformResults(
      MutableDocument mutableDocument, List<Value> serverTransformResults) {
    Map<FieldPath, Value> transformResults = new HashMap<>(fieldTransforms.size());
    hardAssert(
        fieldTransforms.size() == serverTransformResults.size(),
        "server transform count (%d) should match field transform count (%d)",
        serverTransformResults.size(),
        fieldTransforms.size());

    for (int i = 0; i < serverTransformResults.size(); i++) {
      FieldTransform fieldTransform = fieldTransforms.get(i);
      TransformOperation transform = fieldTransform.getOperation();
      Value previousValue = mutableDocument.getField(fieldTransform.getFieldPath());
      transformResults.put(
          fieldTransform.getFieldPath(),
          transform.applyToRemoteDocument(previousValue, serverTransformResults.get(i)));
    }
    return transformResults;
  }

  /**
   * Creates a list of "transform results" (a transform result is a field value representing the
   * result of applying a transform) for use when applying a transform locally.
   *
   * @param localWriteTime The local time of the mutation (used to generate ServerTimestampValues).
   * @param mutableDocument The current state of the document after applying all previous mutations.
   * @return A map of fields to transform results.
   */
  private Map<FieldPath, Value> localTransformResults(
      Timestamp localWriteTime, MutableDocument mutableDocument) {
    Map<FieldPath, Value> transformResults = new HashMap<>(fieldTransforms.size());
    for (FieldTransform fieldTransform : fieldTransforms) {
      TransformOperation transform = fieldTransform.getOperation();
      Value previousValue = mutableDocument.getField(fieldTransform.getFieldPath());
      transformResults.put(
          fieldTransform.getFieldPath(), transform.applyToLocalView(previousValue, localWriteTime));
    }
    return transformResults;
  }

  public ObjectValue extractTransformBaseValue(Document document) {
    ObjectValue baseObject = null;

    for (FieldTransform transform : fieldTransforms) {
      Value existingValue = document.getField(transform.getFieldPath());
      Value coercedValue = transform.getOperation().computeBaseValue(existingValue);
      if (coercedValue != null) {
        if (baseObject == null) {
          baseObject = new ObjectValue();
        }
        baseObject.set(transform.getFieldPath(), coercedValue);
      }
    }

    return baseObject;
  }
}
