// Copyright 2025 Google LLC
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

package com.google.firebase.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.RealtimePipeline.ListenOptions
import com.google.firebase.firestore.pipeline.Expression.Companion.abs
import com.google.firebase.firestore.pipeline.Expression.Companion.add
import com.google.firebase.firestore.pipeline.Expression.Companion.and
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayContains
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayContainsAny
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayLength
import com.google.firebase.firestore.pipeline.Expression.Companion.byteLength
import com.google.firebase.firestore.pipeline.Expression.Companion.ceil
import com.google.firebase.firestore.pipeline.Expression.Companion.charLength
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.divide
import com.google.firebase.firestore.pipeline.Expression.Companion.endsWith
import com.google.firebase.firestore.pipeline.Expression.Companion.equalAny
import com.google.firebase.firestore.pipeline.Expression.Companion.exists
import com.google.firebase.firestore.pipeline.Expression.Companion.exp
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.floor
import com.google.firebase.firestore.pipeline.Expression.Companion.isAbsent
import com.google.firebase.firestore.pipeline.Expression.Companion.like
import com.google.firebase.firestore.pipeline.Expression.Companion.ln
import com.google.firebase.firestore.pipeline.Expression.Companion.log
import com.google.firebase.firestore.pipeline.Expression.Companion.log10
import com.google.firebase.firestore.pipeline.Expression.Companion.mod
import com.google.firebase.firestore.pipeline.Expression.Companion.multiply
import com.google.firebase.firestore.pipeline.Expression.Companion.not
import com.google.firebase.firestore.pipeline.Expression.Companion.notEqual
import com.google.firebase.firestore.pipeline.Expression.Companion.notEqualAny
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.Expression.Companion.or
import com.google.firebase.firestore.pipeline.Expression.Companion.pow
import com.google.firebase.firestore.pipeline.Expression.Companion.regexContains
import com.google.firebase.firestore.pipeline.Expression.Companion.regexFind
import com.google.firebase.firestore.pipeline.Expression.Companion.regexFindAll
import com.google.firebase.firestore.pipeline.Expression.Companion.regexMatch
import com.google.firebase.firestore.pipeline.Expression.Companion.reverse
import com.google.firebase.firestore.pipeline.Expression.Companion.round
import com.google.firebase.firestore.pipeline.Expression.Companion.sqrt
import com.google.firebase.firestore.pipeline.Expression.Companion.startsWith
import com.google.firebase.firestore.pipeline.Expression.Companion.stringConcat
import com.google.firebase.firestore.pipeline.Expression.Companion.stringContains
import com.google.firebase.firestore.pipeline.Expression.Companion.subtract
import com.google.firebase.firestore.pipeline.Expression.Companion.timestampAdd
import com.google.firebase.firestore.pipeline.Expression.Companion.timestampToUnixMicros
import com.google.firebase.firestore.pipeline.Expression.Companion.timestampToUnixMillis
import com.google.firebase.firestore.pipeline.Expression.Companion.timestampToUnixSeconds
import com.google.firebase.firestore.pipeline.Expression.Companion.toLower
import com.google.firebase.firestore.pipeline.Expression.Companion.toUpper
import com.google.firebase.firestore.pipeline.Expression.Companion.trim
import com.google.firebase.firestore.pipeline.Expression.Companion.unixMicrosToTimestamp
import com.google.firebase.firestore.pipeline.Expression.Companion.unixMillisToTimestamp
import com.google.firebase.firestore.pipeline.Expression.Companion.unixSecondsToTimestamp
import com.google.firebase.firestore.pipeline.Expression.Companion.xor
import com.google.firebase.firestore.pipeline.Ordering.Companion.ascending
import com.google.firebase.firestore.testutil.IntegrationTestUtil
import com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor
import com.google.firebase.firestore.testutil.IntegrationTestUtil.writeAllDocs
import com.google.firebase.firestore.util.Util.autoId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

