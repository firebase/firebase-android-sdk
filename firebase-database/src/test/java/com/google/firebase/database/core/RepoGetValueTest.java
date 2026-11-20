// Copyright 2026 Google LLC
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

package com.google.firebase.database.core;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.InternalHelpers;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.Query;
import com.google.firebase.database.TestValues;
import com.google.firebase.database.connection.HostInfo;
import com.google.firebase.database.connection.PersistentConnection;
import com.google.firebase.database.core.persistence.CachePolicy;
import com.google.firebase.database.core.persistence.DefaultPersistenceManager;
import com.google.firebase.database.core.persistence.MockPersistenceStorageEngine;
import com.google.firebase.database.core.persistence.PersistenceManager;
import com.google.firebase.database.core.utilities.DefaultRunLoop;
import com.google.firebase.database.core.utilities.Utilities;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.snapshot.NodeUtilities;
import com.google.firebase.database.util.JsonMapper;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests how {@link Repo#getValue} handles the server's response to a {@code get()} request. */
@RunWith(RobolectricTestRunner.class)
public class RepoGetValueTest {

  private static final long TIMEOUT_SECONDS = 10;

  /** Records exceptions that reach the run loop. On Android, these crash the app. */
  private static class RecordingRunLoop extends DefaultRunLoop {
    final AtomicReference<Throwable> uncaughtException = new AtomicReference<>();

    @Override
    public void handleException(Throwable e) {
      uncaughtException.compareAndSet(null, e);
    }
  }

  private final RecordingRunLoop runLoop = new RecordingRunLoop();
  private final PersistentConnection connection = mock(PersistentConnection.class);
  private FirebaseApp app;
  private DatabaseConfig config;

  @Before
  public void setUp() {
    app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApiKey("apiKey")
                .setApplicationId("appId")
                .setDatabaseUrl(TestValues.TEST_NAMESPACE)
                .build(),
            "RepoGetValueTest");
    config =
        new DatabaseConfig() {
          @Override
          public PersistentConnection newPersistentConnection(
              HostInfo info, PersistentConnection.Delegate delegate) {
            return connection;
          }
        };
    config.setRunLoop(runLoop);
    config.setFirebaseApp(app);
    config.setAuthTokenProvider(mock(TokenProvider.class));
    config.setAppCheckTokenProvider(mock(TokenProvider.class));
    config.freeze();
  }

  @After
  public void tearDown() {
    runLoop.getExecutorService().shutdownNow();
    app.delete();
  }

  @Test
  public void getFailsWhenPriorityIsAnObject() throws Exception {
    respondToGetWith("{'a': 1, '.priority': {'x': 1}}");

    Task<DataSnapshot> task = awaitCompletion(fooReference(newRepo()).get());

    assertThat(task.getException()).isInstanceOf(DatabaseException.class);
    assertNoUncaughtException();
  }

  @Test
  public void getFailsWhenPriorityIsABoolean() throws Exception {
    respondToGetWith("{'.priority': true, '.value': 1}");

    Task<DataSnapshot> task = awaitCompletion(fooReference(newRepo()).get());

    assertThat(task.getException()).isInstanceOf(DatabaseException.class);
    assertNoUncaughtException();
  }

  @Test
  public void getFailsWhenListElementHasInvalidPriority() throws Exception {
    respondToGetWith("['a', {'.priority': ['x'], '.value': 'b'}]");

    Task<DataSnapshot> task = awaitCompletion(fooReference(newRepo()).get());

    assertThat(task.getException()).isInstanceOf(DatabaseException.class);
    assertNoUncaughtException();
  }

  @Test
  public void queryGetFailsWhenChildHasInvalidPriority() throws Exception {
    respondToGetWith("{'a': {'.priority': {'x': 1}, '.value': 1}}");
    Repo repo = newRepo();
    Query query = fooReference(repo).orderByKey().limitToFirst(1);

    Task<DataSnapshot> task = awaitCompletion(query.get());

    assertThat(task.getException()).isInstanceOf(DatabaseException.class);
    // No keep-synced registration was left behind. It would hold a tag for the query.
    assertThat(repo.getServerSyncTree().tagForQuery(query.getSpec())).isNull();
    assertNoUncaughtException();
  }

  @Test
  public void getReturnsPersistedValueWhenResponseCannotBeParsed() throws Exception {
    persistValueAtFoo("persisted");
    respondToGetWith("{'.priority': true, '.value': 1}");

    Task<DataSnapshot> task = awaitCompletion(fooReference(newRepo()).get());

    assertThat(task.getResult().getValue()).isEqualTo("persisted");
    assertNoUncaughtException();
  }

  @Test
  public void getReturnsServerValue() throws Exception {
    respondToGetWith("{'a': 1, 'b': {'.priority': 2, '.value': 'x'}}");

    DataSnapshot snapshot = awaitCompletion(fooReference(newRepo()).get()).getResult();

    assertThat(snapshot.getValue()).isEqualTo(new MapBuilder().put("a", 1L).put("b", "x").build());
    assertThat(snapshot.child("b").getPriority()).isEqualTo(2.0);
    assertNoUncaughtException();
  }

  @Test
  public void queryGetReturnsServerValue() throws Exception {
    respondToGetWith("{'a': 1, 'b': 2}");
    Query query = fooReference(newRepo()).orderByKey().limitToFirst(2);

    DataSnapshot snapshot = awaitCompletion(query.get()).getResult();

    assertThat(snapshot.getValue()).isEqualTo(new MapBuilder().put("a", 1L).put("b", 2L).build());
    assertNoUncaughtException();
  }

  private Repo newRepo() {
    return new Repo(
        Utilities.parseUrl(TestValues.TEST_NAMESPACE).repoInfo, config, /* database= */ null);
  }

  private static DatabaseReference fooReference(Repo repo) {
    return InternalHelpers.createReference(repo, new Path("foo"));
  }

  /**
   * Makes the connection answer every {@code get()} request with {@code json}, parsed the same way
   * as messages from the server. Single quotes in {@code json} are replaced with double quotes.
   */
  private void respondToGetWith(String json) throws IOException {
    Object response = JsonMapper.parseJsonValue(json.replace('\'', '"'));
    when(connection.get(any(), any())).thenReturn(Tasks.forResult(response));
  }

  /** Stores {@code value} for {@code /foo} in the persistence cache. Call before {@link #newRepo}. */
  private void persistValueAtFoo(Object value) {
    QuerySpec query = QuerySpec.defaultQueryAtPath(new Path("foo"));
    PersistenceManager persistence =
        new DefaultPersistenceManager(config, new MockPersistenceStorageEngine(), CachePolicy.NONE);
    persistence.runInTransaction(
        () -> {
          persistence.setQueryActive(query);
          persistence.updateServerCache(query, NodeUtilities.NodeFromJSON(value));
          return null;
        });
    config.forcePersistenceManager(persistence);
  }

  private static <T> Task<T> awaitCompletion(Task<T> task) throws InterruptedException {
    CountDownLatch completed = new CountDownLatch(1);
    task.addOnCompleteListener(Runnable::run, unused -> completed.countDown());
    assertWithMessage("Task did not complete")
        .that(completed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        .isTrue();
    return task;
  }

  private void assertNoUncaughtException() {
    assertThat(runLoop.uncaughtException.get()).isNull();
  }
}
