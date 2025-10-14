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

package com.google.firebase.firestore.remote;

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.AggregateField;
import com.google.firebase.firestore.RealtimePipeline;
import com.google.firebase.firestore.UserDataReader;
import com.google.firebase.firestore.core.Bound;
import com.google.firebase.firestore.core.FieldFilter;
import com.google.firebase.firestore.core.Filter;
import com.google.firebase.firestore.core.OrderBy;
import com.google.firebase.firestore.core.OrderBy.Direction;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.core.TargetOrPipeline;
import com.google.firebase.firestore.local.QueryPurpose;
import com.google.firebase.firestore.local.TargetData;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.Values;
import com.google.firebase.firestore.model.mutation.ArrayTransformOperation;
import com.google.firebase.firestore.model.mutation.DeleteMutation;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firebase.firestore.model.mutation.FieldTransform;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationResult;
import com.google.firebase.firestore.model.mutation.NumericIncrementTransformOperation;
import com.google.firebase.firestore.model.mutation.PatchMutation;
import com.google.firebase.firestore.model.mutation.Precondition;
import com.google.firebase.firestore.model.mutation.ServerTimestampOperation;
import com.google.firebase.firestore.model.mutation.SetMutation;
import com.google.firebase.firestore.model.mutation.TransformOperation;
import com.google.firebase.firestore.model.mutation.VerifyMutation;
import com.google.firebase.firestore.pipeline.CollectionGroupSource;
import com.google.firebase.firestore.pipeline.CollectionSource;
import com.google.firebase.firestore.pipeline.DocumentsSource;
import com.google.firebase.firestore.pipeline.Expr;
import com.google.firebase.firestore.pipeline.Field;
import com.google.firebase.firestore.pipeline.FunctionExpr;
import com.google.firebase.firestore.pipeline.InternalOptions;
import com.google.firebase.firestore.pipeline.LimitStage;
import com.google.firebase.firestore.pipeline.Ordering;
import com.google.firebase.firestore.pipeline.SortStage;
import com.google.firebase.firestore.pipeline.Stage;
import com.google.firebase.firestore.pipeline.WhereStage;
import com.google.firebase.firestore.remote.WatchChange.ExistenceFilterWatchChange;
import com.google.firebase.firestore.remote.WatchChange.WatchTargetChange;
import com.google.firebase.firestore.remote.WatchChange.WatchTargetChangeType;
import com.google.firebase.firestore.util.Assert;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.BatchGetDocumentsResponse;
import com.google.firestore.v1.BatchGetDocumentsResponse.ResultCase;
import com.google.firestore.v1.Cursor;
import com.google.firestore.v1.DocumentChange;
import com.google.firestore.v1.DocumentDelete;
import com.google.firestore.v1.DocumentMask;
import com.google.firestore.v1.DocumentRemove;
import com.google.firestore.v1.DocumentTransform;
import com.google.firestore.v1.ListenResponse;
import com.google.firestore.v1.ListenResponse.ResponseTypeCase;
import com.google.firestore.v1.StructuredAggregationQuery;
import com.google.firestore.v1.StructuredQuery;
import com.google.firestore.v1.StructuredQuery.CollectionSelector;
import com.google.firestore.v1.StructuredQuery.CompositeFilter;
import com.google.firestore.v1.StructuredQuery.FieldReference;
import com.google.firestore.v1.StructuredQuery.Order;
import com.google.firestore.v1.StructuredQuery.UnaryFilter;
import com.google.firestore.v1.Target.DocumentsTarget;
import com.google.firestore.v1.Target.PipelineQueryTarget;
import com.google.firestore.v1.Target.QueryTarget;
import com.google.firestore.v1.Value;
import com.google.protobuf.Int32Value;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Serializer that converts to and from Firestore API protos. */
public final class RemoteSerializer {

  private final DatabaseId databaseId;
  private final String databaseName;

  public RemoteSerializer(DatabaseId databaseId) {
    this.databaseId = databaseId;
    databaseName = encodedDatabaseId(databaseId).canonicalString();
  }

  // Timestamps and Versions

  public com.google.protobuf.Timestamp encodeTimestamp(Timestamp timestamp) {
    com.google.protobuf.Timestamp.Builder builder = com.google.protobuf.Timestamp.newBuilder();
    builder.setSeconds(timestamp.getSeconds());
    builder.setNanos(timestamp.getNanoseconds());
    return builder.build();
  }

  public Timestamp decodeTimestamp(com.google.protobuf.Timestamp proto) {
    return new Timestamp(proto.getSeconds(), proto.getNanos());
  }

  public com.google.protobuf.Timestamp encodeVersion(SnapshotVersion version) {
    return encodeTimestamp(version.getTimestamp());
  }

  public SnapshotVersion decodeVersion(com.google.protobuf.Timestamp proto) {
    if (proto.getSeconds() == 0 && proto.getNanos() == 0) {
      return SnapshotVersion.NONE;
    } else {
      return new SnapshotVersion(decodeTimestamp(proto));
    }
  }

  // Names and Keys

  /**
   * Encodes the given document key as a fully qualified name. This includes the databaseId from the
   * constructor and the key path.
   */
  public String encodeKey(DocumentKey key) {
    return encodeResourceName(databaseId, key.getPath());
  }

  public DocumentKey decodeKey(String name) {
    ResourcePath resource = decodeResourceName(name);
    Assert.hardAssert(
        resource.getSegment(1).equals(databaseId.getProjectId()),
        "Tried to deserialize key from different project.");
    Assert.hardAssert(
        resource.getSegment(3).equals(databaseId.getDatabaseId()),
        "Tried to deserialize key from different database.");
    return DocumentKey.fromPath(extractLocalPathFromResourceName(resource));
  }

  private String encodeQueryPath(ResourcePath path) {
    return encodeResourceName(databaseId, path);
  }

  private ResourcePath decodeQueryPath(String name) {
    ResourcePath resource = decodeResourceName(name);
    if (resource.length() == 4) {
      // In v1beta1 queries for collections at the root did not have a trailing "/documents". In v1
      // all resource paths contain "/documents". Preserve the ability to read the v1 form for
      // compatibility with queries persisted in the local query cache.
      return ResourcePath.EMPTY;
    } else {
      return extractLocalPathFromResourceName(resource);
    }
  }

