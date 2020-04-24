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

package com.google.firebase.firestore.spec;

import com.google.firebase.firestore.core.ComponentProvider;
import com.google.firebase.firestore.core.SQLiteComponentProvider;
import java.util.Set;
import org.json.JSONObject;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SQLiteSpecTest extends SpecTestCase {

  private static final String EAGER_GC = "eager-gc";

  @Override
  protected void specSetUp(JSONObject config) {
    super.specSetUp(config);
  }

  @Override
  protected void specTearDown() throws Exception {
    super.specTearDown();
  }

  @Override
  protected SQLiteComponentProvider initializeComponentProvider(
      ComponentProvider.Configuration configuration, boolean garbageCollectionEnabled) {
    SQLiteComponentProvider provider = new SQLiteComponentProvider();
    provider.initialize(configuration);
    return provider;
  }

  @Override
  protected boolean isExcluded(Set<String> tags) {
    return tags.contains(EAGER_GC);
  }
}
