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

import static com.google.firebase.firestore.testutil.TestUtil.query;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.SnapshotVersion;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class SQLiteTargetCacheTest extends TargetCacheTestCase {

  @Override
  Persistence getPersistence() {
    return PersistenceTestHelpers.createSQLitePersistence();
  }

  @Test
  public void testMetadataPersistedAcrossRestarts() {
    String name = "test-targetCache-restarts";

    SQLitePersistence db1 = PersistenceTestHelpers.createSQLitePersistence(name);
    TargetCache targetCache1 = db1.getTargetCache();
    assertEquals(0, targetCache1.getHighestListenSequenceNumber());

    long originalSequenceNumber = 1234;
    int targetId = 5;
    SnapshotVersion snapshotVersion = new SnapshotVersion(new Timestamp(1, 2));

    Query query = query("rooms");
    TargetData targetData =
        new TargetData(query.toTarget(), targetId, originalSequenceNumber, QueryPurpose.LISTEN);
    db1.runTransaction(
        "add query data",
        () -> {
          targetCache1.addTargetData(targetData);
          targetCache1.setLastRemoteSnapshotVersion(snapshotVersion);
        });

    db1.shutdown();

    SQLitePersistence db2 = PersistenceTestHelpers.createSQLitePersistence(name);
    db2.runTransaction(
        "verify sequence number",
        () -> {
          long newSequenceNumber = db2.getReferenceDelegate().getCurrentSequenceNumber();
          assertTrue(newSequenceNumber > originalSequenceNumber);
        });
    TargetCache targetCache2 = db2.getTargetCache();
    assertEquals(targetId, targetCache2.getHighestTargetId());
    assertEquals(snapshotVersion, targetCache2.getLastRemoteSnapshotVersion());
    assertEquals(1, targetCache2.getTargetCount());
    db2.shutdown();
  }
}