  /**
   * Encodes a databaseId and resource path into the following form: {@code
   * /projects/$projectId/database/$databaseId/documents/$path }
   */
  private String encodeResourceName(DatabaseId databaseId, ResourcePath path) {
    return encodedDatabaseId(databaseId).append("documents").append(path).canonicalString();
  }

  /**
   * Decodes a fully qualified resource name into a resource path and validates that there is a
   * project and database encoded in the path. There are no guarantees that a local path is also
   * encoded in this resource name.
   */
  private ResourcePath decodeResourceName(String encoded) {
    ResourcePath resource = ResourcePath.fromString(encoded);
    Assert.hardAssert(
        isValidResourceName(resource), "Tried to deserialize invalid key %s", resource);
    return resource;
  }

  /** Creates the prefix for a fully qualified resource path, without a local path on the end. */
  private static ResourcePath encodedDatabaseId(DatabaseId databaseId) {
    return ResourcePath.fromSegments(
        Arrays.asList(
            "projects", databaseId.getProjectId(), "databases", databaseId.getDatabaseId()));
  }

  /**
   * Decodes a fully qualified resource name into a resource path and validates that there is a
   * project and database encoded in the path along with a local path.
   */
  private static ResourcePath extractLocalPathFromResourceName(ResourcePath resourceName) {
    Assert.hardAssert(
        resourceName.length() > 4 && resourceName.getSegment(4).equals("documents"),
        "Tried to deserialize invalid key %s",
        resourceName);
    return resourceName.popFirst(5);
  }

  /** Validates that a path has a prefix that looks like a valid encoded databaseId. */
  private static boolean isValidResourceName(ResourcePath path) {
    // Resource names have at least 4 components (project ID, database ID)
    // and commonly the (root) resource type (for example, documents).
    return path.length() >= 4
        && path.getSegment(0).equals("projects")
        && path.getSegment(2).equals("databases");
  }

  /** Validates that a path has a prefix that belongs to the current database. */
  public boolean isLocalResourceName(ResourcePath path) {
    return isValidResourceName(path)
        && path.getSegment(1).equals(databaseId.getProjectId())
        && path.getSegment(3).equals(databaseId.getDatabaseId());
  }

  public String databaseName() {
    return databaseName;
  }

  public DatabaseId databaseId() {
    return databaseId;
  }

  // Documents

  public com.google.firestore.v1.Document encodeDocument(DocumentKey key, ObjectValue value) {
    com.google.firestore.v1.Document.Builder builder =
        com.google.firestore.v1.Document.newBuilder();
    builder.setName(encodeKey(key));
    builder.putAllFields(value.getFieldsMap());
    return builder.build();
  }

  public MutableDocument decodeMaybeDocument(BatchGetDocumentsResponse response) {
    if (response.getResultCase().equals(ResultCase.FOUND)) {
      return decodeFoundDocument(response);
    } else if (response.getResultCase().equals(ResultCase.MISSING)) {
      return decodeMissingDocument(response);
    } else {
      throw new IllegalArgumentException("Unknown result case: " + response.getResultCase());
    }
  }

  private MutableDocument decodeFoundDocument(BatchGetDocumentsResponse response) {
    Assert.hardAssert(
        response.getResultCase().equals(ResultCase.FOUND),
        "Tried to deserialize a found document from a missing document.");
    DocumentKey key = decodeKey(response.getFound().getName());
    ObjectValue value = ObjectValue.fromMap(response.getFound().getFieldsMap());
    SnapshotVersion version = decodeVersion(response.getFound().getUpdateTime());
    hardAssert(
        !version.equals(SnapshotVersion.NONE), "Got a document response with no snapshot version");
    return MutableDocument.newFoundDocument(key, version, value);
  }

  private MutableDocument decodeMissingDocument(BatchGetDocumentsResponse response) {
    Assert.hardAssert(
        response.getResultCase().equals(ResultCase.MISSING),
        "Tried to deserialize a missing document from a found document.");
    DocumentKey key = decodeKey(response.getMissing());
    SnapshotVersion version = decodeVersion(response.getReadTime());
    hardAssert(
        !version.equals(SnapshotVersion.NONE),
        "Got a no document response with no snapshot version");
    return MutableDocument.newNoDocument(key, version);
  }

  // Mutations

  /** Converts a Mutation model to a Write proto */
  public com.google.firestore.v1.Write encodeMutation(Mutation mutation) {
    com.google.firestore.v1.Write.Builder builder = com.google.firestore.v1.Write.newBuilder();
    if (mutation instanceof SetMutation) {
      builder.setUpdate(encodeDocument(mutation.getKey(), ((SetMutation) mutation).getValue()));
    } else if (mutation instanceof PatchMutation) {
      builder.setUpdate(encodeDocument(mutation.getKey(), ((PatchMutation) mutation).getValue()));
      builder.setUpdateMask(encodeDocumentMask((mutation.getFieldMask())));
    } else if (mutation instanceof DeleteMutation) {
      builder.setDelete(encodeKey(mutation.getKey()));
    } else if (mutation instanceof VerifyMutation) {
      builder.setVerify(encodeKey(mutation.getKey()));
    } else {
      throw fail("unknown mutation type %s", mutation.getClass());
    }

    for (FieldTransform fieldTransform : mutation.getFieldTransforms()) {
      builder.addUpdateTransforms(encodeFieldTransform(fieldTransform));
    }

    if (!mutation.getPrecondition().isNone()) {
      builder.setCurrentDocument(this.encodePrecondition(mutation.getPrecondition()));
    }
    return builder.build();
  }

