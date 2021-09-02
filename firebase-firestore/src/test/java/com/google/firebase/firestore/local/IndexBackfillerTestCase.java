// Copyright 2021 Google LLC
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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import com.google.firebase.firestore.index.IndexEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public abstract class IndexBackfillerTestCase {
  @Rule public TestName name = new TestName();

  private Persistence persistence;
  private IndexBackfillerDelegate delegate;

  @Before
  public void setUp() {
    persistence = getPersistence();
    delegate = persistence.getIndexBackfillDelegate();
  }

  @After
  public void tearDown() {
    persistence.shutdown();
  }

  abstract Persistence getPersistence();

  @Test
  public void roundTripIndexEntry() {
    delegate.addIndexEntry();
    IndexEntry entry = delegate.getIndexEntry(1);
    assertNotNull(entry);
    assertEquals("sample-documentId", entry.getDocumentId());
  }
}