private val bookDocs: Map<String, Map<String, Any>> =
  mapOf(
    "book1" to
      mapOf(
        "title" to "The Hitchhiker's Guide to the Galaxy",
        "author" to "Douglas Adams",
        "genre" to "Science Fiction",
        "published" to 1979,
        "rating" to 4.2,
        "tags" to listOf("comedy", "space", "adventure"),
        "awards" to mapOf("hugo" to true, "nebula" to false),
        "nestedField" to mapOf("level.1" to mapOf("level.2" to true)),
      ),
    "book2" to
      mapOf(
        "title" to "Pride and Prejudice",
        "author" to "Jane Austen",
        "genre" to "Romance",
        "published" to 1813,
        "rating" to 4.5,
        "tags" to listOf("classic", "social commentary", "love"),
        "awards" to mapOf("none" to true),
      ),
    "book3" to
      mapOf(
        "title" to "One Hundred Years of Solitude",
        "author" to "Gabriel García Márquez",
        "genre" to "Magical Realism",
        "published" to 1967,
        "rating" to 4.3,
        "tags" to listOf("family", "history", "fantasy"),
        "awards" to mapOf("nobel" to true, "nebula" to false),
      ),
    "book4" to
      mapOf(
        "title" to "The Lord of the Rings",
        "author" to "J.R.R. Tolkien",
        "genre" to "Fantasy",
        "published" to 1954,
        "rating" to 4.7,
        "tags" to listOf("adventure", "magic", "epic"),
        "awards" to mapOf("hugo" to false, "nebula" to false),
      ),
    "book5" to
      mapOf(
        "title" to "The Handmaid's Tale",
        "author" to "Margaret Atwood",
        "genre" to "Dystopian",
        "published" to 1985,
        "rating" to 4.1,
        "tags" to listOf("feminism", "totalitarianism", "resistance"),
        "awards" to mapOf("arthur c. clarke" to true, "booker prize" to false),
      ),
    "book6" to
      mapOf(
        "title" to "Crime and Punishment",
        "author" to "Fyodor Dostoevsky",
        "genre" to "Psychological Thriller",
        "published" to 1866,
        "rating" to 4.3,
        "tags" to listOf("philosophy", "crime", "redemption"),
        "awards" to mapOf("none" to true),
      ),
    "book7" to
      mapOf(
        "title" to "To Kill a Mockingbird",
        "author" to "Harper Lee",
        "genre" to "Southern Gothic",
        "published" to 1960,
        "rating" to 4.2,
        "tags" to listOf("racism", "injustice", "coming-of-age"),
        "awards" to mapOf("pulitzer" to true),
      ),
    "book8" to
      mapOf(
        "title" to "1984",
        "author" to "George Orwell",
        "genre" to "Dystopian",
        "published" to 1949,
        "rating" to 4.2,
        "tags" to listOf("surveillance", "totalitarianism", "propaganda"),
        "awards" to mapOf("prometheus" to true),
      ),
    "book9" to
      mapOf(
        "title" to "The Great Gatsby",
        "author" to "F. Scott Fitzgerald",
        "genre" to "Modernist",
        "published" to 1925,
        "rating" to 4.0,
        "tags" to listOf("wealth", "american dream", "love"),
        "awards" to mapOf("none" to true),
      ),
    "book10" to
      mapOf(
        "title" to "Dune",
        "author" to "Frank Herbert",
        "genre" to "Science Fiction",
        "published" to 1965,
        "rating" to 4.6,
        "tags" to listOf("politics", "desert", "ecology"),
        "awards" to mapOf("hugo" to true, "nebula" to true),
      ),
  )

private val eventDocs: Map<String, Map<String, Any>> =
  mapOf(
    "event1" to
      mapOf(
        "name" to "Test Event",
        "timestamp" to Timestamp(1698228000, 0), // 2023-10-26T10:00:00Z
        "unix_seconds" to 1698228000L,
        "unix_millis" to 1698228000000L,
        "unix_micros" to 1698228000000000L
      )
  )

@RunWith(AndroidJUnit4::class)
class RealtimePipelineTest {
  private lateinit var db: FirebaseFirestore
  private lateinit var collRef: CollectionReference
  private lateinit var eventCollRef: CollectionReference

  @Before
  fun setUp() {
    org.junit.Assume.assumeTrue(
      "Skip RealtimePipelineTest on prod",
      IntegrationTestUtil.isRunningAgainstEmulator()
    )

    org.junit.Assume.assumeTrue(
      "Skip RealtimePipelineTest on standard backend",
      IntegrationTestUtil.getBackendEdition() == IntegrationTestUtil.BackendEdition.ENTERPRISE
    )

    collRef = IntegrationTestUtil.testCollection()
    db = collRef.firestore
    eventCollRef = db.collection(autoId())

    writeAllDocs(collRef, bookDocs)
    writeAllDocs(eventCollRef, eventDocs)
  }

  @After
  fun tearDown() {
    IntegrationTestUtil.tearDown()
  }

  @Test
  fun testBasicAsyncStream() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(field("rating").greaterThanOrEqual(4.5))

    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots().collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(3)
    assertThat(firstSnapshot.results[0].get("title")).isEqualTo("Dune")
    assertThat(firstSnapshot.results[1].get("title")).isEqualTo("Pride and Prejudice")
    assertThat(firstSnapshot.results[2].get("title")).isEqualTo("The Lord of the Rings")