  public Mutation decodeMutation(com.google.firestore.v1.Write mutation) {
    Precondition precondition =
        mutation.hasCurrentDocument()
            ? decodePrecondition(mutation.getCurrentDocument())
            : Precondition.NONE;

    List<FieldTransform> fieldTransforms = new ArrayList<>();
    for (DocumentTransform.FieldTransform fieldTransform : mutation.getUpdateTransformsList()) {
      fieldTransforms.add(decodeFieldTransform(fieldTransform));
    }

    switch (mutation.getOperationCase()) {
      case UPDATE:
        if (mutation.hasUpdateMask()) {
          return new PatchMutation(
              decodeKey(mutation.getUpdate().getName()),
              ObjectValue.fromMap(mutation.getUpdate().getFieldsMap()),
              decodeDocumentMask(mutation.getUpdateMask()),
              precondition,
              fieldTransforms);
        } else {
          return new SetMutation(
              decodeKey(mutation.getUpdate().getName()),
              ObjectValue.fromMap(mutation.getUpdate().getFieldsMap()),
              precondition,
              fieldTransforms);
        }

      case DELETE:
        return new DeleteMutation(decodeKey(mutation.getDelete()), precondition);

      case VERIFY:
        return new VerifyMutation(decodeKey(mutation.getVerify()), precondition);

      default:
        throw fail("Unknown mutation operation: %d", mutation.getOperationCase());
    }
  }

  private com.google.firestore.v1.Precondition encodePrecondition(Precondition precondition) {
    hardAssert(!precondition.isNone(), "Can't serialize an empty precondition");
    com.google.firestore.v1.Precondition.Builder builder =
        com.google.firestore.v1.Precondition.newBuilder();
    if (precondition.getUpdateTime() != null) {
      return builder.setUpdateTime(encodeVersion(precondition.getUpdateTime())).build();
    } else if (precondition.getExists() != null) {
      return builder.setExists(precondition.getExists()).build();
    } else {
      throw fail("Unknown Precondition");
    }
  }

  private Precondition decodePrecondition(com.google.firestore.v1.Precondition precondition) {
    switch (precondition.getConditionTypeCase()) {
      case UPDATE_TIME:
        return Precondition.updateTime(decodeVersion(precondition.getUpdateTime()));
      case EXISTS:
        return Precondition.exists(precondition.getExists());
      case CONDITIONTYPE_NOT_SET:
        return Precondition.NONE;
      default:
        throw fail("Unknown precondition");
    }
  }

  private DocumentMask encodeDocumentMask(FieldMask mask) {
    DocumentMask.Builder builder = DocumentMask.newBuilder();
    for (FieldPath path : mask.getMask()) {
      builder.addFieldPaths(path.canonicalString());
    }
    return builder.build();
  }

  private FieldMask decodeDocumentMask(DocumentMask mask) {
    int count = mask.getFieldPathsCount();
    Set<FieldPath> paths = new HashSet<>(count);
    for (int i = 0; i < count; i++) {
      paths.add(FieldPath.fromServerFormat(mask.getFieldPaths(i)));
    }
    return FieldMask.fromSet(paths);
  }

  private DocumentTransform.FieldTransform encodeFieldTransform(FieldTransform fieldTransform) {
    TransformOperation transform = fieldTransform.getOperation();
    if (transform instanceof ServerTimestampOperation) {
      return DocumentTransform.FieldTransform.newBuilder()
          .setFieldPath(fieldTransform.getFieldPath().canonicalString())
          .setSetToServerValue(DocumentTransform.FieldTransform.ServerValue.REQUEST_TIME)
          .build();
    } else if (transform instanceof ArrayTransformOperation.Union) {
      ArrayTransformOperation.Union union = (ArrayTransformOperation.Union) transform;
      return DocumentTransform.FieldTransform.newBuilder()
          .setFieldPath(fieldTransform.getFieldPath().canonicalString())
          .setAppendMissingElements(ArrayValue.newBuilder().addAllValues(union.getElements()))
          .build();
    } else if (transform instanceof ArrayTransformOperation.Remove) {
      ArrayTransformOperation.Remove remove = (ArrayTransformOperation.Remove) transform;
      return DocumentTransform.FieldTransform.newBuilder()
          .setFieldPath(fieldTransform.getFieldPath().canonicalString())
          .setRemoveAllFromArray(ArrayValue.newBuilder().addAllValues(remove.getElements()))
          .build();
    } else if (transform instanceof NumericIncrementTransformOperation) {
      NumericIncrementTransformOperation incrementOperation =
          (NumericIncrementTransformOperation) transform;
      return DocumentTransform.FieldTransform.newBuilder()
          .setFieldPath(fieldTransform.getFieldPath().canonicalString())
          .setIncrement(incrementOperation.getOperand())
          .build();
    } else {
      throw fail("Unknown transform: %s", transform);
    }
  }

  private FieldTransform decodeFieldTransform(DocumentTransform.FieldTransform fieldTransform) {
    switch (fieldTransform.getTransformTypeCase()) {
      case SET_TO_SERVER_VALUE:
        hardAssert(
            fieldTransform.getSetToServerValue()
                == DocumentTransform.FieldTransform.ServerValue.REQUEST_TIME,
            "Unknown transform setToServerValue: %s",
            fieldTransform.getSetToServerValue());
        return new FieldTransform(
            FieldPath.fromServerFormat(fieldTransform.getFieldPath()),
            ServerTimestampOperation.getInstance());
      case APPEND_MISSING_ELEMENTS:
        return new FieldTransform(
            FieldPath.fromServerFormat(fieldTransform.getFieldPath()),
            new ArrayTransformOperation.Union(
                fieldTransform.getAppendMissingElements().getValuesList()));
      case REMOVE_ALL_FROM_ARRAY:
        return new FieldTransform(
            FieldPath.fromServerFormat(fieldTransform.getFieldPath()),
            new ArrayTransformOperation.Remove(
                fieldTransform.getRemoveAllFromArray().getValuesList()));
      case INCREMENT:
        return new FieldTransform(
            FieldPath.fromServerFormat(fieldTransform.getFieldPath()),
            new NumericIncrementTransformOperation(fieldTransform.getIncrement()));
      default:
        throw fail("Unknown FieldTransform proto: %s", fieldTransform);
    }
  }

