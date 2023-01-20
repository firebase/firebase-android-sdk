// Copyright 2023 Google LLC
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

package com.google.firebase.concurrent;

import java.util.concurrent.Executor;

/**
 * An {@link Executor} that can be paused/resumed.
 *
 * <p>When the executor is {@link #pause() paused}, tasks get queued and execute when {@link
 * #resume()} is called.
 */
public interface PausableExecutor extends Executor {

  void pause();

  void resume();

  boolean isPaused();
}
