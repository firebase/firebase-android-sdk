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

import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.core.ComponentProvider;
import com.google.firebase.firestore.core.MemoryComponentProvider;
import com.google.firebase.firestore.local.LocalSerializer;
import com.google.firebase.firestore.local.LruGarbageCollector;
import com.google.firebase.firestore.local.MemoryPersistence;
import com.google.firebase.firestore.local.Persistence;
import com.google.firebase.firestore.remote.RemoteComponenetProvider;
import java.util.Set;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23, manifest = Config.NONE)
public class MemorySpecTest extends SpecTestCase {

  private static final String DURABLE_PERSISTENCE = "durable-persistence";

  @Override
  protected boolean isExcluded(Set<String> tags) {
    return tags.contains(DURABLE_PERSISTENCE);
  }

  @Override
  protected MemoryComponentProvider initializeComponentProvider(
      RemoteComponenetProvider remoteProvider,
      ComponentProvider.Configuration configuration,
      boolean useEagerGc) {
    MemoryComponentProvider provider =
        new MemoryComponentProvider(new FirebaseFirestoreSettings.Builder().build()) {
          @Override
          protected Persistence createPersistence(Configuration configuration) {
            if (useEagerGc) {
              return MemoryPersistence.createEagerGcMemoryPersistence();
            } else {
              LocalSerializer serializer = new LocalSerializer(getRemoteSerializer());
              return MemoryPersistence.createLruGcMemoryPersistence(
                  LruGarbageCollector.Params.Default(), serializer);
            }
          }
        };
    provider.setRemoteProvider(remoteProvider);
    provider.initialize(configuration);
    return provider;
  }
}