  public MutationResult decodeMutationResult(
      com.google.firestore.v1.WriteResult proto, SnapshotVersion commitVersion) {
    // NOTE: Deletes don't have an updateTime but the commit timestamp from the containing
    // CommitResponse or WriteResponse indicates essentially that the delete happened no later than
    // that. For our purposes we don't care exactly when the delete happened so long as we can tell
    // when an update on the watch stream is at or later than that change.
    SnapshotVersion version = decodeVersion(proto.getUpdateTime());
    if (SnapshotVersion.NONE.equals(version)) {
      version = commitVersion;
    }

    int transformResultsCount = proto.getTransformResultsCount();
    List<Value> transformResults = new ArrayList<>(transformResultsCount);
    for (int i = 0; i < transformResultsCount; i++) {
      transformResults.add(proto.getTransformResults(i));
    }
    return new MutationResult(version, transformResults);
  }

  // Queries

  @Nullable
  public Map<String, String> encodeListenRequestLabels(TargetData targetData) {
    @Nullable String value = encodeLabel(targetData.getPurpose());
    if (value == null) {
      return null;
    }

    Map<String, String> result = new HashMap<>(1);
    result.put("goog-listen-tags", value);
    return result;
  }

  @Nullable
  private String encodeLabel(QueryPurpose purpose) {
    switch (purpose) {
      case LISTEN:
        return null;
      case EXISTENCE_FILTER_MISMATCH:
        return "existence-filter-mismatch";
      case EXISTENCE_FILTER_MISMATCH_BLOOM:
        return "existence-filter-mismatch-bloom";
      case LIMBO_RESOLUTION:
        return "limbo-document";
      default:
        throw fail("Unrecognized query purpose: %s", purpose);
    }
  }

  public com.google.firestore.v1.Target encodeTarget(TargetData targetData) {
    com.google.firestore.v1.Target.Builder builder = com.google.firestore.v1.Target.newBuilder();
    TargetOrPipeline target = targetData.getTarget();

    if (target.isPipeline()) {
      PipelineQueryTarget.Builder pipelineBuilder = PipelineQueryTarget.newBuilder();
      builder.setPipelineQuery(
          pipelineBuilder.setStructuredPipeline(
              target.pipeline().toStructurePipelineProto$com_google_firebase_firebase_firestore()));
    } else if (target.target().isDocumentQuery()) {
      builder.setDocuments(encodeDocumentsTarget(target.target()));
    } else {
      builder.setQuery(encodeQueryTarget(target.target()));
    }

    builder.setTargetId(targetData.getTargetId());

    if (targetData.getResumeToken().isEmpty()
        && targetData.getSnapshotVersion().compareTo(SnapshotVersion.NONE) > 0) {
      // TODO(wuandy): Consider removing above check because it is most likely true. Right now, many
      // tests depend on this behaviour though (leaving min() out of serialization).
      builder.setReadTime(encodeTimestamp(targetData.getSnapshotVersion().getTimestamp()));
    } else {
      builder.setResumeToken(targetData.getResumeToken());
    }

    if (targetData.getExpectedCount() != null
        && (!targetData.getResumeToken().isEmpty()
            || targetData.getSnapshotVersion().compareTo(SnapshotVersion.NONE) > 0)) {
      builder.setExpectedCount(Int32Value.newBuilder().setValue(targetData.getExpectedCount()));
    }

    return builder.build();
  }

  public DocumentsTarget encodeDocumentsTarget(Target target) {
    DocumentsTarget.Builder builder = DocumentsTarget.newBuilder();
    builder.addDocuments(encodeQueryPath(target.getPath()));
    return builder.build();
  }

  public Target decodeDocumentsTarget(DocumentsTarget target) {
    int count = target.getDocumentsCount();
    hardAssert(count == 1, "DocumentsTarget contained other than 1 document %d", count);

    String name = target.getDocuments(0);
    return Query.atPath(decodeQueryPath(name)).toTarget();
  }

  public QueryTarget encodeQueryTarget(Target target) {
    // Dissect the path into parent, collectionId, and optional key filter.
    QueryTarget.Builder builder = QueryTarget.newBuilder();
    StructuredQuery.Builder structuredQueryBuilder = StructuredQuery.newBuilder();
    ResourcePath path = target.getPath();
    if (target.getCollectionGroup() != null) {
      hardAssert(
          path.length() % 2 == 0,
          "Collection Group queries should be within a document path or root.");
      builder.setParent(encodeQueryPath(path));
      CollectionSelector.Builder from = CollectionSelector.newBuilder();
      from.setCollectionId(target.getCollectionGroup());
      from.setAllDescendants(true);
      structuredQueryBuilder.addFrom(from);
    } else {
      hardAssert(path.length() % 2 != 0, "Document queries with filters are not supported.");
      builder.setParent(encodeQueryPath(path.popLast()));
      CollectionSelector.Builder from = CollectionSelector.newBuilder();
      from.setCollectionId(path.getLastSegment());
      structuredQueryBuilder.addFrom(from);
    }

    // Encode the filters.
    if (target.getFilters().size() > 0) {
      structuredQueryBuilder.setWhere(encodeFilters(target.getFilters()));
    }

    // Encode the orders.
    for (OrderBy orderBy : target.getOrderBy()) {
      structuredQueryBuilder.addOrderBy(encodeOrderBy(orderBy));
    }

    // Encode the limit.
    if (target.hasLimit()) {
      structuredQueryBuilder.setLimit(Int32Value.newBuilder().setValue((int) target.getLimit()));
    }

    if (target.getStartAt() != null) {
      Cursor.Builder cursor = Cursor.newBuilder();
      cursor.addAllValues(target.getStartAt().getPosition());
      cursor.setBefore(target.getStartAt().isInclusive());
      structuredQueryBuilder.setStartAt(cursor);
    }

    if (target.getEndAt() != null) {
      Cursor.Builder cursor = Cursor.newBuilder();
      cursor.addAllValues(target.getEndAt().getPosition());
      cursor.setBefore(!target.getEndAt().isInclusive());
      structuredQueryBuilder.setEndAt(cursor);
    }

    builder.setStructuredQuery(structuredQueryBuilder);
    return builder.build();
  }