    // dropping Dune out of the result set
    collRef.document("book10").update("rating", 4.4).await()
    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.results).hasSize(2)
    assertThat(secondSnapshot.results[0].get("title")).isEqualTo("Pride and Prejudice")
    assertThat(secondSnapshot.results[1].get("title")).isEqualTo("The Lord of the Rings")

    // Adding book1 to the result
    collRef.document("book1").update("rating", 4.7).await()
    val thirdSnapshot = channel.receive()
    assertThat(thirdSnapshot.results).hasSize(3)
    assertThat(thirdSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    // Deleting book2
    collRef.document("book2").delete().await()
    val fourthSnapshot = channel.receive()
    assertThat(fourthSnapshot.results).hasSize(2)
    assertThat(fourthSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")
    assertThat(fourthSnapshot.results[1].get("title")).isEqualTo("The Lord of the Rings")

    job.cancel()
  }

  @Test
  fun testResultChanges() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(field("rating").greaterThanOrEqual(4.5))

    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots().collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.getChanges()).hasSize(3)
    assertThat(firstSnapshot.getChanges()[0].result.get("title")).isEqualTo("Dune")
    assertThat(firstSnapshot.getChanges()[0].type)
      .isEqualTo(RealtimePipeline.Snapshot.ResultChange.ChangeType.ADDED)
    assertThat(firstSnapshot.getChanges()[1].result.get("title")).isEqualTo("Pride and Prejudice")
    assertThat(firstSnapshot.getChanges()[1].type)
      .isEqualTo(RealtimePipeline.Snapshot.ResultChange.ChangeType.ADDED)
    assertThat(firstSnapshot.getChanges()[2].result.get("title")).isEqualTo("The Lord of the Rings")
    assertThat(firstSnapshot.getChanges()[2].type)
      .isEqualTo(RealtimePipeline.Snapshot.ResultChange.ChangeType.ADDED)

    // dropping Dune out of the result set
    collRef.document("book10").update("rating", 4.4).await()
    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.getChanges()).hasSize(1)
    assertThat(secondSnapshot.getChanges()[0].result.get("title")).isEqualTo("Dune")
    assertThat(secondSnapshot.getChanges()[0].type)
      .isEqualTo(RealtimePipeline.Snapshot.ResultChange.ChangeType.REMOVED)
    assertThat(secondSnapshot.getChanges()[0].oldIndex).isEqualTo(0)
    assertThat(secondSnapshot.getChanges()[0].newIndex).isEqualTo(-1)

    // Adding book1 to the result
    collRef.document("book1").update("rating", 4.7).await()
    val thirdSnapshot = channel.receive()
    assertThat(thirdSnapshot.getChanges()).hasSize(1)
    assertThat(thirdSnapshot.getChanges()[0].result.get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")
    assertThat(thirdSnapshot.getChanges()[0].type)
      .isEqualTo(RealtimePipeline.Snapshot.ResultChange.ChangeType.ADDED)
    assertThat(thirdSnapshot.getChanges()[0].oldIndex).isEqualTo(-1)
    assertThat(thirdSnapshot.getChanges()[0].newIndex).isEqualTo(0)

    // Delete book 2
    collRef.document("book2").delete().await()
    val fourthSnapshot = channel.receive()
    assertThat(fourthSnapshot.getChanges()).hasSize(1)
    assertThat(fourthSnapshot.getChanges()[0].result.get("title")).isEqualTo("Pride and Prejudice")
    assertThat(fourthSnapshot.getChanges()[0].oldIndex).isEqualTo(1)
    assertThat(fourthSnapshot.getChanges()[0].newIndex).isEqualTo(-1)

    job.cancel()
  }

  @Test
  fun testCanListenToCache() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(field("rating").greaterThanOrEqual(4.5))
    val options =
      RealtimePipeline.ListenOptions()
        .withMetadataChanges(MetadataChanges.INCLUDE)
        .withSource(ListenSource.CACHE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(3)
    assertThat(firstSnapshot.results[0].get("title")).isEqualTo("Dune")
    assertThat(firstSnapshot.results[1].get("title")).isEqualTo("Pride and Prejudice")
    assertThat(firstSnapshot.results[2].get("title")).isEqualTo("The Lord of the Rings")

    waitFor(db.disableNetwork())
    waitFor(db.enableNetwork())

    val nextSnapshot = withTimeoutOrNull(100) { channel.receive() }
    assertThat(nextSnapshot).isNull()

    job.cancel()
  }

  @Test
  fun testCanListenToMetadataOnlyChanges() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(field("rating").greaterThanOrEqual(4.5))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(3)
    assertThat(firstSnapshot.results[0].get("title")).isEqualTo("Dune")
    assertThat(firstSnapshot.results[1].get("title")).isEqualTo("Pride and Prejudice")
    assertThat(firstSnapshot.results[2].get("title")).isEqualTo("The Lord of the Rings")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(3)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testCanReadServerTimestampEstimateProperly() = runBlocking {
    waitFor(db.disableNetwork())
    collRef.document("book1").update("rating", FieldValue.serverTimestamp())

    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(field("title").equal("The Hitchhiker's Guide to the Galaxy"))

    val options =
      ListenOptions().withServerTimestampBehavior(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    val result = firstSnapshot.results[0]
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(result.get("rating")).isInstanceOf(Timestamp::class.java)
    assertThat(result.get("rating")).isEqualTo(result.getData()["rating"])
    val firstChanges = firstSnapshot.getChanges()
    assertThat(firstChanges).hasSize(1)
    assertThat(firstChanges[0].type)
      .isEqualTo(RealtimePipeline.Snapshot.ResultChange.ChangeType.ADDED)
    assertThat(firstChanges[0].result.get("rating")).isInstanceOf(Timestamp::class.java)
    assertThat(firstChanges[0].result.get("rating")).isEqualTo(result.get("rating"))

    waitFor(db.enableNetwork())

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results[0].get("rating")).isNotEqualTo(result.getData()["rating"])
    val secondChanges = secondSnapshot.getChanges()
    assertThat(secondChanges).hasSize(1)
    assertThat(secondChanges[0].type)
      .isEqualTo(RealtimePipeline.Snapshot.ResultChange.ChangeType.MODIFIED)
    assertThat(secondChanges[0].result.get("rating")).isInstanceOf(Timestamp::class.java)
    assertThat(secondChanges[0].result.get("rating"))
      .isEqualTo(secondSnapshot.results[0].get("rating"))

    job.cancel()
  }

  @Test
  fun testCanEvaluateServerTimestampEstimateProperly() = runBlocking {
    waitFor(db.disableNetwork())

    val now = constant(Timestamp.now())
    collRef.document("book1").update("rating", FieldValue.serverTimestamp())

    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(field("rating").timestampAdd(constant("second"), constant(1)).greaterThan(now))

    val options =
      ListenOptions()
        .withServerTimestampBehavior(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        .withMetadataChanges(MetadataChanges.INCLUDE)

    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    val result = firstSnapshot.results[0]
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(result.get("rating")).isInstanceOf(Timestamp::class.java)
    assertThat(result.get("rating")).isEqualTo(result.getData()["rating"])

    job.cancel()
  }

  @Test
  fun testCanReadServerTimestampPreviousProperly() = runBlocking {
    waitFor(db.disableNetwork())

    collRef.document("book1").update("rating", FieldValue.serverTimestamp())

    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(field("title").equal("The Hitchhiker's Guide to the Galaxy"))

    val options =
      ListenOptions().withServerTimestampBehavior(DocumentSnapshot.ServerTimestampBehavior.PREVIOUS)

    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    val result = firstSnapshot.results[0]
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(result.get("rating")).isEqualTo(4.2)
    assertThat(result.get("rating")).isEqualTo(result.getData()["rating"])
    val firstChanges = firstSnapshot.getChanges()
    assertThat(firstChanges).hasSize(1)
    assertThat(firstChanges[0].type)
      .isEqualTo(RealtimePipeline.Snapshot.ResultChange.ChangeType.ADDED)
    assertThat(firstChanges[0].result.get("rating")).isEqualTo(4.2)

    waitFor(db.enableNetwork())

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results[0].get("rating")).isInstanceOf(Timestamp::class.java)
    val secondChanges = secondSnapshot.getChanges()
    assertThat(secondChanges).hasSize(1)
    assertThat(secondChanges[0].type)
      .isEqualTo(RealtimePipeline.Snapshot.ResultChange.ChangeType.MODIFIED)
    assertThat(secondChanges[0].result.get("rating")).isInstanceOf(Timestamp::class.java)
    assertThat(secondChanges[0].result.get("rating"))
      .isEqualTo(secondSnapshot.results[0].get("rating"))

    job.cancel()
  }

  @Test
  fun testCanEvaluateServerTimestampPreviousProperly() = runBlocking {
    waitFor(db.disableNetwork())

    collRef.document("book1").update("title", FieldValue.serverTimestamp())

    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(field("title").equal("The Hitchhiker's Guide to the Galaxy"))

    val options =
      ListenOptions().withServerTimestampBehavior(DocumentSnapshot.ServerTimestampBehavior.PREVIOUS)

    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    val result = firstSnapshot.results[0]
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(result.get("title")).isEqualTo("The Hitchhiker's Guide to the Galaxy")

    job.cancel()
  }

  @Test
  fun testCanReadServerTimestampNoneProperly() = runBlocking {
    waitFor(db.disableNetwork())

    collRef.document("book1").update("rating", FieldValue.serverTimestamp())

    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(field("title").equal("The Hitchhiker's Guide to the Galaxy"))

    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots().collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    val result = firstSnapshot.results[0]
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(result.get("rating")).isNull()
    assertThat(result.get("rating")).isEqualTo(result.getData()["rating"])
    val firstChanges = firstSnapshot.getChanges()
    assertThat(firstChanges).hasSize(1)
    assertThat(firstChanges[0].type)
      .isEqualTo(RealtimePipeline.Snapshot.ResultChange.ChangeType.ADDED)
    assertThat(firstChanges[0].result.get("rating")).isNull()

    waitFor(db.enableNetwork())

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results[0].get("rating")).isInstanceOf(Timestamp::class.java)
    val secondChanges = secondSnapshot.getChanges()
    assertThat(secondChanges).hasSize(1)
    assertThat(secondChanges[0].type)
      .isEqualTo(RealtimePipeline.Snapshot.ResultChange.ChangeType.MODIFIED)
    assertThat(secondChanges[0].result.get("rating")).isInstanceOf(Timestamp::class.java)
    assertThat(secondChanges[0].result.get("rating"))
      .isEqualTo(secondSnapshot.results[0].get("rating"))

    job.cancel()
  }

  @Test
  fun testCanEvaluateServerTimestampNoneProperly() = runBlocking {
    waitFor(db.disableNetwork())

    collRef.document("book1").update("title", FieldValue.serverTimestamp())

    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(field("title").equal(nullValue()))

    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots().collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    val result = firstSnapshot.results[0]
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(result.get("title")).isNull()

    job.cancel()
  }

  @Test
  fun testSamePipelineWithDifferentOptions() = runBlocking {
    waitFor(db.disableNetwork())

    collRef.document("book1").update("title", FieldValue.serverTimestamp())

    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(field("title").notEqual(nullValue()))
        .limit(1)

    val channel1 = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job1 = launch {
      pipeline
        .snapshots(
          ListenOptions()
            .withServerTimestampBehavior(DocumentSnapshot.ServerTimestampBehavior.PREVIOUS)
        )
        .collect { channel1.send(it) }
    }

    val channel2 = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job2 = launch {
      pipeline
        .snapshots(
          ListenOptions()
            .withServerTimestampBehavior(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
        .collect { channel2.send(it) }
    }

    val firstSnapshot1 = channel1.receive()
    var result1 = firstSnapshot1.results[0]
    assertThat(firstSnapshot1.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(result1.get("title")).isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val firstSnapshot2 = channel2.receive()
    var result2 = firstSnapshot2.results[0]
    assertThat(firstSnapshot2.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(result2.get("title")).isInstanceOf(Timestamp::class.java)

    waitFor(db.enableNetwork())

    val secondSnapshot1 = channel1.receive()
    result1 = secondSnapshot1.results[0]
    assertThat(secondSnapshot1.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(result1.get("title")).isInstanceOf(Timestamp::class.java)

    val secondSnapshot2 = channel2.receive()
    result2 = secondSnapshot2.results[0]
    assertThat(secondSnapshot2.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(result2.get("title")).isInstanceOf(Timestamp::class.java)

    job1.cancel()
    job2.cancel()
  }

  @Test
  fun testLogicalAnd() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(
          and(
            field("genre").equal("Science Fiction"),
            field("rating").greaterThan(4.5),
          )
        )
        .sort(ascending("title"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title")).isEqualTo("Dune")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    // Add a book to the result set
    collRef.document("book1").update("rating", 4.6).await()
    val thirdSnapshot = channel.receive()
    assertThat(thirdSnapshot.results).hasSize(2)
    assertThat(thirdSnapshot.results[0].get("title")).isEqualTo("Dune")
    assertThat(thirdSnapshot.results[1].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    job.cancel()
  }

  @Test
  fun testLogicalOr() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(
          or(
            field("genre").equal("Dystopian"),
            field("published").lessThan(1900),
          )
        )
        .sort(ascending("published"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(4)
    assertThat(firstSnapshot.results[0].get("title")).isEqualTo("Pride and Prejudice") // 1813
    assertThat(firstSnapshot.results[1].get("title")).isEqualTo("Crime and Punishment") // 1866
    assertThat(firstSnapshot.results[2].get("title")).isEqualTo("1984") // 1949, Dystopian
    assertThat(firstSnapshot.results[3].get("title"))
      .isEqualTo("The Handmaid's Tale") // 1985, Dystopian

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(4)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    // Add a book to the result set
    collRef.document("book9").update("genre", "Dystopian").await()
    val thirdSnapshot = channel.receive()
    assertThat(thirdSnapshot.results).hasSize(5)
    assertThat(thirdSnapshot.results[2].get("title"))
      .isEqualTo("The Great Gatsby") // 1925, Dystopian

    job.cancel()
  }

  @Test
  fun testLogicalXor() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(
          xor(
            field("rating").greaterThan(4.5),
            field("genre").equal("Science Fiction"),
          )
        )
        .sort(ascending("rating"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(2)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy") // rating 4.2, SF -> false XOR true = true
    assertThat(firstSnapshot.results[1].get("title"))
      .isEqualTo("The Lord of the Rings") // rating 4.7, Fantasy -> true XOR false = true

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(2)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    // Modify a book to be excluded by making both conditions true
    collRef
      .document("book1")
      .update("rating", 4.7)
      .await() // Hitchhiker's Guide: rating 4.7, SF -> true XOR true = false
    val thirdSnapshot = channel.receive()
    assertThat(thirdSnapshot.results).hasSize(1)
    assertThat(thirdSnapshot.results[0].get("title")).isEqualTo("The Lord of the Rings")

    job.cancel()
  }

  @Test
  fun testNotFunction() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(not(field("genre").equal("Science Fiction")))
        .sort(ascending("published"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(8)
    assertThat(firstSnapshot.results.map { it.get("genre") as String })
      .doesNotContain("Science Fiction")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(8)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    // Modify a book to be excluded
    collRef.document("book2").update("genre", "Science Fiction").await()
    val thirdSnapshot = channel.receive()
    assertThat(thirdSnapshot.results).hasSize(7)
    assertThat(thirdSnapshot.results.map { it.get("title") as String })
      .doesNotContain("Pride and Prejudice")

    job.cancel()
  }

  @Test
  fun testEqAny() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(equalAny("genre", listOf("Dystopian", "Fantasy")))
        .sort(ascending("published"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(3)
    assertThat(firstSnapshot.results[0].get("title")).isEqualTo("1984")
    assertThat(firstSnapshot.results[1].get("title")).isEqualTo("The Lord of the Rings")
    assertThat(firstSnapshot.results[2].get("title")).isEqualTo("The Handmaid's Tale")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(3)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    // Add a book to the result set
    collRef.document("book9").update("genre", "Dystopian").await()
    val thirdSnapshot = channel.receive()
    assertThat(thirdSnapshot.results).hasSize(4)
    assertThat(thirdSnapshot.results[0].get("title")).isEqualTo("The Great Gatsby")
    assertThat(thirdSnapshot.getChanges()[0].type)
      .isEqualTo(RealtimePipeline.Snapshot.ResultChange.ChangeType.ADDED)
    assertThat(thirdSnapshot.getChanges()[0].result.get("title")).isEqualTo("The Great Gatsby")
    assertThat(thirdSnapshot.getChanges()[0].newIndex).isEqualTo(0)

    job.cancel()
  }

  @Test
  fun testNotEqAny() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(
          notEqualAny(
            "genre",
            listOf(
              "Dystopian",
              "Fantasy",
              "Science Fiction",
              "Romance",
              "Magical Realism",
              "Psychological Thriller",
              "Southern Gothic"
            )
          )
        )
        .sort(ascending("published"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title")).isEqualTo("The Great Gatsby")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    // Remove a book from the result set
    collRef.document("book9").update("genre", "Dystopian").await()
    val thirdSnapshot = channel.receive()
    assertThat(thirdSnapshot.results).hasSize(0)

    job.cancel()
  }

  @Test
  fun testIsAbsent() = runBlocking {
    collRef.document("book1").update("rating", FieldValue.delete()).await()
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(isAbsent("rating"))
        .sort(ascending("published"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testExists() = runBlocking {
    collRef.document("book1").update("rating", FieldValue.delete()).await()
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(not(exists("rating")))
        .sort(ascending("published"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testIsNanAndIsNotNan() = runBlocking {
    collRef.document("book1").update("rating", Double.NaN).await()

    // Test isNan
    val pipelineIsNan =
      db.realtimePipeline().collection(collRef.path).where(field("rating").equal(Double.NaN))

    val channelIsNan = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val jobIsNan = launch {
      pipelineIsNan.snapshots().collect { snapshot -> channelIsNan.send(snapshot) }
    }

    val snapshotIsNan = channelIsNan.receive()
    assertThat(snapshotIsNan.results).hasSize(1)
    assertThat(snapshotIsNan.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")
    jobIsNan.cancel()

    // Test isNotNan
    val pipelineIsNotNan =
      db.realtimePipeline().collection(collRef.path).where(field("rating").notEqual(Double.NaN))

    val channelIsNotNan = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val jobIsNotNan = launch {
      pipelineIsNotNan.snapshots().collect { snapshot -> channelIsNotNan.send(snapshot) }
    }

    val snapshotIsNotNan = channelIsNotNan.receive()
    assertThat(snapshotIsNotNan.results).hasSize(9)
    jobIsNotNan.cancel()
  }

  @Test
  fun testIsNullAndIsNotNull() = runBlocking {
    collRef.document("book1").update("rating", null).await()

    // Test isNull
    val pipelineIsNull =
      db.realtimePipeline().collection(collRef.path).where(field("rating").equal(nullValue()))

    val channelIsNull = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val jobIsNull = launch {
      pipelineIsNull.snapshots().collect { snapshot -> channelIsNull.send(snapshot) }
    }

    val snapshotIsNull = channelIsNull.receive()
    assertThat(snapshotIsNull.results).hasSize(1)
    assertThat(snapshotIsNull.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")
    jobIsNull.cancel()

    // Test isNotNull
    val pipelineIsNotNull =
      db.realtimePipeline().collection(collRef.path).where(notEqual("rating", nullValue()))

    val channelIsNotNull = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val jobIsNotNull = launch {
      pipelineIsNotNull.snapshots().collect { snapshot -> channelIsNotNull.send(snapshot) }
    }

    val snapshotIsNotNull = channelIsNotNull.receive()
    assertThat(snapshotIsNotNull.results).hasSize(9)
    jobIsNotNull.cancel()
  }

  @Test
  fun testStrConcat() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(field("author").equal(stringConcat(constant("Douglas"), constant(" Adams"))))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testToLower() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(field("author").toLower().equal(toLower(constant("DOUGLAS ADAMS"))))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testToUpper() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(field("author").toUpper().equal(toUpper(constant("dOUglAs adaMs"))))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testTrim() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(field("author").equal(trim(constant("  Douglas Adams  "))))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testCharLength() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(charLength("author").greaterThan(20))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title")).isEqualTo("One Hundred Years of Solitude")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testByteLength() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(byteLength("author").greaterThan(20))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title")).isEqualTo("One Hundred Years of Solitude")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testReverse() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(field("author").equal(reverse(constant("smadA salguoD"))))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testStrContains() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(stringContains("author", "Adams"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testStartsWith() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(startsWith("author", "Douglas"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testEndsWith() = runBlocking {
    val pipeline = db.realtimePipeline().collection(collRef.path).where(endsWith("author", "Adams"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  @Ignore("Not supported yet")
  fun testLike() = runBlocking {
    val pipeline = db.realtimePipeline().collection(collRef.path).where(like("author", "Douglas%"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  @Ignore("Not supported yet")
  fun testRegexContains() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(regexContains("author", "Douglas.*"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  @Ignore("Not supported yet")
  fun testRegexFind() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(regexFind("author", "^\\w+").equal("Douglas"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  @Ignore("Not supported yet")
  fun testRegexFindAll() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(regexFindAll("author", "^\\w+").equal("Douglas"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  @Ignore("Not supported yet")
  fun testRegexMatch() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(regexMatch("author", "Douglas Adams"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testAdd() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(add("rating", 0.8).equal(5.0))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(3)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")
    assertThat(firstSnapshot.results[1].get("title")).isEqualTo("To Kill a Mockingbird")
    assertThat(firstSnapshot.results[2].get("title")).isEqualTo("1984")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(3)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testSubtract() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(subtract("rating", 0.2).equal(4.0))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(3)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")
    assertThat(firstSnapshot.results[1].get("title")).isEqualTo("To Kill a Mockingbird")
    assertThat(firstSnapshot.results[2].get("title")).isEqualTo("1984")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(3)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testMultiply() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(multiply("rating", 2.0).equal(8.4))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(3)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")
    assertThat(firstSnapshot.results[1].get("title")).isEqualTo("To Kill a Mockingbird")
    assertThat(firstSnapshot.results[2].get("title")).isEqualTo("1984")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(3)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testDivide() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(divide("rating", 2.0).equal(2.1))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(3)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")
    assertThat(firstSnapshot.results[1].get("title")).isEqualTo("To Kill a Mockingbird")
    assertThat(firstSnapshot.results[2].get("title")).isEqualTo("1984")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(3)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testMod() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(mod("published", 100).equal(79))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testPow() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(pow("rating", 2.0).greaterThan(20.0))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(3)

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(3)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testAbs() = runBlocking {
    collRef.document("book1").update("rating", -4.2).await()
    val pipeline = db.realtimePipeline().collection(collRef.path).where(abs("rating").equal(4.2))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(3)
    assertThat(firstSnapshot.results.map { it.get("title") })
      .containsExactly("The Hitchhiker's Guide to the Galaxy", "To Kill a Mockingbird", "1984")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(3)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testExp() = runBlocking {
    collRef.document("book1").update("log_rating", 1.4350845335).await() // ln(4.2)
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(and(exp("log_rating").greaterThan(4.19), exp("log_rating").lessThan(4.21)))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testLn() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(and(ln("rating").greaterThan(1.43), ln("rating").lessThan(1.44)))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(3)
    assertThat(firstSnapshot.results.map { it.get("title") })
      .containsExactly("The Hitchhiker's Guide to the Galaxy", "To Kill a Mockingbird", "1984")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(3)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testLog10() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(log10("published").equal(kotlin.math.log10(1979.0)))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title"))
      .isEqualTo("The Hitchhiker's Guide to the Galaxy")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testLog() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(log("published", constant(4.2)).equal(kotlin.math.log(1954.0, 4.2)))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title")).isEqualTo("The Lord of the Rings")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testSqrt() = runBlocking {
    val pipeline =
    // published since 1952
    db.realtimePipeline().collection(collRef.path).where(sqrt("published").greaterThan(44.18))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(6)

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(6)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testRound() = runBlocking {
    val pipeline = db.realtimePipeline().collection(collRef.path).where(round("rating").equal(5.0))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(3)

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(3)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testCeil() = runBlocking {
    val pipeline = db.realtimePipeline().collection(collRef.path).where(ceil("rating").equal(5.0))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    // only book 9's rating is 4.0
    assertThat(firstSnapshot.results).hasSize(9)

    collRef.document("book9").update("rating", FieldValue.increment(0.001)).await()

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(secondSnapshot.results).hasSize(10)
    assertThat(secondSnapshot.getChanges()).hasSize(1)
    assertThat(secondSnapshot.getChanges()[0].result.get("title")).isEqualTo("The Great Gatsby")

    val thirdSnapshot = channel.receive()
    assertThat(thirdSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(thirdSnapshot.results).hasSize(10)
    assertThat(thirdSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testFloor() = runBlocking {
    val pipeline = db.realtimePipeline().collection(collRef.path).where(floor("rating").equal(4.0))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(10)

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(10)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testTimestampAdd() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(eventCollRef.path)
        .where(
          timestampAdd("timestamp", "day", 1)
            .equal(unixSecondsToTimestamp(constant(1698228000 + 24 * 3600)))
        )

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("name")).isEqualTo("Test Event")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testTimestampSub() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(eventCollRef.path)
        .where(
          field("timestamp")
            .timestampSubtract("day", 1)
            .equal(unixSecondsToTimestamp(constant(1698228000 - 24 * 3600)))
        )

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("name")).isEqualTo("Test Event")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testUnixSecondsToTimestamp() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(eventCollRef.path)
        .where(field("timestamp").equal(unixSecondsToTimestamp(field("unix_seconds"))))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("name")).isEqualTo("Test Event")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testUnixMillisToTimestamp() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(eventCollRef.path)
        .where(field("timestamp").equal(unixMillisToTimestamp(constant(1698228000000L))))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("name")).isEqualTo("Test Event")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testTimestampToUnixSeconds() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(eventCollRef.path)
        .where(timestampToUnixSeconds("timestamp").equal(1698228000))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("name")).isEqualTo("Test Event")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testTimestampToUnixMillis() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(eventCollRef.path)
        .where(timestampToUnixMillis("timestamp").equal(field("unix_millis")))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("name")).isEqualTo("Test Event")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testTimestampToUnixMicros() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(eventCollRef.path)
        .where(timestampToUnixMicros("timestamp").equal(field("unix_micros")))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testUnixMicrosToTimestamp() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(eventCollRef.path)
        .where(field("timestamp").equal(unixMicrosToTimestamp(field("unix_micros"))))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testArrayContains() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(arrayContains("tags", "politics"))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(1)
    assertThat(firstSnapshot.results[0].get("title")).isEqualTo("Dune")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(1)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testArrayContainsAny() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(arrayContainsAny("tags", listOf("politics", "love", "racism")))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(4)
    // ordered by document id, doc10 goes first.
    assertThat(firstSnapshot.results[0].get("title")).isEqualTo("Dune")
    assertThat(firstSnapshot.results[1].get("title")).isEqualTo("Pride and Prejudice")
    assertThat(firstSnapshot.results[2].get("title")).isEqualTo("To Kill a Mockingbird")
    assertThat(firstSnapshot.results[3].get("title")).isEqualTo("The Great Gatsby")

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(4)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testArrayLength() = runBlocking {
    val pipeline =
      db.realtimePipeline().collection(collRef.path).where(arrayLength("tags").equal(3))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    assertThat(firstSnapshot.results).hasSize(10)

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(10)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }

  @Test
  fun testSubstring() = runBlocking {
    val pipeline =
      db
        .realtimePipeline()
        .collection(collRef.path)
        .where(field("title").substring(1, 3).equal("he "))

    val options = ListenOptions().withMetadataChanges(MetadataChanges.INCLUDE)
    val channel = Channel<RealtimePipeline.Snapshot>(Channel.UNLIMITED)
    val job = launch { pipeline.snapshots(options).collect { snapshot -> channel.send(snapshot) } }

    val firstSnapshot = channel.receive()
    assertThat(firstSnapshot.metadata.isConsistentBetweenListeners).isFalse()
    // Any title starts with "The "
    assertThat(firstSnapshot.results).hasSize(4)
    assertThat(firstSnapshot.results.map { it.get("title").toString().startsWith("The ") })
      .isEqualTo(listOf(true, true, true, true))

    val secondSnapshot = channel.receive()
    assertThat(secondSnapshot.metadata.isConsistentBetweenListeners).isTrue()
    assertThat(secondSnapshot.results).hasSize(4)
    assertThat(secondSnapshot.getChanges()).isEmpty()

    job.cancel()
  }
}
