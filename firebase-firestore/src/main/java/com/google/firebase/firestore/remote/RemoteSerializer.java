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

import android.support.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.core.Bound;
import com.google.firebase.firestore.core.Filter;
import com.google.firebase.firestore.core.NaNFilter;
import com.google.firebase.firestore.core.NullFilter;
import com.google.firebase.firestore.core.OrderBy;
import com.google.firebase.firestore.core.OrderBy.Direction;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.RelationFilter;
import com.google.firebase.firestore.local.QueryData;
import com.google.firebase.firestore.local.QueryPurpose;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.NoDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
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
import com.google.firebase.firestore.model.mutation.TransformMutation;
import com.google.firebase.firestore.model.mutation.TransformOperation;
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
import com.google.firebase.firestore.remote.WatchChange.ExistenceFilterWatchChange;
import com.google.firebase.firestore.remote.WatchChange.WatchTargetChange;
import com.google.firebase.firestore.remote.WatchChange.WatchTargetChangeType;
import com.google.firebase.firestore.util.Assert;
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
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.StructuredQuery;
import com.google.firestore.v1.StructuredQuery.CollectionSelector;
import com.google.firestore.v1.StructuredQuery.CompositeFilter;
import com.google.firestore.v1.StructuredQuery.FieldFilter;
import com.google.firestore.v1.StructuredQuery.FieldReference;
import com.google.firestore.v1.StructuredQuery.Filter.FilterTypeCase;
import com.google.firestore.v1.StructuredQuery.Order;
import com.google.firestore.v1.StructuredQuery.UnaryFilter;
import com.google.firestore.v1.Target;
import com.google.firestore.v1.Target.DocumentsTarget;
import com.google.firestore.v1.Target.QueryTarget;
import com.google.firestore.v1.Value;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int32Value;
import com.google.type.LatLng;
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

  // GeoPoint

  private LatLng encodeGeoPoint(GeoPoint geoPoint) {
    return LatLng.newBuilder()
        .setLatitude(geoPoint.getLatitude())
        .setLongitude(geoPoint.getLongitude())
        .build();
  }

  private GeoPoint decodeGeoPoint(LatLng latLng) {
    return new GeoPoint(latLng.getLatitude(), latLng.getLongitude());
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
    // and commonly the (root) resource type, e.g. documents
    return path.length() >= 4
        && path.getSegment(0).equals("projects")
        && path.getSegment(2).equals("databases");
  }

  public String databaseName() {
    return databaseName;
  }

  // Values

  /**
   * Converts the FieldValue model passed into the Value proto equivalent.
   *
   * @param value the model to convert
   * @return The proto representation of the model
   */
  public com.google.firestore.v1.Value encodeValue(FieldValue value) {
    com.google.firestore.v1.Value.Builder builder = com.google.firestore.v1.Value.newBuilder();

    if (value instanceof NullValue) {
      builder.setNullValueValue(0);
      return builder.build();
    }

    Object encodedValue = value.value();
    hardAssert(encodedValue != null, "Encoded field value should not be null.");

    if (value instanceof BooleanValue) {
      builder.setBooleanValue((Boolean) encodedValue);
    } else if (value instanceof IntegerValue) {
      builder.setIntegerValue((Long) encodedValue);
    } else if (value instanceof DoubleValue) {
      builder.setDoubleValue((Double) encodedValue);
    } else if (value instanceof StringValue) {
      builder.setStringValue((String) encodedValue);
    } else if (value instanceof ArrayValue) {
      builder.setArrayValue(encodeArrayValue((ArrayValue) value));
    } else if (value instanceof ObjectValue) {
      builder.setMapValue(encodeMapValue((ObjectValue) value));
    } else if (value instanceof TimestampValue) {
      Timestamp t = ((TimestampValue) value).getInternalValue();
      builder.setTimestampValue(encodeTimestamp(t));
    } else if (value instanceof GeoPointValue) {
      GeoPoint geoPoint = (GeoPoint) encodedValue;
      builder.setGeoPointValue(encodeGeoPoint(geoPoint));
    } else if (value instanceof BlobValue) {
      builder.setBytesValue(((Blob) encodedValue).toByteString());
    } else if (value instanceof ReferenceValue) {
      ReferenceValue ref = (ReferenceValue) value;
      DatabaseId id = ref.getDatabaseId();
      DocumentKey key = (DocumentKey) encodedValue;
      builder.setReferenceValue(encodeResourceName(id, key.getPath()));
    } else {
      throw fail("Can't serialize %s", value);
    }

    return builder.build();
  }

  /**
   * Converts from the proto Value format to the model FieldValue format
   *
   * @return The model equivalent of the proto data.
   */
  public FieldValue decodeValue(com.google.firestore.v1.Value proto) {
    switch (proto.getValueTypeCase()) {
      case NULL_VALUE:
        return NullValue.nullValue();
      case BOOLEAN_VALUE:
        return BooleanValue.valueOf(proto.getBooleanValue());
      case INTEGER_VALUE:
        return IntegerValue.valueOf(proto.getIntegerValue());
      case DOUBLE_VALUE:
        return DoubleValue.valueOf(proto.getDoubleValue());
      case TIMESTAMP_VALUE:
        Timestamp timestamp = decodeTimestamp(proto.getTimestampValue());
        return TimestampValue.valueOf(timestamp);
      case GEO_POINT_VALUE:
        LatLng latLng = proto.getGeoPointValue();
        return GeoPointValue.valueOf(decodeGeoPoint(latLng));
      case BYTES_VALUE:
        ByteString bytes = proto.getBytesValue();
        return BlobValue.valueOf(Blob.fromByteString(bytes));
      case REFERENCE_VALUE:
        ResourcePath resourceName = decodeResourceName(proto.getReferenceValue());
        DatabaseId id =
            DatabaseId.forDatabase(resourceName.getSegment(1), resourceName.getSegment(3));
        DocumentKey key = DocumentKey.fromPath(extractLocalPathFromResourceName(resourceName));
        return ReferenceValue.valueOf(id, key);
      case STRING_VALUE:
        return StringValue.valueOf(proto.getStringValue());
      case ARRAY_VALUE:
        return decodeArrayValue(proto.getArrayValue());
      case MAP_VALUE:
        return decodeMapValue(proto.getMapValue());
      default:
        throw fail("Unknown value %s", proto);
    }
  }

  private com.google.firestore.v1.ArrayValue encodeArrayValue(ArrayValue value) {
    List<FieldValue> internalValue = value.getInternalValue();
    com.google.firestore.v1.ArrayValue.Builder arrayBuilder =
        com.google.firestore.v1.ArrayValue.newBuilder();
    for (FieldValue subValue : internalValue) {
      arrayBuilder.addValues(encodeValue(subValue));
    }
    return arrayBuilder.build();
  }

  private ArrayValue decodeArrayValue(com.google.firestore.v1.ArrayValue protoArray) {
    int count = protoArray.getValuesCount();
    List<FieldValue> wrappedList = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      wrappedList.add(decodeValue(protoArray.getValues(i)));
    }
    return ArrayValue.fromList(wrappedList);
  }

  private MapValue encodeMapValue(ObjectValue value) {
    MapValue.Builder builder = MapValue.newBuilder();
    for (Map.Entry<String, FieldValue> entry : value.getInternalValue()) {
      builder.putFields(entry.getKey(), encodeValue(entry.getValue()));
    }
    return builder.build();
  }

  private ObjectValue decodeMapValue(MapValue value) {
    return decodeFields(value.getFieldsMap());
  }

  // PORTING NOTE: There's no encodeFields here because there's no way to write it that doesn't
  // involve creating a temporary map.

  public ObjectValue decodeFields(Map<String, com.google.firestore.v1.Value> fields) {
    ObjectValue result = ObjectValue.emptyObject();
    for (Map.Entry<String, com.google.firestore.v1.Value> entry : fields.entrySet()) {
      FieldPath path = FieldPath.fromSingleSegment(entry.getKey());
      FieldValue value = decodeValue(entry.getValue());
      result = result.set(path, value);
    }
    return result;
  }

  // Documents

  public com.google.firestore.v1.Document encodeDocument(DocumentKey key, ObjectValue value) {
    com.google.firestore.v1.Document.Builder builder =
        com.google.firestore.v1.Document.newBuilder();
    builder.setName(encodeKey(key));
    for (Map.Entry<String, FieldValue> entry : value.getInternalValue()) {
      builder.putFields(entry.getKey(), encodeValue(entry.getValue()));
    }
    return builder.build();
  }

  public MaybeDocument decodeMaybeDocument(BatchGetDocumentsResponse response) {
    if (response.getResultCase().equals(ResultCase.FOUND)) {
      return decodeFoundDocument(response);
    } else if (response.getResultCase().equals(ResultCase.MISSING)) {
      return decodeMissingDocument(response);
    } else {
      throw new IllegalArgumentException("Unknown result case: " + response.getResultCase());
    }
  }

  private Document decodeFoundDocument(BatchGetDocumentsResponse response) {
    Assert.hardAssert(
        response.getResultCase().equals(ResultCase.FOUND),
        "Tried to deserialize a found document from a missing document.");
    DocumentKey key = decodeKey(response.getFound().getName());
    ObjectValue value = decodeFields(response.getFound().getFieldsMap());
    SnapshotVersion version = decodeVersion(response.getFound().getUpdateTime());
    hardAssert(
        !version.equals(SnapshotVersion.NONE), "Got a document response with no snapshot version");
    return new Document(key, version, value, Document.DocumentState.SYNCED, response.getFound());
  }

  private NoDocument decodeMissingDocument(BatchGetDocumentsResponse response) {
    Assert.hardAssert(
        response.getResultCase().equals(ResultCase.MISSING),
        "Tried to deserialize a missing document from a found document.");
    DocumentKey key = decodeKey(response.getMissing());
    SnapshotVersion version = decodeVersion(response.getReadTime());
    hardAssert(
        !version.equals(SnapshotVersion.NONE),
        "Got a no document response with no snapshot version");
    return new NoDocument(key, version, /*hasCommittedMutations=*/ false);
  }

  // Mutations

  /** Converts a Mutation model to a Write proto */
  public com.google.firestore.v1.Write encodeMutation(Mutation mutation) {
    com.google.firestore.v1.Write.Builder builder = com.google.firestore.v1.Write.newBuilder();
    if (mutation instanceof SetMutation) {
      builder.setUpdate(encodeDocument(mutation.getKey(), ((SetMutation) mutation).getValue()));
    } else if (mutation instanceof PatchMutation) {
      builder.setUpdate(encodeDocument(mutation.getKey(), ((PatchMutation) mutation).getValue()));
      builder.setUpdateMask(encodeDocumentMask(((PatchMutation) mutation).getMask()));
    } else if (mutation instanceof TransformMutation) {
      TransformMutation transform = (TransformMutation) mutation;
      DocumentTransform.Builder transformBuilder = DocumentTransform.newBuilder();
      transformBuilder.setDocument(encodeKey(transform.getKey()));
      for (FieldTransform fieldTransform : transform.getFieldTransforms()) {
        transformBuilder.addFieldTransforms(encodeFieldTransform(fieldTransform));
      }
      builder.setTransform(transformBuilder);
    } else if (mutation instanceof DeleteMutation) {
      builder.setDelete(encodeKey(mutation.getKey()));
    } else {
      throw fail("unknown mutation type %s", mutation.getClass());
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

    switch (mutation.getOperationCase()) {
      case UPDATE:
        if (mutation.hasUpdateMask()) {
          return new PatchMutation(
              decodeKey(mutation.getUpdate().getName()),
              decodeFields(mutation.getUpdate().getFieldsMap()),
              decodeDocumentMask(mutation.getUpdateMask()),
              precondition);
        } else {
          return new SetMutation(
              decodeKey(mutation.getUpdate().getName()),
              decodeFields(mutation.getUpdate().getFieldsMap()),
              precondition);
        }

      case DELETE:
        return new DeleteMutation(decodeKey(mutation.getDelete()), precondition);

      case TRANSFORM:
        ArrayList<FieldTransform> fieldTransforms = new ArrayList<>();
        for (DocumentTransform.FieldTransform fieldTransform :
            mutation.getTransform().getFieldTransformsList()) {
          fieldTransforms.add(decodeFieldTransform(fieldTransform));
        }
        Boolean exists = precondition.getExists();
        hardAssert(
            exists != null && exists, "Transforms only support precondition \"exists == true\"");
        return new TransformMutation(
            decodeKey(mutation.getTransform().getDocument()), fieldTransforms);

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
          .setAppendMissingElements(encodeArrayTransformElements(union.getElements()))
          .build();
    } else if (transform instanceof ArrayTransformOperation.Remove) {
      ArrayTransformOperation.Remove remove = (ArrayTransformOperation.Remove) transform;
      return DocumentTransform.FieldTransform.newBuilder()
          .setFieldPath(fieldTransform.getFieldPath().canonicalString())
          .setRemoveAllFromArray(encodeArrayTransformElements(remove.getElements()))
          .build();
    } else if (transform instanceof NumericIncrementTransformOperation) {
      NumericIncrementTransformOperation incrementOperation =
          (NumericIncrementTransformOperation) transform;
      return DocumentTransform.FieldTransform.newBuilder()
          .setFieldPath(fieldTransform.getFieldPath().canonicalString())
          .setIncrement(encodeValue(incrementOperation.getOperand()))
          .build();
    } else {
      throw fail("Unknown transform: %s", transform);
    }
  }

  private com.google.firestore.v1.ArrayValue encodeArrayTransformElements(
      List<FieldValue> elements) {
    com.google.firestore.v1.ArrayValue.Builder arrayBuilder =
        com.google.firestore.v1.ArrayValue.newBuilder();
    for (FieldValue subValue : elements) {
      arrayBuilder.addValues(encodeValue(subValue));
    }
    return arrayBuilder.build();
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
                decodeArrayTransformElements(fieldTransform.getAppendMissingElements())));
      case REMOVE_ALL_FROM_ARRAY:
        return new FieldTransform(
            FieldPath.fromServerFormat(fieldTransform.getFieldPath()),
            new ArrayTransformOperation.Remove(
                decodeArrayTransformElements(fieldTransform.getRemoveAllFromArray())));
      case INCREMENT:
        {
          FieldValue operand = decodeValue(fieldTransform.getIncrement());
          hardAssert(
              operand instanceof NumberValue,
              "Expected NUMERIC_ADD transform to be of number type, but was %s",
              operand.getClass().getCanonicalName());
          return new FieldTransform(
              FieldPath.fromServerFormat(fieldTransform.getFieldPath()),
              new NumericIncrementTransformOperation(
                  (NumberValue) decodeValue(fieldTransform.getIncrement())));
        }
      default:
        throw fail("Unknown FieldTransform proto: %s", fieldTransform);
    }
  }

  private List<FieldValue> decodeArrayTransformElements(
      com.google.firestore.v1.ArrayValue elementsProto) {
    int count = elementsProto.getValuesCount();
    List<FieldValue> result = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      result.add(decodeValue(elementsProto.getValues(i)));
    }
    return result;
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

    ArrayList<FieldValue> transformResults = null;
    int transformResultsCount = proto.getTransformResultsCount();
    if (transformResultsCount > 0) {
      transformResults = new ArrayList<>(transformResultsCount);
      for (int i = 0; i < transformResultsCount; i++) {
        transformResults.add(decodeValue(proto.getTransformResults(i)));
      }
    }
    return new MutationResult(version, transformResults);
  }

  // Queries

  @Nullable
  public Map<String, String> encodeListenRequestLabels(QueryData queryData) {
    @Nullable String value = encodeLabel(queryData.getPurpose());
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
      case LIMBO_RESOLUTION:
        return "limbo-document";
      default:
        throw fail("Unrecognized query purpose: %s", purpose);
    }
  }

  public Target encodeTarget(QueryData queryData) {
    Target.Builder builder = Target.newBuilder();
    Query query = queryData.getQuery();

    if (query.isDocumentQuery()) {
      builder.setDocuments(encodeDocumentsTarget(query));
    } else {
      builder.setQuery(encodeQueryTarget(query));
    }

    builder.setTargetId(queryData.getTargetId());
    builder.setResumeToken(queryData.getResumeToken());

    return builder.build();
  }

  public DocumentsTarget encodeDocumentsTarget(Query query) {
    DocumentsTarget.Builder builder = DocumentsTarget.newBuilder();
    builder.addDocuments(encodeQueryPath(query.getPath()));
    return builder.build();
  }

  public Query decodeDocumentsTarget(DocumentsTarget target) {
    int count = target.getDocumentsCount();
    hardAssert(count == 1, "DocumentsTarget contained other than 1 document %d", count);

    String name = target.getDocuments(0);
    return Query.atPath(decodeQueryPath(name));
  }

  public QueryTarget encodeQueryTarget(Query query) {
    // Dissect the path into parent, collectionId, and optional key filter.
    QueryTarget.Builder builder = QueryTarget.newBuilder();
    StructuredQuery.Builder structuredQueryBuilder = StructuredQuery.newBuilder();
    ResourcePath path = query.getPath();
    if (query.getCollectionGroup() != null) {
      Assert.hardAssert(
          path.length() % 2 == 0,
          "Collection Group queries should be within a document path or root.");
      builder.setParent(encodeQueryPath(path));
      CollectionSelector.Builder from = CollectionSelector.newBuilder();
      from.setCollectionId(query.getCollectionGroup());
      from.setAllDescendants(true);
      structuredQueryBuilder.addFrom(from);
    } else {
      Assert.hardAssert(path.length() % 2 != 0, "Document queries with filters are not supported.");
      builder.setParent(encodeQueryPath(path.popLast()));
      CollectionSelector.Builder from = CollectionSelector.newBuilder();
      from.setCollectionId(path.getLastSegment());
      structuredQueryBuilder.addFrom(from);
    }

    // Encode the filters.
    if (query.getFilters().size() > 0) {
      structuredQueryBuilder.setWhere(encodeFilters(query.getFilters()));
    }

    // Encode the orders.
    for (OrderBy orderBy : query.getOrderBy()) {
      structuredQueryBuilder.addOrderBy(encodeOrderBy(orderBy));
    }

    // Encode the limit.
    if (query.hasLimit()) {
      structuredQueryBuilder.setLimit(Int32Value.newBuilder().setValue((int) query.getLimit()));
    }

    if (query.getStartAt() != null) {
      structuredQueryBuilder.setStartAt(encodeBound(query.getStartAt()));
    }

    if (query.getEndAt() != null) {
      structuredQueryBuilder.setEndAt(encodeBound(query.getEndAt()));
    }

    builder.setStructuredQuery(structuredQueryBuilder);
    return builder.build();
  }

  public Query decodeQueryTarget(QueryTarget target) {
    ResourcePath path = decodeQueryPath(target.getParent());

    StructuredQuery query = target.getStructuredQuery();

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

    long limit = Query.NO_LIMIT;
    if (query.hasLimit()) {
      limit = query.getLimit().getValue();
    }

    Bound startAt = null;
    if (query.hasStartAt()) {
      startAt = decodeBound(query.getStartAt());
    }

    Bound endAt = null;
    if (query.hasEndAt()) {
      endAt = decodeBound(query.getEndAt());
    }

    return new Query(path, collectionGroup, filterBy, orderBy, limit, startAt, endAt);
  }

  // Filters

  private StructuredQuery.Filter encodeFilters(List<Filter> filters) {
    List<StructuredQuery.Filter> protos = new ArrayList<>(filters.size());
    for (Filter filter : filters) {
      if (filter instanceof RelationFilter) {
        protos.add(encodeRelationFilter((RelationFilter) filter));
      } else {
        protos.add(encodeUnaryFilter(filter));
      }
    }
    if (filters.size() == 1) {
      return protos.get(0);
    } else {
      CompositeFilter.Builder composite = CompositeFilter.newBuilder();
      composite.setOp(CompositeFilter.Operator.AND);
      composite.addAllFilters(protos);
      return StructuredQuery.Filter.newBuilder().setCompositeFilter(composite).build();
    }
  }

  private List<Filter> decodeFilters(StructuredQuery.Filter proto) {
    List<StructuredQuery.Filter> filters;
    if (proto.getFilterTypeCase() == FilterTypeCase.COMPOSITE_FILTER) {
      hardAssert(
          proto.getCompositeFilter().getOp() == CompositeFilter.Operator.AND,
          "Only AND-type composite filters are supported, got %d",
          proto.getCompositeFilter().getOp());
      filters = proto.getCompositeFilter().getFiltersList();
    } else {
      filters = Collections.singletonList(proto);
    }

    List<Filter> result = new ArrayList<>(filters.size());
    for (StructuredQuery.Filter filter : filters) {
      switch (filter.getFilterTypeCase()) {
        case COMPOSITE_FILTER:
          throw fail("Nested composite filters are not supported.");

        case FIELD_FILTER:
          result.add(decodeRelationFilter(filter.getFieldFilter()));
          break;

        case UNARY_FILTER:
          result.add(decodeUnaryFilter(filter.getUnaryFilter()));
          break;

        default:
          throw fail("Unrecognized Filter.filterType %d", filter.getFilterTypeCase());
      }
    }

    return result;
  }

  private StructuredQuery.Filter encodeRelationFilter(RelationFilter filter) {
    FieldFilter.Builder proto = FieldFilter.newBuilder();
    proto.setField(encodeFieldPath(filter.getField()));
    proto.setOp(encodeRelationFilterOperator(filter.getOperator()));
    proto.setValue(encodeValue(filter.getValue()));
    return StructuredQuery.Filter.newBuilder().setFieldFilter(proto).build();
  }

  private Filter decodeRelationFilter(StructuredQuery.FieldFilter proto) {
    FieldPath fieldPath = FieldPath.fromServerFormat(proto.getField().getFieldPath());
    RelationFilter.Operator filterOperator = decodeRelationFilterOperator(proto.getOp());
    FieldValue value = decodeValue(proto.getValue());
    return Filter.create(fieldPath, filterOperator, value);
  }

  private StructuredQuery.Filter encodeUnaryFilter(Filter filter) {
    UnaryFilter.Builder proto = UnaryFilter.newBuilder();
    proto.setField(encodeFieldPath(filter.getField()));
    if (filter instanceof NaNFilter) {
      proto.setOp(UnaryFilter.Operator.IS_NAN);
    } else if (filter instanceof NullFilter) {
      proto.setOp(UnaryFilter.Operator.IS_NULL);
    } else {
      throw fail("Unrecognized filter: %s", filter.getCanonicalId());
    }
    return StructuredQuery.Filter.newBuilder().setUnaryFilter(proto).build();
  }

  private Filter decodeUnaryFilter(StructuredQuery.UnaryFilter proto) {
    FieldPath fieldPath = FieldPath.fromServerFormat(proto.getField().getFieldPath());
    switch (proto.getOp()) {
      case IS_NAN:
        return new NaNFilter(fieldPath);

      case IS_NULL:
        return new NullFilter(fieldPath);

      default:
        throw fail("Unrecognized UnaryFilter.operator %d", proto.getOp());
    }
  }

  private FieldReference encodeFieldPath(FieldPath field) {
    return FieldReference.newBuilder().setFieldPath(field.canonicalString()).build();
  }

  private FieldFilter.Operator encodeRelationFilterOperator(RelationFilter.Operator operator) {
    switch (operator) {
      case LESS_THAN:
        return FieldFilter.Operator.LESS_THAN;
      case LESS_THAN_OR_EQUAL:
        return FieldFilter.Operator.LESS_THAN_OR_EQUAL;
      case EQUAL:
        return FieldFilter.Operator.EQUAL;
      case GREATER_THAN:
        return FieldFilter.Operator.GREATER_THAN;
      case GREATER_THAN_OR_EQUAL:
        return FieldFilter.Operator.GREATER_THAN_OR_EQUAL;
      case ARRAY_CONTAINS:
        return FieldFilter.Operator.ARRAY_CONTAINS;
      default:
        throw fail("Unknown operator %d", operator);
    }
  }

  private RelationFilter.Operator decodeRelationFilterOperator(FieldFilter.Operator operator) {
    switch (operator) {
      case LESS_THAN:
        return RelationFilter.Operator.LESS_THAN;
      case LESS_THAN_OR_EQUAL:
        return RelationFilter.Operator.LESS_THAN_OR_EQUAL;
      case EQUAL:
        return RelationFilter.Operator.EQUAL;
      case GREATER_THAN_OR_EQUAL:
        return RelationFilter.Operator.GREATER_THAN_OR_EQUAL;
      case GREATER_THAN:
        return RelationFilter.Operator.GREATER_THAN;
      case ARRAY_CONTAINS:
        return RelationFilter.Operator.ARRAY_CONTAINS;
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

  // Bounds

  private Cursor encodeBound(Bound bound) {
    Cursor.Builder builder = Cursor.newBuilder();
    builder.setBefore(bound.isBefore());
    for (FieldValue component : bound.getPosition()) {
      builder.addValues(encodeValue(component));
    }
    return builder.build();
  }

  private Bound decodeBound(Cursor proto) {
    int valuesCount = proto.getValuesCount();
    List<FieldValue> indexComponents = new ArrayList<>(valuesCount);

    for (int i = 0; i < valuesCount; i++) {
      Value valueProto = proto.getValues(i);
      indexComponents.add(decodeValue(valueProto));
    }
    return new Bound(indexComponents, proto.getBefore());
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
        ObjectValue data = decodeFields(docChange.getDocument().getFieldsMap());
        // The document may soon be re-serialized back to protos in order to store it in local
        // persistence. Memoize the encoded form to avoid encoding it again.
        Document document =
            new Document(
                key, version, data, Document.DocumentState.SYNCED, docChange.getDocument());
        watchChange = new WatchChange.DocumentChange(added, removed, document.getKey(), document);
        break;
      case DOCUMENT_DELETE:
        DocumentDelete docDelete = protoChange.getDocumentDelete();
        removed = docDelete.getRemovedTargetIdsList();
        key = decodeKey(docDelete.getDocument());
        // Note that version might be unset in which case we use SnapshotVersion.NONE
        version = decodeVersion(docDelete.getReadTime());
        NoDocument doc = new NoDocument(key, version, /*hasCommittedMutations=*/ false);
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
        // TODO: implement existence filter parsing (see b/33076578)
        ExistenceFilter filter = new ExistenceFilter(protoFilter.getCount());
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
    // and it applies to all targets (i.e. the list of targets is empty). The backend is guaranteed
    // to send such responses.
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