  public Target decodeQueryTarget(String parent, StructuredQuery query) {
    ResourcePath path = decodeQueryPath(parent);

    String collectionGroup = null;
    int fromCount = query.getFromCount();
    if (fromCount > 0) {
      hardAssert(
          fromCount == 1, "StructuredQuery.from with more than one collection is not supported.");

      CollectionSelector from = query.getFrom(0);
      if (from.getAllDescendants()) {
        collectionGroup = from.getCollectionId();
      } else {
        path = path.append(from.getCollectionId());
      }
    }

    List<Filter> filterBy;
    if (query.hasWhere()) {
      filterBy = decodeFilters(query.getWhere());
    } else {
      filterBy = Collections.emptyList();
    }

    List<OrderBy> orderBy;
    int orderByCount = query.getOrderByCount();
    if (orderByCount > 0) {
      orderBy = new ArrayList<>(orderByCount);
      for (int i = 0; i < orderByCount; i++) {
        orderBy.add(decodeOrderBy(query.getOrderBy(i)));
      }
    } else {
      orderBy = Collections.emptyList();
    }

    long limit = Target.NO_LIMIT;
    if (query.hasLimit()) {
      limit = query.getLimit().getValue();
    }

    Bound startAt = null;
    if (query.hasStartAt()) {
      startAt = new Bound(query.getStartAt().getValuesList(), query.getStartAt().getBefore());
    }

    Bound endAt = null;
    if (query.hasEndAt()) {
      endAt = new Bound(query.getEndAt().getValuesList(), !query.getEndAt().getBefore());
    }

    return new Target(path, collectionGroup, filterBy, orderBy, limit, startAt, endAt);
  }

  public Target decodeQueryTarget(QueryTarget target) {
    return decodeQueryTarget(target.getParent(), target.getStructuredQuery());
  }

  public RealtimePipeline decodePipelineQueryTarget(PipelineQueryTarget proto) {
    hardAssert(
        proto.getPipelineTypeCase() == PipelineQueryTarget.PipelineTypeCase.STRUCTURED_PIPELINE,
        "Unknown pipeline_type in PipelineQueryTarget: " + proto.getPipelineTypeCase());

    com.google.firestore.v1.Pipeline pipelineProto = proto.getStructuredPipeline().getPipeline();
    List<Stage<?>> decodedStages = new ArrayList<>();
    for (com.google.firestore.v1.Pipeline.Stage stageProto : pipelineProto.getStagesList()) {
      decodedStages.add(decodeStage(stageProto));
    }

    // It is ok for firestore field to be null, because deserialzed realtime pipeline is only used
    // for facilitating pipeline request in remote store, firestore field is not used.
    return new RealtimePipeline(
        null, this, new UserDataReader(this.databaseId()), decodedStages, null);
  }

  private Stage<?> decodeStage(com.google.firestore.v1.Pipeline.Stage protoStage) {
    String stageName = protoStage.getName();
    List<Value> args = protoStage.getArgsList();

    switch (stageName) {
      case "collection":
        hardAssert(
            args.size() >= 1
                && args.get(0).getValueTypeCase() == Value.ValueTypeCase.REFERENCE_VALUE,
            "Invalid 'collection' stage: missing or invalid arguments");
        return new CollectionSource(
            ResourcePath.fromString(args.get(0).getReferenceValue()), this, InternalOptions.EMPTY);
      case "collection_group":
        hardAssert(
            args.size() >= 1 && args.get(0).getValueTypeCase() == Value.ValueTypeCase.STRING_VALUE,
            "Invalid 'collection_group' stage: missing or invalid arguments");
        return new CollectionGroupSource(args.get(0).getStringValue(), InternalOptions.EMPTY);
      case "documents":
        List<ResourcePath> documentPaths = new ArrayList<>();
        for (Value arg : args) {
          hardAssert(
              arg.getValueTypeCase() == Value.ValueTypeCase.REFERENCE_VALUE,
              "Invalid argument type for 'documents' stage: expected reference_value");
          documentPaths.add(ResourcePath.fromString(arg.getReferenceValue()));
        }
        return new DocumentsSource(
            documentPaths.toArray(new ResourcePath[0]), InternalOptions.EMPTY);
      case "where":
        hardAssert(args.size() >= 1, "Invalid 'where' stage: missing or invalid arguments");
        return new WhereStage(decodeExpression(args.get(0)), InternalOptions.EMPTY);
      case "limit":
        hardAssert(
            args.size() >= 1 && args.get(0).getValueTypeCase() == Value.ValueTypeCase.INTEGER_VALUE,
            "Invalid 'limit' stage: missing or invalid arguments");
        return new LimitStage((int) args.get(0).getIntegerValue(), InternalOptions.EMPTY);
      case "sort":
        hardAssert(args.size() > 0, "Invalid 'sort' stage: missing arguments");
        List<Ordering> orderings = new ArrayList<>();
        for (Value arg : args) {
          orderings.add(decodeOrdering(arg));
        }
        return new SortStage(orderings.toArray(new Ordering[0]), InternalOptions.EMPTY);
      default:
        throw new IllegalArgumentException("Unsupported stage type: " + stageName);
    }
  }

  private Expr decodeExpression(Value protoValue) {
    switch (protoValue.getValueTypeCase()) {
      case FIELD_REFERENCE_VALUE:
        return new Field(FieldPath.fromServerFormat(protoValue.getFieldReferenceValue()));
      case FUNCTION_VALUE:
        return decodeFunctionExpression(protoValue.getFunctionValue());
      default:
        return new Expr.Constant(protoValue);
    }
  }

  private FunctionExpr decodeFunctionExpression(com.google.firestore.v1.Function protoFunction) {
    String funcName = protoFunction.getName();
    List<Expr> decodedArgs = new ArrayList<>();
    for (Value arg : protoFunction.getArgsList()) {
      decodedArgs.add(decodeExpression(arg));
    }
    return new FunctionExpr(funcName, decodedArgs, InternalOptions.EMPTY);
  }

