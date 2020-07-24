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

package com.google.firebase.functions;

import static com.google.firebase.functions.testutil.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.functions.FirebaseFunctionsException.Code;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CallTest {
  private static FirebaseApp app;

  @BeforeClass
  public static void setUp() {
    FirebaseApp.initializeApp(InstrumentationRegistry.getContext());
    app = FirebaseApp.getInstance();
  }

  @Test
  public void testData() throws InterruptedException, ExecutionException {
    FirebaseFunctions functions = FirebaseFunctions.getInstance(app);

    Map<String, Object> params = new HashMap<>();
    params.put("bool", true);
    params.put("int", 2);
    params.put("long", 3L);
    params.put("string", "four");
    params.put("array", Arrays.asList(5, 6));
    params.put("null", null);

    HttpsCallableReference function = functions.getHttpsCallable("dataTest");
    Task<HttpsCallableResult> result = function.call(params);
    Object actual = Tasks.await(result).getData();

    assertTrue(actual instanceof Map);
    Map<String, ?> map = (Map<String, ?>) actual;
    assertEquals("stub response", map.get("message"));
    assertEquals(42, map.get("code"));
    assertEquals(420L, map.get("long"));
  }

  @Test
  public void testScalars() throws InterruptedException, ExecutionException {
    FirebaseFunctions functions = FirebaseFunctions.getInstance(app);

    HttpsCallableReference function = functions.getHttpsCallable("scalarTest");
    Task<HttpsCallableResult> result = function.call(17);
    Object actual = Tasks.await(result).getData();

    assertEquals(76, actual);
  }

  @Test
  public void testToken() throws InterruptedException, ExecutionException {
    // Override the normal token provider to simulate FirebaseAuth being logged in.
    FirebaseFunctions functions =
        new FirebaseFunctions(
            app,
            app.getApplicationContext(),
            app.getOptions().getProjectId(),
            "us-central1",
            () -> {
              HttpsCallableContext context = new HttpsCallableContext("token", null);
              return Tasks.forResult(context);
            });

    HttpsCallableReference function = functions.getHttpsCallable("tokenTest");
    Task<HttpsCallableResult> result = function.call(new HashMap<>());
    Object actual = Tasks.await(result).getData();

    assertEquals(new HashMap<>(), actual);
  }

  @Test
  public void testInstanceId() throws InterruptedException, ExecutionException {
    // Override the normal token provider to simulate FirebaseAuth being logged in.
    FirebaseFunctions functions =
        new FirebaseFunctions(
            app,
            app.getApplicationContext(),
            app.getOptions().getProjectId(),
            "us-central1",
            () -> {
              HttpsCallableContext context = new HttpsCallableContext(null, "iid");
              return Tasks.forResult(context);
            });

    HttpsCallableReference function = functions.getHttpsCallable("instanceIdTest");
    Task<HttpsCallableResult> result = function.call(new HashMap<>());
    Object actual = Tasks.await(result).getData();

    assertEquals(new HashMap<>(), actual);
  }

  @Test
  public void testNull() throws InterruptedException, ExecutionException {
    FirebaseFunctions functions = FirebaseFunctions.getInstance(app);

    HttpsCallableReference function = functions.getHttpsCallable("nullTest");
    Task<HttpsCallableResult> result = function.call(null);
    Object actual = Tasks.await(result).getData();
    assertNull(actual);

    // Test with void arguments version.
    result = function.call();
    actual = Tasks.await(result).getData();
    assertNull(actual);
  }

  @Test
  public void testMissingResult() {
    FirebaseFunctions functions = FirebaseFunctions.getInstance(app);

    HttpsCallableReference function = functions.getHttpsCallable("missingResultTest");
    Task<HttpsCallableResult> result = function.call(null);
    ExecutionException exe = assertThrows(ExecutionException.class, () -> Tasks.await(result));
    Throwable cause = exe.getCause();
    assertTrue(cause.toString(), cause instanceof FirebaseFunctionsException);
    FirebaseFunctionsException ffe = (FirebaseFunctionsException) cause;
    assertEquals(Code.INTERNAL, ffe.getCode());
    assertEquals("Response is missing data field.", ffe.getMessage());
    assertNull(ffe.getDetails());
  }

  @Test
  public void testUnhandledError() {
    FirebaseFunctions functions = FirebaseFunctions.getInstance(app);

    HttpsCallableReference function = functions.getHttpsCallable("unhandledErrorTest");
    Task<HttpsCallableResult> result = function.call();
    ExecutionException exe = assertThrows(ExecutionException.class, () -> Tasks.await(result));
    Throwable cause = exe.getCause();
    assertTrue(cause.toString(), cause instanceof FirebaseFunctionsException);
    FirebaseFunctionsException ffe = (FirebaseFunctionsException) cause;
    assertEquals(Code.INTERNAL, ffe.getCode());
    assertEquals("INTERNAL", ffe.getMessage());
    assertNull(ffe.getDetails());
  }

  @Test
  public void testUnknownError() {
    FirebaseFunctions functions = FirebaseFunctions.getInstance(app);

    HttpsCallableReference function = functions.getHttpsCallable("unknownErrorTest");
    Task<HttpsCallableResult> result = function.call();
    ExecutionException exe = assertThrows(ExecutionException.class, () -> Tasks.await(result));
    Throwable cause = exe.getCause();
    assertTrue(cause.toString(), cause instanceof FirebaseFunctionsException);
    FirebaseFunctionsException ffe = (FirebaseFunctionsException) cause;
    assertEquals(Code.INTERNAL, ffe.getCode());
    assertEquals("INTERNAL", ffe.getMessage());
    assertNull(ffe.getDetails());
  }

  @Test
  public void testExplicitError() {
    FirebaseFunctions functions = FirebaseFunctions.getInstance(app);

    HttpsCallableReference function = functions.getHttpsCallable("explicitErrorTest");
    Task<HttpsCallableResult> result = function.call();
    ExecutionException exe = assertThrows(ExecutionException.class, () -> Tasks.await(result));
    Throwable cause = exe.getCause();
    assertTrue(cause.toString(), cause instanceof FirebaseFunctionsException);
    FirebaseFunctionsException ffe = (FirebaseFunctionsException) cause;
    assertEquals(Code.OUT_OF_RANGE, ffe.getCode());
    assertEquals("explicit nope", ffe.getMessage());
    assertNotNull(ffe.getDetails());
    assertTrue(ffe.getDetails().getClass().getCanonicalName(), ffe.getDetails() instanceof Map);
    Map<?, ?> details = (Map<?, ?>) ffe.getDetails();
    assertEquals(10, details.get("start"));
    assertEquals(20, details.get("end"));
    assertEquals(30L, details.get("long"));
  }

  @Test
  public void testHttpError() {
    FirebaseFunctions functions = FirebaseFunctions.getInstance(app);

    HttpsCallableReference function = functions.getHttpsCallable("httpErrorTest");
    Task<HttpsCallableResult> result = function.call();
    ExecutionException exe = assertThrows(ExecutionException.class, () -> Tasks.await(result));
    Throwable cause = exe.getCause();
    assertTrue(cause.toString(), cause instanceof FirebaseFunctionsException);
    FirebaseFunctionsException ffe = (FirebaseFunctionsException) cause;
    assertEquals(Code.INVALID_ARGUMENT, ffe.getCode());
    assertEquals("INVALID_ARGUMENT", ffe.getMessage());
    assertNull(ffe.getDetails());
  }

  @Test
  public void testTimeout() {
    FirebaseFunctions functions = FirebaseFunctions.getInstance(app);

    HttpsCallableReference function =
        functions.getHttpsCallable("timeoutTest").withTimeout(10, TimeUnit.MILLISECONDS);
    assertEquals(10, function.getTimeout());
    Task<HttpsCallableResult> result = function.call();
    ExecutionException exe = assertThrows(ExecutionException.class, () -> Tasks.await(result));
    Throwable cause = exe.getCause();
    assertTrue(cause.toString(), cause instanceof FirebaseFunctionsException);
    FirebaseFunctionsException ffe = (FirebaseFunctionsException) cause;
    assertEquals(Code.DEADLINE_EXCEEDED, ffe.getCode());
    assertEquals("DEADLINE_EXCEEDED", ffe.getMessage());
    assertNull(ffe.getDetails());
  }
}
