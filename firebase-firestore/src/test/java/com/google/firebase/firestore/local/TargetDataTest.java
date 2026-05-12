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

package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.testutil.TestUtil.query;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.google.firebase.firestore.core.TargetOrPipeline;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.remote.RemoteTargetData;
import com.google.firebase.firestore.remote.RemoteTargetId;
import com.google.firebase.firestore.remote.WatchStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class TargetDataTest {

  @Test
  public void testConstructorWithInt() {
    TargetOrPipeline target = new TargetOrPipeline.TargetWrapper(query("c").toTarget());
    TargetData targetData = new TargetData(target, 42, 1, QueryPurpose.LISTEN);

    assertEquals(42, (int) targetData.getTargetId());
  }

  @Test
  public void testConstructorWithRemoteTargetId() {
    TargetOrPipeline target = new TargetOrPipeline.TargetWrapper(query("c").toTarget());
    RemoteTargetId remoteTargetId = RemoteTargetId.from(42);
    RemoteTargetData targetData =
        new RemoteTargetData(
            target,
            remoteTargetId,
            1,
            QueryPurpose.LISTEN,
            SnapshotVersion.NONE,
            SnapshotVersion.NONE,
            WatchStream.EMPTY_RESUME_TOKEN,
            null);

    assertEquals(remoteTargetId, targetData.getTargetId());
  }

  @Test
  public void testWithMethodsPreserveRemoteTargetId() {
    TargetOrPipeline target = new TargetOrPipeline.TargetWrapper(query("c").toTarget());
    RemoteTargetId remoteTargetId = RemoteTargetId.from(42);
    RemoteTargetData targetData =
        new RemoteTargetData(
            target,
            remoteTargetId,
            1,
            QueryPurpose.LISTEN,
            SnapshotVersion.NONE,
            SnapshotVersion.NONE,
            WatchStream.EMPTY_RESUME_TOKEN,
            null);

    RemoteTargetData updated = targetData.withSequenceNumber(2);
    assertEquals(remoteTargetId, updated.getTargetId());

    updated = targetData.withResumeToken(WatchStream.EMPTY_RESUME_TOKEN, SnapshotVersion.NONE);
    assertEquals(remoteTargetId, updated.getTargetId());

    updated = targetData.withExpectedCount(10);
    assertEquals(remoteTargetId, updated.getTargetId());

    updated = targetData.withLastLimboFreeSnapshotVersion(SnapshotVersion.NONE);
    assertEquals(remoteTargetId, updated.getTargetId());
  }

  @Test
  public void testEqualsAndHashCode() {
    TargetOrPipeline target = new TargetOrPipeline.TargetWrapper(query("c").toTarget());
    RemoteTargetId remoteTargetId1 = RemoteTargetId.from(42);
    RemoteTargetId remoteTargetId2 = RemoteTargetId.from(42);
    RemoteTargetId remoteTargetId3 = RemoteTargetId.from(43);

    RemoteTargetData targetData1 =
        new RemoteTargetData(
            target,
            remoteTargetId1,
            1,
            QueryPurpose.LISTEN,
            SnapshotVersion.NONE,
            SnapshotVersion.NONE,
            WatchStream.EMPTY_RESUME_TOKEN,
            null);

    RemoteTargetData targetData2 =
        new RemoteTargetData(
            target,
            remoteTargetId2,
            1,
            QueryPurpose.LISTEN,
            SnapshotVersion.NONE,
            SnapshotVersion.NONE,
            WatchStream.EMPTY_RESUME_TOKEN,
            null);

    RemoteTargetData targetData3 =
        new RemoteTargetData(
            target,
            remoteTargetId3,
            1,
            QueryPurpose.LISTEN,
            SnapshotVersion.NONE,
            SnapshotVersion.NONE,
            WatchStream.EMPTY_RESUME_TOKEN,
            null);

    TargetData targetDataInt =
        new TargetData(
            target,
            42,
            1,
            QueryPurpose.LISTEN,
            SnapshotVersion.NONE,
            SnapshotVersion.NONE,
            WatchStream.EMPTY_RESUME_TOKEN,
            null);

    assertEquals(targetData1, targetData2);
    assertEquals(targetData1.hashCode(), targetData2.hashCode());

    assertNotEquals(targetData1, targetData3);
    assertNotEquals(targetData1, targetDataInt);
  }
}
