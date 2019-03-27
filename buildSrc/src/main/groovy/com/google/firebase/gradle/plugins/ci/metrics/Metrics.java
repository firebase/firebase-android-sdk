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

package com.google.firebase.gradle.plugins.ci.metrics;

import java.util.function.Predicate;
import org.gradle.api.Task;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;

/** Provides methods for measuring various parts of the build. */
interface Metrics {
  /** Measure successful execution of a task. */
  void measureSuccess(Task task, long elapsedTime);

  /** Measure task execution failure. */
  void measureFailure(Task task);

  /**
   * Creates a {@link Metrics} implementation that uses a {@code predicate} to determine whether to
   * emit measurements for it.
   */
  static Metrics filtered(Metrics metrics, Predicate<Task> predicate) {
    return new Metrics() {
      @Override
      public void measureSuccess(Task task, long elapsedTime) {
        if (predicate.test(task)) {
          metrics.measureSuccess(task, elapsedTime);
        }
      }

      @Override
      public void measureFailure(Task task) {
        if (predicate.test(task)) {
          metrics.measureFailure(task);
        }
      }
    };
  }

  /** Creates a {@link Metrics} implementation that logs results at the specified level. */
  static Metrics toLog(Logger toLogger, LogLevel level) {
    return new Metrics() {
      @Override
      public void measureSuccess(Task task, long elapsedTime) {
        toLogger.log(level, "[METRICS] Task {} took {}ms.", task.getPath(), elapsedTime);
      }

      @Override
      public void measureFailure(Task task) {
        toLogger.log(level, "[METRICS] Task {} failed.", task.getPath());
      }
    };
  }
}
