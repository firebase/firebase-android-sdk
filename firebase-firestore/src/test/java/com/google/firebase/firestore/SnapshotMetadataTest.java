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

package com.google.firebase.firestore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SnapshotMetadataTest {

  @Test
  public void testEquals() {
    SnapshotMetadata foo = new SnapshotMetadata(true, true);
    SnapshotMetadata fooDup = new SnapshotMetadata(true, true);
    SnapshotMetadata bar = new SnapshotMetadata(true, false);
    SnapshotMetadata baz = new SnapshotMetadata(false, true);
    assertEquals(foo, fooDup);
    assertNotEquals(foo, bar);
    assertNotEquals(foo, baz);
    assertNotEquals(bar, baz);

    assertEquals(foo.hashCode(), fooDup.hashCode());
    assertNotEquals(foo.hashCode(), bar.hashCode());
    assertNotEquals(foo.hashCode(), baz.hashCode());
    assertNotEquals(bar.hashCode(), baz.hashCode());
  }
}
