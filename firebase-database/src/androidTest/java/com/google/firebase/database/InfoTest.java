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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.RepoManager;
import com.google.firebase.database.future.ReadFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

@org.junit.runner.RunWith(AndroidJUnit4.class)
public class InfoTest {
  @Rule public RetryRule retryRule = new RetryRule(3);

  @After
  public void tearDown() {
    IntegrationTestHelpers.failOnFirstUncaughtException();
  }

  private DatabaseReference getRootNode() throws DatabaseException {
    return IntegrationTestHelpers.getRandomNode().getRoot();
  }

  @Test
  public void canGetAReferenceToDotInfoNodes() throws DatabaseException {
    DatabaseReference root = getRootNode();

    assertEquals(IntegrationTestValues.getNamespace() + "/.info", root.child(".info").toString());
    assertEquals(
        IntegrationTestValues.getNamespace() + "/.info/foo", root.child(".info/foo").toString());

    DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);
    DatabaseReference ref =
        new DatabaseReference(IntegrationTestValues.getNamespace() + "/.info", ctx);
    assertEquals(IntegrationTestValues.getNamespace() + "/.info", ref.toString());
    ref = new DatabaseReference(IntegrationTestValues.getNamespace() + "/.info/foo", ctx);
    assertEquals(IntegrationTestValues.getNamespace() + "/.info/foo", ref.toString());
  }

  @Test
  public void cantWriteToDotInfo() throws DatabaseException {
    DatabaseReference ref = getRootNode().child(".info");

    try {
      ref.setValue("hi");
      fail("Should not be allowed");
    } catch (DatabaseException e) {
      // No-op, expected
    }

    try {
      ref.setValue("hi", 5);
      fail("Should not be allowed");
    } catch (DatabaseException e) {
      // No-op, expected
    }

    try {
      ref.setPriority("hi");
      fail("Should not be allowed");
    } catch (DatabaseException e) {
      // No-op, expected
    }

    try {
      ref.runTransaction(
          new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
              fail("Should not get called");
              return null;
            }

            @Override
            public void onComplete(
                DatabaseError error, boolean committed, DataSnapshot currentData) {
              fail("Should not get called");
            }
          });
      fail("Should not be allowed");
    } catch (DatabaseException e) {
      // No-op, expected
    }

    try {
      ref.removeValue();
      fail("Should not be allowed");
    } catch (DatabaseException e) {
      // No-op, expected
    }

    try {
      ref.child("test").setValue("hi");
      fail("Should not be allowed");
    } catch (DatabaseException e) {
      // No-op, expected
    }
  }

  @Test
  public void canWatchInfoConnected()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = getRootNode();

    DataSnapshot snap =
        new ReadFuture(ref.child(".info/connected")).timedGet().get(0).getSnapshot();

    assertTrue(snap.getValue() instanceof Boolean);
  }

  @Test
  public void testManualConnectionManagementWorks()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    // Wait until we're connected to the database
    ReadFuture.untilEquals(ref.getRoot().child(".info/connected"), true).timedGet();

    ref.getDatabase().goOffline();

    // Ensure we're disconnected from both Databases
    DataSnapshot snap =
        new ReadFuture(ref.getRoot().child(".info/connected")).timedGet().get(0).getSnapshot();
    assertFalse((Boolean) snap.getValue());

    // Ensure that we don't automatically reconnect upon new DatabaseReference creation
    DatabaseReference refDup = ref.getDatabase().getReference();

    try {
      ReadFuture.untilEquals(refDup.child(".info/connected"), true)
          .timedGet(1500, TimeUnit.MILLISECONDS);
      Assert.fail();
    } catch (TimeoutException e) { //
    }

    ref.getDatabase().goOnline();
    // Ensure we're reconnected to both Databases
    ReadFuture.untilEquals(ref.getRoot().child(".info/connected"), true).timedGet();
  }

  @Test
  public void dotInfoConnectedGoesToFalseWhenDisconnected()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = getRootNode();

    // Wait until we're connected
    ReadFuture.untilEquals(ref.child(".info/connected"), true).timedGet();

    DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);
    RepoManager.interrupt(ctx);

    DataSnapshot snap =
        new ReadFuture(ref.child(".info/connected")).timedGet().get(0).getSnapshot();
    assertFalse((Boolean) snap.getValue());
    RepoManager.resume(ctx);
  }

  @Test
  public void dotInfoServerTimeOffset()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = getRootNode();

    DataSnapshot snap =
        new ReadFuture(ref.child(".info/serverTimeOffset")).timedGet().get(0).getSnapshot();
    assertTrue(snap.getValue() instanceof Long);
  }
}
