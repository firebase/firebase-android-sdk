// Copyright 2019 Google LLC
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

package com.google.firebase.firestore.remote;

import com.google.android.gms.tasks.Task;

/** A class that encapsulates the platform-specific initialization of the SSL context. */
public interface SslProvider {
  // PORTING NOTE: This class only exists on Android.

  /** Initializes the SSL context and resolves Task on completion. */
  Task<Void> initializeSsl();
}
