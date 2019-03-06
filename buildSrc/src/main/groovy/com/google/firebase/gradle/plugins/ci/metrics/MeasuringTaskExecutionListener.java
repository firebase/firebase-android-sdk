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

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.tasks.TaskState;

class MeasuringTaskExecutionListener implements TaskExecutionListener {
  private static final String METRICS_START_TIME = "metricsStartTime";
  private static final String METRICS_ELAPSED_TIME = "metricsElapsedTime";

  private final Metrics metrics;
  private final TaskExecutionGraph taskGraph;

  MeasuringTaskExecutionListener(Metrics metrics, TaskExecutionGraph taskGraph) {
    this.metrics = metrics;
    this.taskGraph = taskGraph;
  }

  @Override
  public void beforeExecute(Task task) {
    recordStart(task);
  }

  @Override
  public void afterExecute(Task task, TaskState taskState) {
    recordElapsed(task);
    long elapsedTime = getTotalElapsed(task);

    if (taskState.getFailure() != null) {
      metrics.measureFailure(task);
      return;
    }
    metrics.measureSuccess(task, elapsedTime);
  }

  private static void recordStart(Task task) {
    task.getExtensions().add(METRICS_START_TIME, System.currentTimeMillis());
  }

  private static void recordElapsed(Task task) {
    long startTime = (long) task.getExtensions().getByName(METRICS_START_TIME);
    task.getExtensions().add(METRICS_ELAPSED_TIME, System.currentTimeMillis() - startTime);
  }

  private static long getElapsed(Task task) {
    return (long) task.getExtensions().getByName(METRICS_ELAPSED_TIME);
  }

  // a tasks elapsed time does not include how long it took for its dependencies took to execute,
  // so we walk the dependency graph to get the total elapsed time.
  private long getTotalElapsed(Task task) {
    Queue<Task> queue = new ArrayDeque<>();
    queue.add(task);
    Set<Task> visited = new HashSet<>();

    long totalElapsed = 0;
    while (!queue.isEmpty()) {
      Task currentTask = queue.remove();
      if (!visited.add(currentTask)) {
        continue;
      }

      totalElapsed += getElapsed(currentTask);

      for (Task dep : taskGraph.getDependencies(currentTask)) {
        if (!visited.contains(dep)) {
          queue.add(dep);
        }
      }
    }
    return totalElapsed;
  }
}
