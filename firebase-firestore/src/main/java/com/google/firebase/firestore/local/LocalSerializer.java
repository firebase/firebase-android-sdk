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

package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.NoDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.UnknownDocument;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.ObjectValue;
import com.google.firebase.firestore.remote.RemoteSerializer;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Serializer for values stored in the LocalStore. */
public final class LocalSerializer {

  private final RemoteSerializer rpcSerializer;

  public LocalSerializer(RemoteSerializer rpcSerializer) {
    this.rpcSerializer = rpcSerializer;
  }

  /** Encodes a MaybeDocument model to the equivalent protocol buffer for local storage. */
  com.google.firebase.firestore.proto.MaybeDocument encodeMaybeDocument(MaybeDocument document) {
    com.google.firebase.firestore.proto.MaybeDocument.Builder builder =
        com.google.firebase.firestore.proto.MaybeDocument.newBuilder();

    if (document instanceof NoDocument) {
      NoDocument noDocument = (NoDocument) document;
      builder.setNoDocument(encodeNoDocument(noDocument));
      builder.setHasCommittedMutations(noDocument.hasCommittedMutations());
    } else if (document instanceof Document) {
      Document existingDocument = (Document) document;
      // Use the memoized encoded form if it exists.
      if (existingDocument.getProto() != null) {
        builder.setDocument(existingDocument.getProto());
      } else {
        builder.setDocument(encodeDocument(existingDocument));
      }
      builder.setHasCommittedMutations(existingDocument.hasCommittedMutations());
    } else if (document instanceof UnknownDocument) {
      builder.setUnknownDocument(encodeUnknownDocument((UnknownDocument) document));
      builder.setHasCommittedMutations(true);
    } else {
      throw fail("Unknown document type %s", document.getClass().getCanonicalName());
    }

    return builder.build();
  }

  /** Decodes a MaybeDocument proto to the equivalent model. */
  MaybeDocument decodeMaybeDocument(com.google.firebase.firestore.proto.MaybeDocument proto) {
    switch (proto.getDocumentTypeCase()) {
      case DOCUMENT:
        return decodeDocument(proto.getDocument(), proto.getHasCommittedMutations());

      case NO_DOCUMENT:
        return decodeNoDocument(proto.getNoDocument(), proto.getHasCommittedMutations());

      case UNKNOWN_DOCUMENT:
        return decodeUnknownDocument(proto.getUnknownDocument());

      default:
        throw fail("Unknown MaybeDocument %s", proto);
    }
  }

  /**
   * Encodes a Document for local storage. This differs from the v1 RPC serializer for Documents in
   * that it preserves the updateTime, which is considered an output only value by the server.
   */
  private com.google.firestore.v1.Document encodeDocument(Document document) {
    com.google.firestore.v1.Document.Builder builder =
        com.google.firestore.v1.Document.newBuilder();
    builder.setName(rpcSerializer.encodeKey(document.getKey()));

    ObjectValue value = document.getData();
    for (Map.Entry<String, FieldValue> entry : value.getInternalValue()) {
      builder.putFields(entry.getKey(), rpcSerializer.encodeValue(entry.getValue()));
    }

    Timestamp updateTime = document.getVersion().getTimestamp();
    builder.setUpdateTime(rpcSerializer.encodeTimestamp(updateTime));
    return builder.build();
  }

  /** Decodes a Document proto to the equivalent model. */
  private Document decodeDocument(
      com.google.firestore.v1.Document document, boolean hasCommittedMutations) {
    DocumentKey key = rpcSerializer.decodeKey(document.getName());
    ObjectValue value = rpcSerializer.decodeFields(document.getFieldsMap());
    SnapshotVersion version = rpcSerializer.decodeVersion(document.getUpdateTime());
    return new Document(
        key,
        version,
        value,
        hasCommittedMutations
            ? Document.DocumentState.COMMITTED_MUTATIONS
            : Document.DocumentState.SYNCED);
  }

  /** Encodes a NoDocument value to the equivalent proto. */
  private com.google.firebase.firestore.proto.NoDocument encodeNoDocument(NoDocument document) {
    com.google.firebase.firestore.proto.NoDocument.Builder builder =
        com.google.firebase.firestore.proto.NoDocument.newBuilder();
    builder.setName(rpcSerializer.encodeKey(document.getKey()));
    builder.setReadTime(rpcSerializer.encodeTimestamp(document.getVersion().getTimestamp()));
    return builder.build();
  }

  /** Decodes a NoDocument proto to the equivalent model. */
  private NoDocument decodeNoDocument(
      com.google.firebase.firestore.proto.NoDocument proto, boolean hasCommittedMutations) {
    DocumentKey key = rpcSerializer.decodeKey(proto.getName());
    SnapshotVersion version = rpcSerializer.decodeVersion(proto.getReadTime());
    return new NoDocument(key, version, hasCommittedMutations);
  }