  private Ordering decodeOrdering(Value protoValue) {
    hardAssert(
        protoValue.getValueTypeCase() == Value.ValueTypeCase.MAP_VALUE,
        "Invalid proto_value type for Ordering, expected map_value.");

    Expr decodedExpr = null;
    Ordering.Direction decodedDirection = null;

    for (Map.Entry<String, Value> entry : protoValue.getMapValue().getFieldsMap().entrySet()) {
      String key = entry.getKey();
      Value value = entry.getValue();
      if (key.equals("expression")) {
        hardAssert(decodedExpr == null, "Duplicate 'expression' field in Ordering proto.");
        decodedExpr = decodeExpression(value);
      } else if (key.equals("direction")) {
        hardAssert(decodedDirection == null, "Duplicate 'direction' field in Ordering proto.");
        hardAssert(
            value.getValueTypeCase() == Value.ValueTypeCase.STRING_VALUE,
            "Invalid type for 'direction' field in Ordering proto, expected string_value.");
        String directionStr = value.getStringValue();
        if (directionStr.equals("ascending")) {
          decodedDirection = Ordering.Direction.ASCENDING;
        } else if (directionStr.equals("descending")) {
          decodedDirection = Ordering.Direction.DESCENDING;
        } else {
          throw new IllegalArgumentException(
              "Invalid string value '"
                  + directionStr
                  + "' for 'direction' field in Ordering proto.");
        }
      }
    }

    hardAssert(decodedExpr != null, "Missing 'expression' field in Ordering proto.");
    hardAssert(decodedDirection != null, "Missing 'direction' field in Ordering proto.");

    return new Ordering(decodedExpr, decodedDirection);
  }

  StructuredAggregationQuery encodeStructuredAggregationQuery(
      QueryTarget encodedQueryTarget,
      List<AggregateField> aggregateFields,
      HashMap<String, String> aliasMap) {
    StructuredAggregationQuery.Builder structuredAggregationQuery =
        StructuredAggregationQuery.newBuilder();
    structuredAggregationQuery.setStructuredQuery(encodedQueryTarget.getStructuredQuery());

    List<StructuredAggregationQuery.Aggregation> aggregations = new ArrayList<>();

    HashSet<String> uniqueFields = new HashSet<>();
    int aliasID = 1;
    for (AggregateField aggregateField : aggregateFields) {
      // The code block below is used to deduplicate the same aggregate fields.
      if (uniqueFields.contains(aggregateField.getAlias())) {
        continue;
      }
      uniqueFields.add(aggregateField.getAlias());

      String serverAlias = "aggregate_" + aliasID++;
      aliasMap.put(serverAlias, aggregateField.getAlias());

      StructuredAggregationQuery.Aggregation.Builder aggregation =
          StructuredAggregationQuery.Aggregation.newBuilder();
      StructuredQuery.FieldReference fieldPath =
          StructuredQuery.FieldReference.newBuilder()
              .setFieldPath(aggregateField.getFieldPath())
              .build();

      if (aggregateField instanceof AggregateField.CountAggregateField) {
        aggregation.setCount(StructuredAggregationQuery.Aggregation.Count.getDefaultInstance());
      } else if (aggregateField instanceof AggregateField.SumAggregateField) {
        aggregation.setSum(
            StructuredAggregationQuery.Aggregation.Sum.newBuilder().setField(fieldPath).build());
      } else if (aggregateField instanceof AggregateField.AverageAggregateField) {
        aggregation.setAvg(
            StructuredAggregationQuery.Aggregation.Avg.newBuilder().setField(fieldPath).build());
      } else {
        throw new RuntimeException("Unsupported aggregation");
      }

      aggregation.setAlias(serverAlias);
      aggregations.add(aggregation.build());
    }
    structuredAggregationQuery.addAllAggregations(aggregations);
    return structuredAggregationQuery.build();
  }

  // Filters

  private StructuredQuery.Filter encodeFilters(List<Filter> filters) {
    // A target's filter list is implicitly a composite AND filter.
    return encodeFilter(
        new com.google.firebase.firestore.core.CompositeFilter(
            filters, com.google.firebase.firestore.core.CompositeFilter.Operator.AND));
  }

  private List<Filter> decodeFilters(StructuredQuery.Filter proto) {
    Filter result = decodeFilter(proto);

    // Instead of a singletonList containing AND(F1, F2, ...), we can return a list containing F1,
    // F2, ... to stay consistent with the older SDK versions.
    if (result instanceof com.google.firebase.firestore.core.CompositeFilter) {
      com.google.firebase.firestore.core.CompositeFilter compositeFilter =
          (com.google.firebase.firestore.core.CompositeFilter) result;
      if (compositeFilter.isFlatConjunction()) {
        return compositeFilter.getFilters();
      }
    }

    return Collections.singletonList(result);
  }

  @VisibleForTesting
  StructuredQuery.Filter encodeFilter(com.google.firebase.firestore.core.Filter filter) {
    if (filter instanceof FieldFilter) {
      return encodeUnaryOrFieldFilter((FieldFilter) filter);
    } else if (filter instanceof com.google.firebase.firestore.core.CompositeFilter) {
      return encodeCompositeFilter((com.google.firebase.firestore.core.CompositeFilter) filter);
    } else {
      throw fail("Unrecognized filter type %s", filter.toString());
    }
  }

  @VisibleForTesting
  StructuredQuery.Filter encodeUnaryOrFieldFilter(FieldFilter filter) {
    FieldFilter.Operator op = filter.getOperator();
    Value value = filter.getValue();
    FieldReference fieldReference = encodeFieldPath(filter.getField());
    if (op == FieldFilter.Operator.EQUAL) {
      if (Values.isNanValue(value)) {
        return encodeUnaryFilter(fieldReference, UnaryFilter.Operator.IS_NAN);
      }
      if (Values.isNullValue(value)) {
        return encodeUnaryFilter(fieldReference, UnaryFilter.Operator.IS_NULL);
      }
    } else if (op == FieldFilter.Operator.NOT_EQUAL) {
      if (Values.isNanValue(value)) {
        return encodeUnaryFilter(fieldReference, UnaryFilter.Operator.IS_NOT_NAN);
      }
      if (Values.isNullValue(value)) {
        return encodeUnaryFilter(fieldReference, UnaryFilter.Operator.IS_NOT_NULL);
      }
    }
    StructuredQuery.FieldFilter.Builder proto = StructuredQuery.FieldFilter.newBuilder();
    proto.setField(fieldReference);
    proto.setOp(encodeFieldFilterOperator(op));
    proto.setValue(value);
    return StructuredQuery.Filter.newBuilder().setFieldFilter(proto).build();
  }

