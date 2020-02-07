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

package com.google.firebase.database.connection;

import static org.junit.Assert.assertFalse;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.database.IntegrationTestHelpers;
import com.google.firebase.database.IntegrationTestValues;
import com.google.firebase.database.RetryRule;
import com.google.firebase.database.core.DatabaseConfig;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.junit.Rule;
import org.junit.Test;

// TODO: Move this test to separate firebase-database-connection
// tests.
@org.junit.runner.RunWith(AndroidJUnit4.class)
public class ConnectionTest {
  @Rule public RetryRule retryRule = new RetryRule(3);

  /**
   * @throws InterruptedException Test to see if we can get a sessionID from a Connection and pass
   *     it up to the Delegate
   */
  @Test
  public void testObtainSessionID() throws InterruptedException {
    final Semaphore valSemaphore = new Semaphore(0);
    Connection.Delegate del =
        new Connection.Delegate() {
          @Override
          public void onReady(long timestamp, String sessionId) {
            assertFalse("sessionId is null", sessionId == null);
            assertFalse("sessionId is empty", sessionId.isEmpty());
            valSemaphore.release();
          }

          @Override
          public void onDataMessage(Map<String, Object> message) {}

          @Override
          public void onDisconnect(Connection.DisconnectReason reason) {}

          @Override
          public void onKill(String reason) {}

          @Override
          public void onCacheHost(String s) {}
        };
    HostInfo info =
        new HostInfo(
            IntegrationTestValues.getProjectId() + "." + IntegrationTestValues.getServer(),
            IntegrationTestValues.getProjectId(),
            /*secure=*/ true);
    DatabaseConfig config = IntegrationTestHelpers.newFrozenTestConfig();
    Connection conn = new Connection(config.getConnectionContext(), info, null, del, null);
    conn.open();
    IntegrationTestHelpers.waitFor(valSemaphore);
  }
}
