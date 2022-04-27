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

package com.google.firebase.perf.session;

/**
 * Any object that cares about changes in the {@link com.google.firebase.perf.session.PerfSession}
 * that is active for the given app. This object is then registered with the {@link SessionManager}
 * which then supplies it with updates as needed.
 */
public interface SessionAwareObject {

  /**
   * Updates the SessionAwareObject with the new sessionId.
   *
   * @param session The new PerfSession.
   */
  void updateSession(PerfSession session);
}
