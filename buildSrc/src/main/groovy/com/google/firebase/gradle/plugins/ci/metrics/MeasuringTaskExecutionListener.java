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

import java.util.HashMap;
import java.util.Map;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.tasks.TaskState;

class MeasuringTaskExecutionListener implements TaskExecutionListener {
  private final Map<Task, Long> taskStartTimes = new HashMap<>();
  private final Metrics metrics;

  MeasuringTaskExecutionListener(Metrics metrics) {
    this.metrics = metrics;
  }

  @Override
  public void beforeExecute(Task task) {
    taskStartTimes.put(task, System.currentTimeMillis());
  }

  @Override
  public void afterExecute(Task task, TaskState taskState) {
    long startTime = taskStartTimes.remove(task);

    if (taskState.getFailure() != null) {
      metrics.measureFailure(task);
      return;
    }

    long elapsedTime = System.currentTimeMillis() - startTime;
    metrics.measureSuccess(task, elapsedTime);
  }
}
