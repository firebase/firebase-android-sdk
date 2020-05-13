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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.logging.LogLevel;

/** Instruments Gradle to measure latency and success rate of all executed tasks. */
public class MetricsPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {

    Metrics metrics = Metrics.filtered(initializeMetrics(project), MetricsPlugin::isTaskPublic);

    TaskExecutionGraph taskGraph = project.getGradle().getTaskGraph();
    taskGraph.addTaskExecutionListener(new MeasuringTaskExecutionListener(metrics, taskGraph));
  }

  private static Metrics initializeMetrics(Project project) {
    if (!isCollectionEnabled()) {
      project.getLogger().lifecycle("Metrics collection is disabled. Logging to stdout...");
      return Metrics.toLog(project.getLogger(), LogLevel.LIFECYCLE);
    }
    project.getLogger().lifecycle("Metrics collection is enabled.");
    return new StackdriverMetrics(project.getGradle(), project.getLogger());
  }

  private static boolean isCollectionEnabled() {
    String enabled = System.getenv("FIREBASE_ENABLE_METRICS");
    return enabled != null && enabled.equals("1");
  }

  /**
   * Determines whether a given gradle {@link Task} is "public", e.g. visible with `gradle tasks`.
   */
  private static boolean isTaskPublic(Task task) {
    return task.getGroup() != null;
  }
}
