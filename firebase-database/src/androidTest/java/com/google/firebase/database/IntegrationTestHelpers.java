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

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;
import static com.google.firebase.database.snapshot.NodeUtilities.NodeFromJSON;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.android.AndroidAuthTokenProvider;
import com.google.firebase.database.core.CompoundWrite;
import com.google.firebase.database.core.Context;
import com.google.firebase.database.core.CoreTestHelpers;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.EventTarget;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.persistence.PersistenceManager;
import com.google.firebase.database.core.utilities.DefaultRunLoop;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.future.WriteFuture;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.Node;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class IntegrationTestHelpers {

  private static List<DatabaseConfig> contexts = new ArrayList<DatabaseConfig>();
  private static String testSecret = null;

  public static Path path(String path) {
    return new Path(path);
  }

  public static ChildKey childKey(String key) {
    return ChildKey.fromString(key);
  }

  public static Map<String, Object> mapFromJsonString(String json) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Object objectFromJsonString(String json) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.readValue(json, new TypeReference<Object>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Map<String, Object> mapFromSingleQuotedString(String json) {
    return mapFromJsonString(json.replace("'", "\""));
  }

  public static Object objectFromSingleQuotedString(String json) {
    return objectFromJsonString(json.replace("'", "\""));
  }

  public static Node node(String json) {
    return NodeFromJSON(objectFromSingleQuotedString(json));
  }

  public static Node leafNodeOfSize(int size) {
    StringBuilder builder = new StringBuilder();
    String pattern = "abdefghijklmopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    int patternLength = pattern.length();
    for (int i = 0; i < size - patternLength; i = i + patternLength) { // 10 MB String
      builder.append(pattern);
    }
    int remainingLength = size - builder.length();
    builder.append(pattern.substring(0, remainingLength));
    hardAssert(builder.length() == size, "The string size did not match the expected size");
    return NodeFromJSON(builder.toString());
  }

  public static CompoundWrite compoundWrite(String json) {
    return CompoundWrite.fromValue(mapFromSingleQuotedString(json));
  }

  public static QuerySpec defaultQueryAt(String path) {
    return QuerySpec.defaultQueryAtPath(new Path(path));
  }

  public static <T> Set<T> asSet(List<T> list) {
    return new HashSet<T>(list);
  }

  public static <T> Set<T> asSet(T... objects) {
    return new HashSet<T>(Arrays.asList(objects));
  }

  public static Set<ChildKey> childKeySet(String... keys) {
    Set<ChildKey> childKeys = new HashSet<ChildKey>();
    for (String key : keys) {
      childKeys.add(ChildKey.fromString(key));
    }
    return childKeys;
  }

  private static class TestEventTarget implements EventTarget {
    AtomicReference<Throwable> caughtException = new AtomicReference<Throwable>(null);

    int poolSize = 1;
    BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            poolSize,
            poolSize,
            0,
            TimeUnit.NANOSECONDS,
            queue,
            new ThreadFactory() {

              ThreadFactory wrappedFactory = Executors.defaultThreadFactory();

              @Override
              public Thread newThread(Runnable r) {
                Thread thread = wrappedFactory.newThread(r);
                thread.setName("FirebaseDatabaseTestsEventTarget");
                // TODO: should we set an uncaught exception handler here? Probably want to let
                // exceptions happen...
                thread.setUncaughtExceptionHandler(
                    new Thread.UncaughtExceptionHandler() {
                      @Override
                      public void uncaughtException(Thread t, Throwable e) {
                        e.printStackTrace();
                        caughtException.set(e);
                      }
                    });
                return thread;
              }
            });

    @Override
    public void postEvent(Runnable r) {
      executor.execute(r);
    }

    @Override
    public void shutdown() {}

    @Override
    public void restart() {}
  }

  private static class TestRunLoop extends DefaultRunLoop {
    AtomicReference<Throwable> caughtException = new AtomicReference<Throwable>(null);

    @Override
    public void handleException(Throwable e) {
      e.printStackTrace();
      caughtException.set(e);
    }
  }

  public static void failOnFirstUncaughtException() {
    for (Context ctx : contexts) {
      TestEventTarget eventTarget = (TestEventTarget) ctx.getEventTarget();
      Throwable t = eventTarget.caughtException.getAndSet(null);
      if (t != null) {
        t.printStackTrace();
        fail("Found error on event target");
      }
      TestRunLoop runLoop = (TestRunLoop) ctx.getRunLoop();
      t = runLoop.caughtException.getAndSet(null);
      if (t != null) {
        t.printStackTrace();
        fail("Found error on run loop");
      }
    }
  }

  public static DatabaseConfig getContext(int i) {
    ensureContexts(i + 1);
    return contexts.get(i);
  }

  public static DatabaseReference rootWithConfig(DatabaseConfig config) {
    return new DatabaseReference(IntegrationTestValues.getNamespace(), config);
  }

  public static DatabaseReference getRandomNode() throws DatabaseException {
    return getRandomNode(1).get(0);
  }

  public static void goOffline(DatabaseConfig cfg) {
    DatabaseReference.goOffline(cfg);
  }

  public static void goOnline(DatabaseConfig cfg) {
    DatabaseReference.goOnline(cfg);
  }

  public static List<DatabaseReference> getRandomNode(int count) throws DatabaseException {
    ensureContexts(count);

    List<DatabaseReference> results = new ArrayList<DatabaseReference>(count);
    String name = null;
    for (int i = 0; i < count; ++i) {
      DatabaseReference ref =
          new DatabaseReference(IntegrationTestValues.getNamespace(), contexts.get(i));
      if (name == null) {
        name = ref.push().getKey();
      }
      results.add(ref.child(name));
    }
    return results;
  }

  public static DatabaseConfig newFrozenTestConfig() {
    DatabaseConfig cfg = newTestConfig();
    CoreTestHelpers.freezeContext(cfg);
    return cfg;
  }

  public static DatabaseConfig newTestConfig() {
    TestRunLoop runLoop = new TestRunLoop();
    DatabaseConfig config = new DatabaseConfig();
    config.setLogLevel(Logger.Level.DEBUG);
    config.setEventTarget(new TestEventTarget());
    config.setRunLoop(runLoop);
    config.setFirebaseApp(FirebaseApp.getInstance());
    config.setAuthTokenProvider(AndroidAuthTokenProvider.forUnauthenticatedAccess());
    return config;
  }

  public static ScheduledExecutorService getExecutorService(DatabaseConfig config) {
    DefaultRunLoop runLoop = (DefaultRunLoop) config.getRunLoop();
    return runLoop.getExecutorService();
  }

  public static void setForcedPersistentCache(Context ctx, PersistenceManager manager) {
    try {
      Method method =
          Context.class.getDeclaredMethod("forcePersistenceManager", PersistenceManager.class);
      method.setAccessible(true);
      method.invoke(ctx, manager);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void setLogger(
      DatabaseConfig ctx, com.google.firebase.database.logging.Logger logger) {
    ctx.setLogger(logger);
  }

  private static void ensureContexts(int count) {
    for (int i = contexts.size(); i < count; ++i) {
      contexts.add(newTestConfig());
    }
  }

  public static String getTestSecret() {
    if (testSecret == null) {
      try {
        InputStream response =
            new URL(IntegrationTestValues.getNamespace() + "/.nsadmin/.json?key=1234").openStream();
        TypeReference<Map<String, Object>> t = new TypeReference<Map<String, Object>>() {};
        Map<String, Object> data = new ObjectMapper().readValue(response, t);
        testSecret = (String) ((List) data.get("secrets")).get(0);
      } catch (Throwable e) {
        throw new AssertionError("Could not get test secret. ", e);
      }
    }
    return testSecret;
  }

  public static void waitFor(Semaphore semaphore) throws InterruptedException {
    waitFor(semaphore, 1);
  }

  public static void waitFor(Semaphore semaphore, int count) throws InterruptedException {
    waitFor(semaphore, count, IntegrationTestValues.getTimeout(), TimeUnit.MILLISECONDS);
  }

  public static void waitFor(Semaphore semaphore, int count, long timeout, TimeUnit unit)
      throws InterruptedException {
    boolean success = semaphore.tryAcquire(count, timeout, unit);
    failOnFirstUncaughtException();
    assertTrue("Operation timed out", success);
  }

  public static DataSnapshot getSnap(Query ref) throws InterruptedException {

    final Semaphore semaphore = new Semaphore(0);

    // Hack to get around final reference issue
    final List<DataSnapshot> snapshotList = new ArrayList<DataSnapshot>(1);

    ref.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            snapshotList.add(snapshot);
            semaphore.release(1);
          }

          @Override
          public void onCancelled(DatabaseError error) {
            semaphore.release(1);
          }
        });

    semaphore.tryAcquire(1, IntegrationTestValues.getTimeout(), TimeUnit.MILLISECONDS);
    return snapshotList.get(0);
  }

  public static Map<String, Object> fromJsonString(String json) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Map<String, Object> fromSingleQuotedString(String json) {
    return fromJsonString(json.replace("'", "\""));
  }

  public static void waitForRoundtrip(DatabaseReference reader) {
    try {
      new WriteFuture(reader.getRoot().child(UUID.randomUUID().toString()), null, null).timedGet();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void waitForQueue(DatabaseReference ref) {
    try {
      final Semaphore semaphore = new Semaphore(0);
      ref.getRepo()
          .scheduleNow(
              new Runnable() {
                @Override
                public void run() {
                  semaphore.release();
                }
              });
      semaphore.acquire();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void waitForEvents(DatabaseReference ref) {
    try {
      // Make sure queue is done and all events are queued
      IntegrationTestHelpers.waitForQueue(ref);
      // Next, all events were queued, make sure all events are done raised
      final Semaphore semaphore = new Semaphore(0);
      ref.getRepo()
          .postEvent(
              new Runnable() {
                @Override
                public void run() {
                  semaphore.release();
                }
              });
      semaphore.acquire();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static String repeatedString(String s, int n) {
    String result = "";

    for (int i = 0; i < n; i++) {
      result += s;
    }
    return result;
  }

  // Create a (test) object which places a test value at then end of the
  // object path (e.g., a/b/c would yield {a: {b: {c: "test_value"}}}
  public static HashMap<String, Object> buildObjFromPath(Path path, Object testValue) {
    final HashMap<String, Object> result = new HashMap<String, Object>();

    HashMap<String, Object> parent = result;
    for (Iterator<ChildKey> i = path.iterator(); i.hasNext(); ) {
      ChildKey key = i.next();
      if (i.hasNext()) {
        HashMap<String, Object> child = new HashMap<String, Object>();
        parent.put(key.asString(), child);
        parent = child;
      } else {
        parent.put(key.asString(), testValue);
      }
    }

    return result;
  }

  // Lookup the value at the path in HashMap (e.g., "a/b/c").
  @SuppressWarnings("unchecked")
  public static Object applyPath(Object value, Path path) {
    for (ChildKey key : path) {
      value = ((HashMap<String, Object>) value).get(key.asString());
    }
    return value;
  }

  public static void assertContains(String str, String substr) {
    assertTrue("'" + str + "' does not contain '" + substr + "'.", str.contains(substr));
  }
}