  private StructuredQuery.Filter encodeUnaryFilter(
      FieldReference fieldReference, UnaryFilter.Operator op) {
    UnaryFilter.Builder unaryProto = UnaryFilter.newBuilder();
    unaryProto.setField(fieldReference);
    unaryProto.setOp(op);
    return StructuredQuery.Filter.newBuilder().setUnaryFilter(unaryProto).build();
  }

  StructuredQuery.CompositeFilter.Operator encodeCompositeFilterOperator(
      com.google.firebase.firestore.core.CompositeFilter.Operator op) {
    switch (op) {
      case AND:
        return StructuredQuery.CompositeFilter.Operator.AND;
      case OR:
        return StructuredQuery.CompositeFilter.Operator.OR;
      default:
        throw fail("Unrecognized composite filter type.");
    }
  }

  com.google.firebase.firestore.core.CompositeFilter.Operator decodeCompositeFilterOperator(
      StructuredQuery.CompositeFilter.Operator op) {
    switch (op) {
      case AND:
        return com.google.firebase.firestore.core.CompositeFilter.Operator.AND;
      case OR:
        return com.google.firebase.firestore.core.CompositeFilter.Operator.OR;
      default:
        throw fail("Only AND and OR composite filter types are supported.");
    }
  }

  @VisibleForTesting
  StructuredQuery.Filter encodeCompositeFilter(
      com.google.firebase.firestore.core.CompositeFilter compositeFilter) {
    List<StructuredQuery.Filter> protos = new ArrayList<>(compositeFilter.getFilters().size());
    for (Filter filter : compositeFilter.getFilters()) {
      protos.add(encodeFilter(filter));
    }

    // If there's only one filter in the composite filter, use it directly.
    if (protos.size() == 1) {
      return protos.get(0);
    }

    CompositeFilter.Builder composite = CompositeFilter.newBuilder();
    composite.setOp(encodeCompositeFilterOperator(compositeFilter.getOperator()));
    composite.addAllFilters(protos);
    return StructuredQuery.Filter.newBuilder().setCompositeFilter(composite).build();
  }

  @VisibleForTesting
  Filter decodeFilter(StructuredQuery.Filter proto) {
    switch (proto.getFilterTypeCase()) {
      case COMPOSITE_FILTER:
        return decodeCompositeFilter(proto.getCompositeFilter());
      case FIELD_FILTER:
        return decodeFieldFilter(proto.getFieldFilter());
      case UNARY_FILTER:
        return decodeUnaryFilter(proto.getUnaryFilter());
      default:
        throw fail("Unrecognized Filter.filterType %d", proto.getFilterTypeCase());
    }
  }

  @VisibleForTesting
  FieldFilter decodeFieldFilter(StructuredQuery.FieldFilter proto) {
    FieldPath fieldPath = FieldPath.fromServerFormat(proto.getField().getFieldPath());
    FieldFilter.Operator filterOperator = decodeFieldFilterOperator(proto.getOp());
    return FieldFilter.create(fieldPath, filterOperator, proto.getValue());
  }

  private Filter decodeUnaryFilter(StructuredQuery.UnaryFilter proto) {
    FieldPath fieldPath = FieldPath.fromServerFormat(proto.getField().getFieldPath());
    switch (proto.getOp()) {
      case IS_NAN:
        return FieldFilter.create(fieldPath, FieldFilter.Operator.EQUAL, Values.NAN_VALUE);
      case IS_NULL:
        return FieldFilter.create(fieldPath, FieldFilter.Operator.EQUAL, Values.NULL_VALUE);
      case IS_NOT_NAN:
        return FieldFilter.create(fieldPath, FieldFilter.Operator.NOT_EQUAL, Values.NAN_VALUE);
      case IS_NOT_NULL:
        return FieldFilter.create(fieldPath, FieldFilter.Operator.NOT_EQUAL, Values.NULL_VALUE);
      default:
        throw fail("Unrecognized UnaryFilter.operator %d", proto.getOp());
    }
  }

  @VisibleForTesting
  com.google.firebase.firestore.core.CompositeFilter decodeCompositeFilter(
      StructuredQuery.CompositeFilter compositeFilter) {
    List<Filter> filters = new ArrayList<>();
    for (StructuredQuery.Filter filter : compositeFilter.getFiltersList()) {
      filters.add(decodeFilter(filter));
    }
    return new com.google.firebase.firestore.core.CompositeFilter(
        filters, decodeCompositeFilterOperator(compositeFilter.getOp()));
  }

  private FieldReference encodeFieldPath(FieldPath field) {
    return FieldReference.newBuilder().setFieldPath(field.canonicalString()).build();
  }

  private StructuredQuery.FieldFilter.Operator encodeFieldFilterOperator(
      FieldFilter.Operator operator) {
    switch (operator) {
      case LESS_THAN:
        return StructuredQuery.FieldFilter.Operator.LESS_THAN;
      case LESS_THAN_OR_EQUAL:
        return StructuredQuery.FieldFilter.Operator.LESS_THAN_OR_EQUAL;
      case EQUAL:
        return StructuredQuery.FieldFilter.Operator.EQUAL;
      case NOT_EQUAL:
        return StructuredQuery.FieldFilter.Operator.NOT_EQUAL;
      case GREATER_THAN:
        return StructuredQuery.FieldFilter.Operator.GREATER_THAN;
      case GREATER_THAN_OR_EQUAL:
        return StructuredQuery.FieldFilter.Operator.GREATER_THAN_OR_EQUAL;
      case ARRAY_CONTAINS:
        return StructuredQuery.FieldFilter.Operator.ARRAY_CONTAINS;
      case IN:
        return StructuredQuery.FieldFilter.Operator.IN;
      case ARRAY_CONTAINS_ANY:
        return StructuredQuery.FieldFilter.Operator.ARRAY_CONTAINS_ANY;
      case NOT_IN:
        return StructuredQuery.FieldFilter.Operator.NOT_IN;
      default:
        throw fail("Unknown operator %d", operator);
    }
  }