  /** Encodes a UnknownDocument value to the equivalent proto. */
  private com.google.firebase.firestore.proto.UnknownDocument encodeUnknownDocument(
      UnknownDocument document) {
    com.google.firebase.firestore.proto.UnknownDocument.Builder builder =
        com.google.firebase.firestore.proto.UnknownDocument.newBuilder();
    builder.setName(rpcSerializer.encodeKey(document.getKey()));
    builder.setVersion(rpcSerializer.encodeTimestamp(document.getVersion().getTimestamp()));
    return builder.build();
  }

  /** Decodes a UnknownDocument proto to the equivalent model. */
  private UnknownDocument decodeUnknownDocument(
      com.google.firebase.firestore.proto.UnknownDocument proto) {
    DocumentKey key = rpcSerializer.decodeKey(proto.getName());
    SnapshotVersion version = rpcSerializer.decodeVersion(proto.getVersion());
    return new UnknownDocument(key, version);
  }

  /** Encodes a MutationBatch model for local storage in the mutation queue. */
  com.google.firebase.firestore.proto.WriteBatch encodeMutationBatch(MutationBatch batch) {
    com.google.firebase.firestore.proto.WriteBatch.Builder result =
        com.google.firebase.firestore.proto.WriteBatch.newBuilder();

    result.setBatchId(batch.getBatchId());
    result.setLocalWriteTime(rpcSerializer.encodeTimestamp(batch.getLocalWriteTime()));
    for (Mutation mutation : batch.getBaseMutations()) {
      result.addBaseWrites(rpcSerializer.encodeMutation(mutation));
    }
    for (Mutation mutation : batch.getMutations()) {
      result.addWrites(rpcSerializer.encodeMutation(mutation));
    }
    return result.build();
  }

  /** Decodes a WriteBatch proto into a MutationBatch model. */
  MutationBatch decodeMutationBatch(com.google.firebase.firestore.proto.WriteBatch batch) {
    int batchId = batch.getBatchId();
    Timestamp localWriteTime = rpcSerializer.decodeTimestamp(batch.getLocalWriteTime());

    int baseMutationsCount = batch.getBaseWritesCount();
    List<Mutation> baseMutations = new ArrayList<>(baseMutationsCount);
    for (int i = 0; i < baseMutationsCount; i++) {
      baseMutations.add(rpcSerializer.decodeMutation(batch.getBaseWrites(i)));
    }
    int mutationsCount = batch.getWritesCount();
    List<Mutation> mutations = new ArrayList<>(mutationsCount);
    for (int i = 0; i < mutationsCount; i++) {
      mutations.add(rpcSerializer.decodeMutation(batch.getWrites(i)));
    }
    return new MutationBatch(batchId, localWriteTime, baseMutations, mutations);
  }

  com.google.firebase.firestore.proto.Target encodeQueryData(QueryData queryData) {
    hardAssert(
        QueryPurpose.LISTEN.equals(queryData.getPurpose()),
        "Only queries with purpose %s may be stored, got %s",
        QueryPurpose.LISTEN,
        queryData.getPurpose());

    com.google.firebase.firestore.proto.Target.Builder result =
        com.google.firebase.firestore.proto.Target.newBuilder();

    result
        .setTargetId(queryData.getTargetId())
        .setLastListenSequenceNumber(queryData.getSequenceNumber())
        .setSnapshotVersion(rpcSerializer.encodeVersion(queryData.getSnapshotVersion()))
        .setResumeToken(queryData.getResumeToken());

    Query query = queryData.getQuery();
    if (query.isDocumentQuery()) {
      result.setDocuments(rpcSerializer.encodeDocumentsTarget(query));
    } else {
      result.setQuery(rpcSerializer.encodeQueryTarget(query));
    }

    return result.build();
  }

  QueryData decodeQueryData(com.google.firebase.firestore.proto.Target target) {
    int targetId = target.getTargetId();
    SnapshotVersion version = rpcSerializer.decodeVersion(target.getSnapshotVersion());
    ByteString resumeToken = target.getResumeToken();
    long sequenceNumber = target.getLastListenSequenceNumber();

    Query query;
    switch (target.getTargetTypeCase()) {
      case DOCUMENTS:
        query = rpcSerializer.decodeDocumentsTarget(target.getDocuments());
        break;

      case QUERY:
        query = rpcSerializer.decodeQueryTarget(target.getQuery());
        break;

      default:
        throw fail("Unknown targetType %d", target.getTargetTypeCase());
    }

    return new QueryData(
        query, targetId, sequenceNumber, QueryPurpose.LISTEN, version, resumeToken);
  }
}
