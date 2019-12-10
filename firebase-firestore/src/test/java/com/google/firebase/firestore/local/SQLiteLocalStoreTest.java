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

import java.util.Arrays;
import java.util.Collection;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SQLiteLocalStoreTest extends LocalStoreTestCase {

  private QueryEngine queryEngine;

  @ParameterizedRobolectricTestRunner.Parameters(name = "QueryEngine = {0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[] {new SimpleQueryEngine()}, new Object[] {new IndexFreeQueryEngine()});
  }

  public SQLiteLocalStoreTest(QueryEngine queryEngine) {
    this.queryEngine = queryEngine;
  }

  @Override
  QueryEngine getQueryEngine() {
    return this.queryEngine;
  }

  @Override
  Persistence getPersistence() {
    return PersistenceTestHelpers.createSQLitePersistence();
  }

  @Override
  boolean garbageCollectorIsEager() {
    return false;
  }
}
