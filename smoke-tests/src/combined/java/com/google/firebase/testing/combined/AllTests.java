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

package com.google.firebase.testing.combined;

import com.google.firebase.testing.database.DatabaseTest;
import com.google.firebase.testing.firestore.FirestoreTest;
import com.google.firebase.testing.functions.FunctionsTest;
import com.google.firebase.testing.remoteconfig.RemoteConfigTest;
import com.google.firebase.testing.storage.StorageTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * A test suite combining the individual product flavors.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
  DatabaseTest.class,
  FirestoreTest.class,
  FunctionsTest.class,
  RemoteConfigTest.class,
  StorageTest.class,
})
public final class AllTests {}
