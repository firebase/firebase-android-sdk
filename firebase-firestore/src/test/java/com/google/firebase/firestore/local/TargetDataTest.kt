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
package com.google.firebase.firestore.local

import com.google.firebase.firestore.core.TargetOrPipeline
import com.google.firebase.firestore.core.TargetOrPipeline.TargetWrapper
import com.google.firebase.firestore.model.SnapshotVersion
import com.google.firebase.firestore.remote.RemoteTargetData
import com.google.firebase.firestore.remote.RemoteTargetId.Companion.from
import com.google.firebase.firestore.remote.WatchStream
import com.google.firebase.firestore.testutil.TestUtil
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TargetDataTest {
  @Test
  fun testConstructorWithInt() {
    val target: TargetOrPipeline = TargetWrapper(TestUtil.query("c").toTarget())
    val targetData = TargetData(target, 42, 1, QueryPurpose.LISTEN)

    Assert.assertEquals(42, targetData.targetId.toLong())
  }

  @Test
  fun testConstructorWithRemoteTargetId() {
    val target: TargetOrPipeline = TargetWrapper(TestUtil.query("c").toTarget())
    val remoteTargetId = from(42)
    val targetData =
      RemoteTargetData(
        target,
        remoteTargetId,
        1,
        QueryPurpose.LISTEN,
        SnapshotVersion.NONE,
        SnapshotVersion.NONE,
        WatchStream.EMPTY_RESUME_TOKEN,
        null
      )

    Assert.assertEquals(remoteTargetId, targetData.targetId)
  }

  @Test
  fun testWithMethodsPreserveRemoteTargetId() {
    val target: TargetOrPipeline = TargetWrapper(TestUtil.query("c").toTarget())
    val remoteTargetId = from(42)
    val targetData =
      RemoteTargetData(
        target,
        remoteTargetId,
        1,
        QueryPurpose.LISTEN,
        SnapshotVersion.NONE,
        SnapshotVersion.NONE,
        WatchStream.EMPTY_RESUME_TOKEN,
        null
      )

    var updated = targetData.withSequenceNumber(2)
    Assert.assertEquals(remoteTargetId, updated.targetId)

    updated = targetData.withResumeToken(WatchStream.EMPTY_RESUME_TOKEN, SnapshotVersion.NONE)
    Assert.assertEquals(remoteTargetId, updated.targetId)

    updated = targetData.withExpectedCount(10)
    Assert.assertEquals(remoteTargetId, updated.targetId)

    updated = targetData.withLastLimboFreeSnapshotVersion(SnapshotVersion.NONE)
    Assert.assertEquals(remoteTargetId, updated.targetId)
  }

  @Test
  fun testEqualsAndHashCode() {
    val target: TargetOrPipeline = TargetWrapper(TestUtil.query("c").toTarget())
    val remoteTargetId1 = from(42)
    val remoteTargetId2 = from(42)
    val remoteTargetId3 = from(43)

    val targetData1 =
      RemoteTargetData(
        target,
        remoteTargetId1,
        1,
        QueryPurpose.LISTEN,
        SnapshotVersion.NONE,
        SnapshotVersion.NONE,
        WatchStream.EMPTY_RESUME_TOKEN,
        null
      )

    val targetData2 =
      RemoteTargetData(
        target,
        remoteTargetId2,
        1,
        QueryPurpose.LISTEN,
        SnapshotVersion.NONE,
        SnapshotVersion.NONE,
        WatchStream.EMPTY_RESUME_TOKEN,
        null
      )

    val targetData3 =
      RemoteTargetData(
        target,
        remoteTargetId3,
        1,
        QueryPurpose.LISTEN,
        SnapshotVersion.NONE,
        SnapshotVersion.NONE,
        WatchStream.EMPTY_RESUME_TOKEN,
        null
      )

    val targetDataInt =
      TargetData(
        target,
        42,
        1,
        QueryPurpose.LISTEN,
        SnapshotVersion.NONE,
        SnapshotVersion.NONE,
        WatchStream.EMPTY_RESUME_TOKEN,
        null
      )

    Assert.assertEquals(targetData1, targetData2)
    Assert.assertEquals(targetData1.hashCode().toLong(), targetData2.hashCode().toLong())

    Assert.assertNotEquals(targetData1, targetData3)
    Assert.assertNotEquals(targetData1, targetDataInt)
  }
}
