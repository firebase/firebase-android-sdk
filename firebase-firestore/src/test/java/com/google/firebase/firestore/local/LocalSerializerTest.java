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

import static com.google.firebase.firestore.testutil.Assert.assertThrows;
import static com.google.firebase.firestore.testutil.TestUtil.deleteMutation;
import static com.google.firebase.firestore.testutil.TestUtil.deletedDoc;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.fieldMask;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static com.google.firebase.firestore.testutil.TestUtil.unknownDoc;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.NoDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.UnknownDocument;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.firebase.firestore.model.mutation.PatchMutation;
import com.google.firebase.firestore.model.mutation.SetMutation;
import com.google.firebase.firestore.proto.WriteBatch;
import com.google.firebase.firestore.remote.RemoteSerializer;
import com.google.firebase.firestore.testutil.TestUtil;
import com.google.firestore.v1.DocumentMask;
import com.google.firestore.v1.DocumentTransform;
import com.google.firestore.v1.DocumentTransform.FieldTransform;
import com.google.firestore.v1.Precondition;
import com.google.firestore.v1.Value;
import com.google.firestore.v1.Write;
import com.google.firestore.v1.Write.Builder;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Add a test for serializer. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class LocalSerializerTest {
  private RemoteSerializer remoteSerializer;
  private LocalSerializer serializer;

  private Timestamp writeTime = Timestamp.now();
  private com.google.protobuf.Timestamp writeTimeProto =
      com.google.protobuf.Timestamp.newBuilder()
          .setSeconds(writeTime.getSeconds())
          .setNanos(writeTime.getNanoseconds())
          .build();

  static class TestWriteBuilder {
    private Builder builder;

    TestWriteBuilder() {
      this.builder = Write.newBuilder();
    }

    TestWriteBuilder addSet() {
      builder.setUpdate(
          com.google.firestore.v1.Document.newBuilder()
              .setName("projects/p/databases/d/documents/foo/bar")
              .putFields("a", Value.newBuilder().setStringValue("b").build())
              .putFields("num", Value.newBuilder().setIntegerValue(1).build()));
      return this;
    }

    TestWriteBuilder addPatch() {
      builder
          .setUpdate(
              com.google.firestore.v1.Document.newBuilder()
                  .setName("projects/p/databases/d/documents/bar/baz")
                  .putFields("a", Value.newBuilder().setStringValue("b").build())
                  .putFields("num", Value.newBuilder().setIntegerValue(1).build()))
          .setUpdateMask(DocumentMask.newBuilder().addFieldPaths("a"))
          .setCurrentDocument(Precondition.newBuilder().setExists(true));
      return this;
    }

    TestWriteBuilder addDelete() {
      builder.setDelete("projects/p/databases/d/documents/baz/quux");
      return this;
    }

    TestWriteBuilder addUpdateTransforms() {
      builder
          .addUpdateTransforms(
              FieldTransform.newBuilder()
                  .setFieldPath("integer")
                  .setIncrement(Value.newBuilder().setIntegerValue(42)))
          .addUpdateTransforms(
              FieldTransform.newBuilder()
                  .setFieldPath("double")
                  .setIncrement(Value.newBuilder().setDoubleValue(13.37)));
      return this;
    }

    TestWriteBuilder addLegacyTransform() {
      builder
          .setTransform(
              DocumentTransform.newBuilder()
                  .setDocument("projects/p/databases/d/documents/docs/1")
                  .addFieldTransforms(
                      FieldTransform.newBuilder()
                          .setFieldPath("integer")
                          .setIncrement(Value.newBuilder().setIntegerValue(42).build()))
                  .addFieldTransforms(
                      FieldTransform.newBuilder()
                          .setFieldPath("double")
                          .setIncrement(Value.newBuilder().setDoubleValue(13.37).build())))
          .setCurrentDocument(Precondition.newBuilder().setExists(true));
      return this;
    }

    Write build() {
      return builder.build();
    }
  }

  Write setProto = new TestWriteBuilder().addSet().build();
  Write patchProto = new TestWriteBuilder().addPatch().build();
  Write deleteProto = new TestWriteBuilder().addDelete().build();
  Write transformProto = new TestWriteBuilder().addLegacyTransform().build();

  @Before
  public void setUp() {
    DatabaseId databaseId = DatabaseId.forDatabase("p", "d");
    remoteSerializer = new RemoteSerializer(databaseId);
    serializer = new LocalSerializer(remoteSerializer);
  }

  // TODO(b/174608374): Remove these tests once we perform a schema migration.
  @Test
  public void testSetMutationAndTransFormMutationAreSquashed() {
    WriteBatch batchProto =
        com.google.firebase.firestore.proto.WriteBatch.newBuilder()
            .setBatchId(42)
            .addAllWrites(asList(setProto, transformProto))
            .setLocalWriteTime(writeTimeProto)
            .build();

    MutationBatch decoded = serializer.decodeMutationBatch(batchProto);
    assertEquals(1, decoded.getMutations().size());
    assertTrue(decoded.getMutations().get(0) instanceof SetMutation);
    Write encoded = remoteSerializer.encodeMutation(decoded.getMutations().get(0));
    Write expected = new TestWriteBuilder().addSet().addUpdateTransforms().build();
    assertEquals(expected, encoded);
  }

  // TODO(b/174608374): Remove these tests once we perform a schema migration.
  @Test
  public void testPatchMutationAndTransFormMutationAreSquashed() {
    WriteBatch batchProto =
        com.google.firebase.firestore.proto.WriteBatch.newBuilder()
            .setBatchId(42)
            .addAllWrites(asList(patchProto, transformProto))
            .setLocalWriteTime(writeTimeProto)
            .build();

    MutationBatch decoded = serializer.decodeMutationBatch(batchProto);
    assertEquals(1, decoded.getMutations().size());
    assertTrue(decoded.getMutations().get(0) instanceof PatchMutation);
    Write encoded = remoteSerializer.encodeMutation(decoded.getMutations().get(0));
    Write expected = new TestWriteBuilder().addPatch().addUpdateTransforms().build();
    assertEquals(expected, encoded);
  }

  // TODO(b/174608374): Remove these tests once we perform a schema migration.
  @Test
  public void testTransformAndTransformThrowError() {
    WriteBatch batchProto =
        com.google.firebase.firestore.proto.WriteBatch.newBuilder()
            .setBatchId(42)
            .addAllWrites(asList(transformProto, transformProto))
            .setLocalWriteTime(writeTimeProto)
            .build();
    assertThrows(AssertionError.class, () -> serializer.decodeMutationBatch(batchProto));
  }

  // TODO(b/174608374): Remove these tests once we perform a schema migration.
  @Test
  public void testOnlyTransformThrowsError() {
    WriteBatch batchProto =
        com.google.firebase.firestore.proto.WriteBatch.newBuilder()
            .setBatchId(42)
            .addAllWrites(asList(transformProto))
            .setLocalWriteTime(writeTimeProto)
            .build();
    assertThrows(AssertionError.class, () -> serializer.decodeMutationBatch(batchProto));
  }

  // TODO(b/174608374): Remove these tests once we perform a schema migration.
  @Test
  public void testDeleteAndTransformThrowError() {
    WriteBatch batchProto =
        com.google.firebase.firestore.proto.WriteBatch.newBuilder()
            .setBatchId(42)
            .addAllWrites(asList(deleteProto, transformProto))
            .setLocalWriteTime(writeTimeProto)
            .build();
    assertThrows(AssertionError.class, () -> serializer.decodeMutationBatch(batchProto));
  }

  // TODO(b/174608374): Remove these tests once we perform a schema migration.
  @Test
  public void testMultipleMutationsAreSquashed() {
    // INPUT:
    // SetMutation -> SetMutation -> TransformMutation -> DeleteMutation -> PatchMutation ->
    // TransformMutation -> PatchMutation
    // OUTPUT (squashed):
    // SetMutation -> SetMutation -> DeleteMutation -> PatchMutation -> PatchMutation
    WriteBatch batchProto =
        com.google.firebase.firestore.proto.WriteBatch.newBuilder()
            .setBatchId(42)
            .addAllWrites(
                asList(
                    setProto,
                    setProto,
                    transformProto,
                    deleteProto,
                    patchProto,
                    transformProto,
                    patchProto))
            .setLocalWriteTime(writeTimeProto)
            .build();

    MutationBatch decoded = serializer.decodeMutationBatch(batchProto);
    assertEquals(5, decoded.getMutations().size());
    List<Write> allExpected =
        asList(
            new TestWriteBuilder().addSet().build(),
            new TestWriteBuilder().addSet().addUpdateTransforms().build(),
            new TestWriteBuilder().addDelete().build(),
            new TestWriteBuilder().addPatch().addUpdateTransforms().build(),
            new TestWriteBuilder().addPatch().build());
    for (int i = 0; i < decoded.getMutations().size(); i++) {
      Mutation mutation = decoded.getMutations().get(i);
      Write encoded = remoteSerializer.encodeMutation(mutation);
      assertEquals(allExpected.get(i), encoded);
    }
  }

  @Test
  public void testEncodesMutationBatch() {
    Mutation baseWrite =
        new PatchMutation(
            key("foo/bar"),
            TestUtil.wrapObject(map("a", "b")),
            FieldMask.fromSet(Collections.singleton(field("a"))),
            com.google.firebase.firestore.model.mutation.Precondition.NONE);
    Mutation set = setMutation("foo/bar", map("a", "b", "num", 1));
    Mutation patch =
        new PatchMutation(
            key("bar/baz"),
            TestUtil.wrapObject(map("a", "b", "num", 1)),
            fieldMask("a"),
            com.google.firebase.firestore.model.mutation.Precondition.exists(true));
    Mutation del = deleteMutation("baz/quux");
    MutationBatch model =
        new MutationBatch(
            42, writeTime, Collections.singletonList(baseWrite), asList(set, patch, del));

    Write baseWriteProto =
        Write.newBuilder()
            .setUpdate(
                com.google.firestore.v1.Document.newBuilder()
                    .setName("projects/p/databases/d/documents/foo/bar")
                    .putFields("a", Value.newBuilder().setStringValue("b").build()))
            .setUpdateMask(DocumentMask.newBuilder().addFieldPaths("a"))
            .build();

    com.google.firebase.firestore.proto.WriteBatch batchProto =
        com.google.firebase.firestore.proto.WriteBatch.newBuilder()
            .setBatchId(42)
            .addBaseWrites(baseWriteProto)
            .addAllWrites(asList(setProto, patchProto, deleteProto))
            .setLocalWriteTime(writeTimeProto)
            .build();

    assertEquals(batchProto, serializer.encodeMutationBatch(model));
    MutationBatch decoded = serializer.decodeMutationBatch(batchProto);
    assertEquals(model.getBatchId(), decoded.getBatchId());
    assertEquals(model.getLocalWriteTime(), decoded.getLocalWriteTime());
    assertEquals(model.getMutations(), decoded.getMutations());
    assertEquals(model.getBaseMutations(), decoded.getBaseMutations());
    assertEquals(model.getKeys(), decoded.getKeys());
  }

  @Test
  public void testEncodesDocumentAsMaybeDocument() {
    Document document = doc("some/path", 42, map("foo", "bar"));

    com.google.firebase.firestore.proto.MaybeDocument maybeDocProto =
        com.google.firebase.firestore.proto.MaybeDocument.newBuilder()
            .setDocument(
                com.google.firestore.v1.Document.newBuilder()
                    .setName("projects/p/databases/d/documents/some/path")
                    .putFields("foo", Value.newBuilder().setStringValue("bar").build())
                    .setUpdateTime(
                        com.google.protobuf.Timestamp.newBuilder().setSeconds(0).setNanos(42000)))
            .build();

    assertEquals(maybeDocProto, serializer.encodeMaybeDocument(document));
    MaybeDocument decoded = serializer.decodeMaybeDocument(maybeDocProto);
    assertEquals(document, decoded);
  }

  @Test
  public void testEncodesDeletedDocumentAsMaybeDocument() {
    NoDocument deletedDoc = deletedDoc("some/path", 42);

    com.google.firebase.firestore.proto.MaybeDocument maybeDocProto =
        com.google.firebase.firestore.proto.MaybeDocument.newBuilder()
            .setNoDocument(
                com.google.firebase.firestore.proto.NoDocument.newBuilder()
                    .setName("projects/p/databases/d/documents/some/path")
                    .setReadTime(
                        com.google.protobuf.Timestamp.newBuilder().setSeconds(0).setNanos(42000)))
            .build();

    assertEquals(maybeDocProto, serializer.encodeMaybeDocument(deletedDoc));
    MaybeDocument decoded = serializer.decodeMaybeDocument(maybeDocProto);
    assertEquals(deletedDoc, decoded);
  }

  @Test
  public void testEncodesUnknownDocumentAsMaybeDocument() {
    UnknownDocument unknownDoc = unknownDoc("some/path", 42);

    com.google.firebase.firestore.proto.MaybeDocument maybeDocProto =
        com.google.firebase.firestore.proto.MaybeDocument.newBuilder()
            .setUnknownDocument(
                com.google.firebase.firestore.proto.UnknownDocument.newBuilder()
                    .setName("projects/p/databases/d/documents/some/path")
                    .setVersion(
                        com.google.protobuf.Timestamp.newBuilder().setSeconds(0).setNanos(42000)))
            .setHasCommittedMutations(true)
            .build();

    assertEquals(maybeDocProto, serializer.encodeMaybeDocument(unknownDoc));
    MaybeDocument decoded = serializer.decodeMaybeDocument(maybeDocProto);
    assertEquals(unknownDoc, decoded);
  }

  @Test
  public void testEncodesTargetData() {
    Query query = TestUtil.query("room");
    int targetId = 42;
    long sequenceNumber = 10;
    SnapshotVersion snapshotVersion = TestUtil.version(1039);
    SnapshotVersion limboFreeVersion = TestUtil.version(1000);
    ByteString resumeToken = TestUtil.resumeToken(1039);

    TargetData targetData =
        new TargetData(
            query.toTarget(),
            targetId,
            sequenceNumber,
            QueryPurpose.LISTEN,
            snapshotVersion,
            limboFreeVersion,
            resumeToken);

    // Let the RPC serializer test various permutations of query serialization.
    com.google.firestore.v1.Target.QueryTarget queryTarget =
        remoteSerializer.encodeQueryTarget(query.toTarget());

    com.google.firebase.firestore.proto.Target expected =
        com.google.firebase.firestore.proto.Target.newBuilder()
            .setTargetId(targetId)
            .setLastListenSequenceNumber(sequenceNumber)
            .setSnapshotVersion(com.google.protobuf.Timestamp.newBuilder().setNanos(1039000))
            .setResumeToken(ByteString.copyFrom(resumeToken.toByteArray()))
            .setQuery(
                com.google.firestore.v1.Target.QueryTarget.newBuilder()
                    .setParent(queryTarget.getParent())
                    .setStructuredQuery(queryTarget.getStructuredQuery()))
            .setLastLimboFreeSnapshotVersion(
                com.google.protobuf.Timestamp.newBuilder().setNanos(1000000))
            .build();

    assertEquals(expected, serializer.encodeTargetData(targetData));
    TargetData decoded = serializer.decodeTargetData(expected);
    assertEquals(targetData, decoded);
  }
}
