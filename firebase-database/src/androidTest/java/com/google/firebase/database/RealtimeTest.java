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

package com.google.firebase.database;

import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.RepoManager;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

@org.junit.runner.RunWith(AndroidJUnit4.class)
public class RealtimeTest {
  @Rule public RetryRule retryRule = new RetryRule(3);

  @After
  public void tearDown() {
    IntegrationTestHelpers.failOnFirstUncaughtException();
  }

  @Test
  public void testOnDisconnectSetWorks()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);
    RepoManager.resume(ctx);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    ReadFuture writerFuture =
        new ReadFuture(
            writer.child("disconnected"),
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                valSemaphore.release();
                Object snap = events.get(events.size() - 1).getSnapshot().getValue();
                return snap != null;
              }
            });

    ReadFuture readerFuture =
        new ReadFuture(
            reader.child("disconnected"),
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                valSemaphore.release();
                Object snap = events.get(events.size() - 1).getSnapshot().getValue();
                return snap != null;
              }
            });

    // Wait for initial (null) value on both reader and writer.
    IntegrationTestHelpers.waitFor(valSemaphore, 2);

    Object expected = "dummy";
    writer
        .child("disconnected")
        .onDisconnect()
        .setValue(
            expected,
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                RepoManager.interrupt(ctx);
                ;
                opSemaphore.release();
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    EventRecord writerEventRecord = writerFuture.timedGet().get(1);
    EventRecord readerEventRecord = readerFuture.timedGet().get(1);

    RepoManager.resume(ctx);

    DeepEquals.assertEquals(expected, writerEventRecord.getSnapshot().getValue());
    DeepEquals.assertEquals(expected, readerEventRecord.getSnapshot().getValue());
  }

  @Test
  public void testOnDisconnectSetWithPriorityWorks()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);
    RepoManager.resume(ctx);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    ReadFuture writerFuture =
        new ReadFuture(
            writer.child("disconnected"),
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                Object snap = events.get(events.size() - 1).getSnapshot().getValue();
                valSemaphore.release();
                return snap != null;
              }
            });

    ReadFuture readerFuture =
        new ReadFuture(
            reader.child("disconnected"),
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                Object snap = events.get(events.size() - 1).getSnapshot().getValue();
                valSemaphore.release();
                return snap != null;
              }
            });

    // Wait for initial (null) value on both reader and writer.
    IntegrationTestHelpers.waitFor(valSemaphore, 2);

    Object expected = true;
    String expectedPriority = "12345";
    writer
        .child("disconnected")
        .onDisconnect()
        .setValue(
            expected,
            expectedPriority,
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                RepoManager.interrupt(ctx);
                ;
                opSemaphore.release();
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    EventRecord writerEventRecord = writerFuture.timedGet().get(1);
    EventRecord readerEventRecord = readerFuture.timedGet().get(1);

    RepoManager.resume(ctx);
    ;

    DeepEquals.assertEquals(expected, writerEventRecord.getSnapshot().getValue());
    DeepEquals.assertEquals(expectedPriority, writerEventRecord.getSnapshot().getPriority());
    DeepEquals.assertEquals(expected, readerEventRecord.getSnapshot().getValue());
    DeepEquals.assertEquals(expectedPriority, readerEventRecord.getSnapshot().getPriority());
  }

  @Test
  public void testOnDisconnectRemoveWorks()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);
    RepoManager.resume(ctx);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    ReadFuture writerFuture =
        new ReadFuture(
            writer.child("foo"),
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                valSemaphore.release();
                return events.size() == 3;
              }
            });

    ReadFuture readerFuture =
        new ReadFuture(
            reader.child("foo"),
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                valSemaphore.release();
                return events.size() == 3;
              }
            });

    // Wait for initial (null) value on both reader and writer.
    IntegrationTestHelpers.waitFor(valSemaphore, 2);

    writer
        .child("foo")
        .setValue(
            "bar",
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release();
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    writer
        .child("foo")
        .onDisconnect()
        .removeValue(
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                RepoManager.interrupt(ctx);
                opSemaphore.release();
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    EventRecord writerEventRecord = writerFuture.timedGet().get(2);
    EventRecord readerEventRecord = readerFuture.timedGet().get(2);

    RepoManager.resume(ctx);
    ;

    DeepEquals.assertEquals(null, writerEventRecord.getSnapshot().getValue());
    DeepEquals.assertEquals(null, readerEventRecord.getSnapshot().getValue());
  }

  @Test
  public void testOnDisconnectUpdateWorks()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);
    RepoManager.resume(ctx);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    ReadFuture writerFuture =
        new ReadFuture(
            writer.child("foo"),
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                valSemaphore.release();
                return events.size() == 4;
              }
            });

    ReadFuture readerFuture =
        new ReadFuture(
            reader.child("foo"),
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                valSemaphore.release();
                return events.size() == 3;
              }
            });

    // Wait for initial (null) value on both reader and writer.
    IntegrationTestHelpers.waitFor(valSemaphore, 2);

    Map<String, Object> initialValues = new MapBuilder().put("bar", "a").put("baz", "b").build();
    writer
        .child("foo")
        .setValue(
            initialValues,
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release();
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    Map<String, Object> updatedValues = new MapBuilder().put("baz", "c").put("bat", "d").build();
    writer
        .child("foo")
        .onDisconnect()
        .updateChildren(
            updatedValues,
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                RepoManager.interrupt(ctx);
                ;
                opSemaphore.release();
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    EventRecord writerEventRecord = writerFuture.timedGet().get(3);
    EventRecord readerEventRecord = readerFuture.timedGet().get(2);

    RepoManager.resume(ctx);
    ;

    Map<String, Object> expected =
        new MapBuilder().put("bar", "a").put("baz", "c").put("bat", "d").build();

    DeepEquals.assertEquals(expected, writerEventRecord.getSnapshot().getValue());
    DeepEquals.assertEquals(expected, readerEventRecord.getSnapshot().getValue());
  }

  @Test
  public void testOnDisconnectTriggersSingleLocalValueEventForWriter()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(1);
    DatabaseReference writer = refs.get(0);
    final DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);
    RepoManager.resume(ctx);

    final AtomicInteger callbackCount = new AtomicInteger(0);
    final Semaphore valSemaphore = new Semaphore(0);
    ReadFuture writerFuture =
        new ReadFuture(
            writer,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                callbackCount.incrementAndGet();
                valSemaphore.release(1);
                return events.size() == 2;
              }
            });
    IntegrationTestHelpers.waitFor(valSemaphore);

    final Semaphore opSemaphore = new Semaphore(0);
    writer
        .child("foo")
        .onDisconnect()
        .setValue(
            new MapBuilder().put("bar", "a").put("baz", "b").build(),
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release(1);
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    writer
        .child("foo")
        .onDisconnect()
        .updateChildren(
            new MapBuilder().put("bam", "c").build(),
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release(1);
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    writer
        .child("foo/baz")
        .onDisconnect()
        .removeValue(
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release(1);
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    RepoManager.interrupt(ctx);
    ;

    IntegrationTestHelpers.waitFor(valSemaphore);
    EventRecord writerEventRecord = writerFuture.timedGet().get(1);

    RepoManager.resume(ctx);
    ;

    Map<String, Object> expected =
        new MapBuilder()
            .put("foo", new MapBuilder().put("bam", "c").put("bar", "a").build())
            .build();
    DeepEquals.assertEquals(expected, writerEventRecord.getSnapshot().getValue());
    assertTrue(callbackCount.get() == 2);
  }

  @Test
  public void testOnDisconnectTriggersSingleLocalValueEventForReader()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);
    RepoManager.resume(ctx);

    final AtomicInteger callbackCount = new AtomicInteger(0);
    final Semaphore valSemaphore = new Semaphore(0);
    ReadFuture readerFuture =
        new ReadFuture(
            reader,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                callbackCount.incrementAndGet();
                valSemaphore.release(1);
                return events.size() == 2;
              }
            });
    IntegrationTestHelpers.waitFor(valSemaphore);

    final Semaphore opSemaphore = new Semaphore(0);
    writer
        .child("foo")
        .onDisconnect()
        .setValue(
            new MapBuilder().put("bar", "a").put("baz", "b").build(),
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release(1);
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    writer
        .child("foo")
        .onDisconnect()
        .updateChildren(
            new MapBuilder().put("bam", "c").build(),
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release(1);
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    writer
        .child("foo/baz")
        .onDisconnect()
        .removeValue(
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release(1);
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    RepoManager.interrupt(ctx);
    ;

    IntegrationTestHelpers.waitFor(valSemaphore);
    EventRecord readerEventRecord = readerFuture.timedGet().get(1);

    RepoManager.resume(ctx);
    ;

    Map<String, Object> expected =
        new MapBuilder()
            .put("foo", new MapBuilder().put("bam", "c").put("bar", "a").build())
            .build();
    DeepEquals.assertEquals(expected, readerEventRecord.getSnapshot().getValue());
    assertTrue(callbackCount.get() == 2);
  }

  @Test
  public void testOnDisconnectTriggersSingleLocalValueEventForWriterWithQuery()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(1);
    DatabaseReference writer = refs.get(0);
    final DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);
    RepoManager.resume(ctx);
    ;

    final AtomicInteger callbackCount = new AtomicInteger(0);
    final Semaphore valSemaphore = new Semaphore(0);
    ReadFuture writerFuture =
        new ReadFuture(
            writer,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                callbackCount.incrementAndGet();
                valSemaphore.release(1);
                return events.size() == 2;
              }
            });
    IntegrationTestHelpers.waitFor(valSemaphore);

    final Semaphore opSemaphore = new Semaphore(0);
    writer
        .child("foo")
        .onDisconnect()
        .setValue(
            new MapBuilder().put("bar", "a").put("baz", "b").build(),
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release(1);
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    writer
        .child("foo")
        .onDisconnect()
        .updateChildren(
            new MapBuilder().put("bam", "c").build(),
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release(1);
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    writer
        .child("foo/baz")
        .onDisconnect()
        .removeValue(
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release(1);
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    RepoManager.interrupt(ctx);
    ;

    IntegrationTestHelpers.waitFor(valSemaphore);
    EventRecord writerEventRecord = writerFuture.timedGet().get(1);

    RepoManager.resume(ctx);
    ;

    Map<String, Object> expected =
        new MapBuilder()
            .put("foo", new MapBuilder().put("bam", "c").put("bar", "a").build())
            .build();
    DeepEquals.assertEquals(expected, writerEventRecord.getSnapshot().getValue());
    assertTrue(callbackCount.get() == 2);
  }

  @Test
  public void testOnDisconnectTriggersSingleLocalValueEventForReaderWithQuery()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);

    final AtomicInteger callbackCount = new AtomicInteger(0);
    final Semaphore valSemaphore = new Semaphore(0);
    ReadFuture readerFuture =
        new ReadFuture(
            reader,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                callbackCount.incrementAndGet();
                valSemaphore.release(1);
                return events.size() == 2;
              }
            });
    IntegrationTestHelpers.waitFor(valSemaphore);

    final Semaphore opSemaphore = new Semaphore(0);
    writer
        .child("foo")
        .onDisconnect()
        .setValue(
            new MapBuilder().put("bar", "a").put("baz", "b").build(),
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release(1);
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    writer
        .child("foo")
        .onDisconnect()
        .updateChildren(
            new MapBuilder().put("bam", "c").build(),
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release(1);
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    writer
        .child("foo/baz")
        .onDisconnect()
        .removeValue(
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release(1);
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    RepoManager.interrupt(ctx);
    ;

    IntegrationTestHelpers.waitFor(valSemaphore);
    EventRecord readerEventRecord = readerFuture.timedGet().get(1);

    RepoManager.resume(ctx);
    ;

    Map<String, Object> expected =
        new MapBuilder()
            .put("foo", new MapBuilder().put("bam", "c").put("bar", "a").build())
            .build();
    DeepEquals.assertEquals(expected, readerEventRecord.getSnapshot().getValue());
    assertTrue(callbackCount.get() == 2);
  }

  @Test
  public void testOnDisconnectDeepMergeTriggersOnlyOneValueEventForReaderWithQuery()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);

    final AtomicInteger callbackCount = new AtomicInteger(0);
    final Semaphore valSemaphore = new Semaphore(0);
    ReadFuture readerFuture =
        new ReadFuture(
            reader,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                callbackCount.incrementAndGet();
                valSemaphore.release(1);
                return events.size() == 3;
              }
            });
    IntegrationTestHelpers.waitFor(valSemaphore);

    final Semaphore opSemaphore = new Semaphore(0);
    Map<String, Object> initialValues =
        new MapBuilder()
            .put("a", 1)
            .put(
                "b",
                new MapBuilder()
                    .put("c", true)
                    .put("d", "scalar")
                    .put("e", new MapBuilder().put("f", "hooray").build())
                    .build())
            .build();
    writer.setValue(
        initialValues,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release(1);
          }
        });
    IntegrationTestHelpers.waitFor(valSemaphore);
    IntegrationTestHelpers.waitFor(opSemaphore);

    writer
        .child("b/c")
        .onDisconnect()
        .setValue(
            false,
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release(1);
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    writer
        .child("b/d")
        .onDisconnect()
        .removeValue(
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release(1);
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    RepoManager.interrupt(ctx);

    IntegrationTestHelpers.waitFor(valSemaphore);
    EventRecord readerEventRecord = readerFuture.timedGet().get(2);

    RepoManager.resume(ctx);

    Map<String, Object> expected =
        new MapBuilder()
            .put("a", 1L)
            .put(
                "b",
                new MapBuilder()
                    .put("c", false)
                    .put("e", new MapBuilder().put("f", "hooray").build())
                    .build())
            .build();
    DeepEquals.assertEquals(expected, readerEventRecord.getSnapshot().getValue());
    assertTrue(callbackCount.get() == 3);
  }

  @Test
  public void testOnDisconnectCancelWorks()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);
    RepoManager.resume(ctx);
    ;

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    ReadFuture writerFuture =
        new ReadFuture(
            writer.child("foo"),
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                valSemaphore.release();
                return events.size() == 3;
              }
            });

    ReadFuture readerFuture =
        new ReadFuture(
            reader.child("foo"),
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                valSemaphore.release();
                return events.size() == 3;
              }
            });

    // Wait for initial (null) value on both reader and writer.
    IntegrationTestHelpers.waitFor(valSemaphore, 2);

    Map<String, Object> initialValues = new MapBuilder().put("bar", "a").put("baz", "b").build();
    writer
        .child("foo")
        .setValue(
            initialValues,
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release();
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    Map<String, Object> updatedValues = new MapBuilder().put("baz", "c").put("bat", "d").build();
    writer
        .child("foo")
        .onDisconnect()
        .updateChildren(
            updatedValues,
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release();
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    writer
        .child("foo/bat")
        .onDisconnect()
        .cancel(
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                RepoManager.interrupt(ctx);
                ;
                opSemaphore.release();
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    EventRecord writerEventRecord = writerFuture.timedGet().get(2);
    EventRecord readerEventRecord = readerFuture.timedGet().get(2);

    RepoManager.resume(ctx);
    ;

    Map<String, Object> expected = new MapBuilder().put("bar", "a").put("baz", "c").build();

    DeepEquals.assertEquals(expected, writerEventRecord.getSnapshot().getValue());
    DeepEquals.assertEquals(expected, readerEventRecord.getSnapshot().getValue());
  }

  @Test
  public void testOnDisconnectWithServerValuesWorks()
      throws TestFailure, TimeoutException, DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(1);
    DatabaseReference writer = refs.get(0);
    final DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);
    RepoManager.resume(ctx);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    ReadFuture writerFuture =
        new ReadFuture(
            writer,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                valSemaphore.release();
                Object snapVal = events.get(events.size() - 1).getSnapshot().getValue();
                return snapVal != null;
              }
            });

    // Wait for initial (null) value.
    IntegrationTestHelpers.waitFor(valSemaphore);

    Map<String, Object> initialValues =
        new MapBuilder()
            .put("a", ServerValue.TIMESTAMP)
            .put(
                "b",
                new MapBuilder()
                    .put(".value", ServerValue.TIMESTAMP)
                    .put(".priority", ServerValue.TIMESTAMP)
                    .build())
            .build();

    writer
        .onDisconnect()
        .setValue(
            initialValues,
            ServerValue.TIMESTAMP,
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                RepoManager.interrupt(ctx);
                opSemaphore.release();
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    EventRecord readerEventRecord = writerFuture.timedGet().get(1);
    DataSnapshot snap = readerEventRecord.getSnapshot();

    RepoManager.resume(ctx);

    assertEquals(snap.child("a").getValue().getClass(), Long.class);
    assertEquals(snap.getPriority().getClass(), Double.class);
    assertEquals(snap.getPriority(), snap.child("b").getPriority());
    assertEquals(snap.child("a").getValue(), snap.child("b").getValue());
    long drift = System.currentTimeMillis() - Long.parseLong(snap.child("a").getValue().toString());
    assertThat(Math.abs(drift), lessThan(2000l));
  }

  // TODO: Find better way to test shutdown behavior. This test is not worth a 13-second pause (6
  // second sleep and then 7 second timeout via timedGet())!
  @Test
  @Ignore
  public void testShutdown()
      throws InterruptedException, ExecutionException, TestFailure, TimeoutException {
    DatabaseConfig config = IntegrationTestHelpers.newTestConfig();
    config.setLogLevel(Logger.Level.DEBUG);
    DatabaseReference ref = new DatabaseReference(IntegrationTestValues.getNamespace(), config);

    // Shut it down right away
    DatabaseReference.goOffline(config);
    Thread.sleep(6 * 1000); // Long enough for all of the threads to exit
    assertTrue(config.isStopped());
    // Test that we can use an existing ref
    DatabaseReference pushed = ref.push();
    try {
      new WriteFuture(pushed, "foo").timedGet();
      fail("Should time out, we're offline");
    } catch (TimeoutException t) {
      // Expected, we're offline
    }
    assertFalse(config.isStopped());
    DatabaseReference.goOnline(config);

    final Semaphore ready = new Semaphore(0);
    ref.child(".info/connected")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                Boolean connected = snapshot.getValue(Boolean.class);
                if (connected) {
                  ready.release(1);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    // Wait for us to be connected so we send the buffered put
    IntegrationTestHelpers.waitFor(ready);

    DataSnapshot snap = IntegrationTestHelpers.getSnap(pushed);
    assertEquals("foo", snap.getValue(String.class));
  }

  @Test
  public void testWritesToSameLocationWhileOfflineAreInOrder()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    DatabaseReference.goOffline();
    for (int i = 0; i < 100; i++) {
      ref.setValue(i);
    }
    // This should be the last write and the actual value
    WriteFuture future = new WriteFuture(ref, 100);
    DatabaseReference.goOnline();
    future.timedGet();

    final Semaphore semaphore = new Semaphore(0);
    ref.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            Assert.assertEquals(100L, snapshot.getValue());
            semaphore.release();
          }

          @Override
          public void onCancelled(DatabaseError error) {
            Assert.fail("Shouldn't be cancelled");
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void testOnDisconnectIsNotRerunOnReconnect()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);
    RepoManager.resume(ctx);

    final Semaphore semaphore = new Semaphore(0);

    // Will ensure that the operation is queued
    RepoManager.interrupt(ctx);

    final int[] counter = new int[1];

    ref.child("disconnected")
        .onDisconnect()
        .setValue(
            true,
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                assertNull(error);
                semaphore.release();
                counter[0]++;
              }
            });

    // Will trigger sending the onDisconnect
    RepoManager.resume(ctx);

    // Should be complete initially
    IntegrationTestHelpers.waitFor(semaphore);
    // One onComplete called
    assertEquals(1, counter[0]);

    // Will trigger a reconnect
    RepoManager.interrupt(ctx);
    RepoManager.resume(ctx);

    // Make sure we sent all outstanding onDisconnects
    IntegrationTestHelpers.waitForRoundtrip(ref);
    IntegrationTestHelpers.waitForRoundtrip(
        ref); // Two are needed because writes are restored first, then onDisconnects
    assertEquals(1, counter[0]); // No onComplete should have triggered
  }

  /*
  @Test
  public void testServerValuesEventualConsistencyBetweenLocalAndRemote() throws DatabaseException,
      InterruptedException {
      List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
      DatabaseReference writer = refs.get(0);
      DatabaseReference reader = refs.get(1);

      final Semaphore valMatchSemaphore = new Semaphore(0);
      final AtomicLong readerTimestamp = new AtomicLong();
      final AtomicLong writerTimestamp = new AtomicLong();

      reader.addValueEventListener(new ValueEventListener() {
          @Override public void onCancelled() {}

          @Override
          public void onDataChange(DataSnapshot snapshot) {
              if (snapshot.getValue() != null) {
                  readerTimestamp.set((Long)snapshot.getValue());
                  System.out.println("*** reader: " + snapshot.getValue());

                  if (readerTimestamp.equals(writerTimestamp)) {
                      valMatchSemaphore.release();
                  }
              }
          }
      });

      writer.addValueEventListener(new ValueEventListener() {
          @Override public void onCancelled() {}

          @Override
          public void onDataChange(DataSnapshot snapshot) {
              if (snapshot.getValue() != null) {
                  writerTimestamp.set((Long)snapshot.getValue());
                  System.out.println("*** writer: " + snapshot.getValue());

                  if (readerTimestamp.equals(writerTimestamp)) {
                      valMatchSemaphore.release();
                  }
              }
          }
      });


      writer.onDisconnect().setValue(DatabaseReference.ServerValue.TIMESTAMP, new
          DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
              ctx.interrupt();
              opSemaphore.release();
          }
      });

      IntegrationTestHelpers.waitFor(valMatchSemaphore);

      ctx.resume();
  }*/
}
