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

package com.google.firebase.messaging.threads;

/**
 * Commonly used thread priorities.
 *
 * <p>A true, but simple, enum is used for type safety to prevent mistakes like thread pool
 * factories frequently take in integers that could be mistaken for an {@code IntDef} if the caller
 * is not using Android Lint. The {@code @SimpleEnum} annotation is not used because of complex
 * downstream usage, so minification by ProGuard depends on the app's usage.
 */
public enum ThreadPriority {

  /**
   * Reduced performance but at greater power efficiency. If the user isn't staring at a spinner
   * while you're doing your work, or if more CPU power won't help, or if you're just not sure what
   * to choose - then this is for you!
   *
   * <p>Example use cases: - Doing auto-backup. - Cleaning up a database. - Computing contextual
   * signals in the background. - Pretty much anything that's I/O bound (tasks that are mostly disk
   * and network access). - A sane default.
   *
   * <p>Such tasks will be yield to more important work, but won't be starved, and will still be
   * allowed to make forward progress. On some devices with big.LITTLE CPU architectures running
   * Marshmallow, your tasks may be locked to the little cores, executing slower but drawing 3x less
   * power for the same amount of work! (This is primarily true for Nexus 5x/6p devices, and any
   * other OEM with specialized kernel logic; it is not true for Pixels.)
   *
   * <p>See b/25246923 for more.
   */
  LOW_POWER,

  /**
   * Better performance at the expense of power and execution of other tasks. If the user will be
   * waiting for your work to complete (i.e. staring at a spinner), then this is a good choice.
   * Often this is the case for handling client app requests. Otherwise, it's best to use {@link
   * #LOW_POWER}.
   *
   * <p>Tasks with this priority will be allowed to contend with other urgent things across the
   * system. On devices with big.LITTLE CPU architectures running Marshmallow, your tasks will be
   * allowed to schedule on the big cores, executing faster but drawing 3x more power for the same
   * amount of work.
   *
   * <p>Some tasks require even higher priority, such as some UI work or real-time audio. If needed,
   * create your own thread(s) with an elevated priority. Please be careful!
   *
   * <p>See b/25246923 for more.
   */
  HIGH_SPEED
}
