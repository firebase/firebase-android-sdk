// Copyright 2020 Google LLC
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

package com.google.firebase.emulators;

import com.google.android.gms.common.annotation.KeepForSdk;

/**
 * Enum for Firebase services that can be emulated using the Firebase Emulator Suite.
 *
 * <p>TODO(samstern): Un-hide this once Firestore, Database, and Functions are implemented
 *
 * @see com.google.firebase.FirebaseApp#enableEmulators(EmulatorSettings)
 * @see EmulatorSettings
 * @see EmulatedServiceSettings
 * @hide
 */
@KeepForSdk
public enum FirebaseEmulators {
  DATABASE,
  FIRESTORE,
  FUNCTIONS,
}