  private FieldFilter.Operator decodeFieldFilterOperator(
      StructuredQuery.FieldFilter.Operator operator) {
    switch (operator) {
      case LESS_THAN:
        return FieldFilter.Operator.LESS_THAN;
      case LESS_THAN_OR_EQUAL:
        return FieldFilter.Operator.LESS_THAN_OR_EQUAL;
      case EQUAL:
        return FieldFilter.Operator.EQUAL;
      case NOT_EQUAL:
        return FieldFilter.Operator.NOT_EQUAL;
      case GREATER_THAN_OR_EQUAL:
        return FieldFilter.Operator.GREATER_THAN_OR_EQUAL;
      case GREATER_THAN:
        return FieldFilter.Operator.GREATER_THAN;
      case ARRAY_CONTAINS:
        return FieldFilter.Operator.ARRAY_CONTAINS;
      case IN:
        return FieldFilter.Operator.IN;
      case ARRAY_CONTAINS_ANY:
        return FieldFilter.Operator.ARRAY_CONTAINS_ANY;
      case NOT_IN:
        return FieldFilter.Operator.NOT_IN;
      default:
        throw fail("Unhandled FieldFilter.operator %d", operator);
    }
  }

  // Property orders

  private Order encodeOrderBy(OrderBy orderBy) {
    Order.Builder proto = Order.newBuilder();
    if (orderBy.getDirection().equals(Direction.ASCENDING)) {
      proto.setDirection(StructuredQuery.Direction.ASCENDING);
    } else {
      proto.setDirection(StructuredQuery.Direction.DESCENDING);
    }
    proto.setField(encodeFieldPath(orderBy.getField()));
    return proto.build();
  }

  private OrderBy decodeOrderBy(Order proto) {
    FieldPath fieldPath = FieldPath.fromServerFormat(proto.getField().getFieldPath());
    OrderBy.Direction direction;
    switch (proto.getDirection()) {
      case ASCENDING:
        direction = Direction.ASCENDING;
        break;
      case DESCENDING:
        direction = Direction.DESCENDING;
        break;
      default:
        throw fail("Unrecognized direction %d", proto.getDirection());
    }
    return OrderBy.getInstance(direction, fieldPath);
  }

  // Watch changes

  public WatchChange decodeWatchChange(ListenResponse protoChange) {
    WatchChange watchChange;

    switch (protoChange.getResponseTypeCase()) {
      case TARGET_CHANGE:
        com.google.firestore.v1.TargetChange targetChange = protoChange.getTargetChange();
        WatchTargetChangeType changeType;
        Status cause = null;
        switch (targetChange.getTargetChangeType()) {
          case NO_CHANGE:
            changeType = WatchTargetChangeType.NoChange;
            break;
          case ADD:
            changeType = WatchTargetChangeType.Added;
            break;
          case REMOVE:
            changeType = WatchTargetChangeType.Removed;
            cause = fromStatus(targetChange.getCause());
            break;
          case CURRENT:
            changeType = WatchTargetChangeType.Current;
            break;
          case RESET:
            changeType = WatchTargetChangeType.Reset;
            break;
          case UNRECOGNIZED:
          default:
            throw new IllegalArgumentException("Unknown target change type");
        }
        watchChange =
            new WatchTargetChange(
                changeType, targetChange.getTargetIdsList(), targetChange.getResumeToken(), cause);
        break;
      case DOCUMENT_CHANGE:
        DocumentChange docChange = protoChange.getDocumentChange();
        List<Integer> added = docChange.getTargetIdsList();
        List<Integer> removed = docChange.getRemovedTargetIdsList();
        DocumentKey key = decodeKey(docChange.getDocument().getName());
        SnapshotVersion version = decodeVersion(docChange.getDocument().getUpdateTime());
        hardAssert(
            !version.equals(SnapshotVersion.NONE), "Got a document change without an update time");
        ObjectValue data = ObjectValue.fromMap(docChange.getDocument().getFieldsMap());
        MutableDocument document = MutableDocument.newFoundDocument(key, version, data);
        watchChange = new WatchChange.DocumentChange(added, removed, document.getKey(), document);
        break;
      case DOCUMENT_DELETE:
        DocumentDelete docDelete = protoChange.getDocumentDelete();
        removed = docDelete.getRemovedTargetIdsList();
        key = decodeKey(docDelete.getDocument());
        // Note that version might be unset in which case we use SnapshotVersion.NONE
        version = decodeVersion(docDelete.getReadTime());
        MutableDocument doc = MutableDocument.newNoDocument(key, version);
        watchChange =
            new WatchChange.DocumentChange(Collections.emptyList(), removed, doc.getKey(), doc);
        break;
      case DOCUMENT_REMOVE:
        DocumentRemove docRemove = protoChange.getDocumentRemove();
        removed = docRemove.getRemovedTargetIdsList();
        key = decodeKey(docRemove.getDocument());
        watchChange = new WatchChange.DocumentChange(Collections.emptyList(), removed, key, null);
        break;
      case FILTER:
        com.google.firestore.v1.ExistenceFilter protoFilter = protoChange.getFilter();
        ExistenceFilter filter =
            new ExistenceFilter(protoFilter.getCount(), protoFilter.getUnchangedNames());
        int targetId = protoFilter.getTargetId();
        watchChange = new ExistenceFilterWatchChange(targetId, filter);
        break;
      case RESPONSETYPE_NOT_SET:
      default:
        throw new IllegalArgumentException("Unknown change type set");
    }

    return watchChange;
  }

  public SnapshotVersion decodeVersionFromListenResponse(ListenResponse watchChange) {
    // We have only reached a consistent snapshot for the entire stream if there is a read_time set
    // and it applies to all targets (specifically, the list of targets is empty). The backend is
    // guaranteed to send such responses.
    if (watchChange.getResponseTypeCase() != ResponseTypeCase.TARGET_CHANGE) {
      return SnapshotVersion.NONE;
    }
    if (watchChange.getTargetChange().getTargetIdsCount() != 0) {
      return SnapshotVersion.NONE;
    }
    return decodeVersion(watchChange.getTargetChange().getReadTime());
  }

  private Status fromStatus(com.google.rpc.Status status) {
    // TODO: Use details?
    return Status.fromCodeValue(status.getCode()).withDescription(status.getMessage());
  }
}
