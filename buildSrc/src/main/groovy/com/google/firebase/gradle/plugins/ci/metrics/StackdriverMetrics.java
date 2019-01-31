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

import io.opencensus.common.Duration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsConfiguration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.stats.Aggregation;
import io.opencensus.stats.Measure;
import io.opencensus.stats.Stats;
import io.opencensus.stats.View;
import io.opencensus.tags.TagContext;
import io.opencensus.tags.TagKey;
import io.opencensus.tags.TagValue;
import io.opencensus.tags.Tags;
import io.opencensus.tags.propagation.TagContextBinarySerializer;
import io.opencensus.tags.propagation.TagContextDeserializationException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;

/**
 * Object used to record measurements via {@link #measureSuccess(Task, long)} and {@link
 * #measureFailure(Task)}.
 */
class StackdriverMetrics implements Metrics {
  private static final AtomicBoolean STACKDRIVER_INITIALIZED = new AtomicBoolean();
  private static final long STACKDRIVER_UPLOAD_PERIOD_MS = 5000;

  private final TagContext globalContext;
  private final Logger logger;

  private static Measure.MeasureDouble M_LATENCY =
      Measure.MeasureDouble.create("latency", "", "ms");
  private static Measure.MeasureLong M_SUCCESS = Measure.MeasureLong.create("success", "", "1");

  private static final TagKey STAGE = TagKey.create("stage");
  private static final TagKey GRADLE_PROJECT = TagKey.create("gradle_project");

  private static final List<TagKey> TAG_KEYS =
      Arrays.asList(
          STAGE,
          GRADLE_PROJECT,
          TagKey.create("repo_owner"),
          TagKey.create("repo_name"),
          TagKey.create("pull_number"),
          TagKey.create("job_name"),
          TagKey.create("build_id"),
          TagKey.create("job_type"));

  StackdriverMetrics(Gradle gradle, Logger logger) {
    this.logger = logger;
    globalContext = deserializeContext();

    ensureStackdriver(gradle);

    Stats.getViewManager()
        .registerView(
            View.create(
                View.Name.create("fireci/tasklatency"),
                "The latency in milliseconds",
                M_LATENCY,
                Aggregation.LastValue.create(),
                TAG_KEYS));

    Stats.getViewManager()
        .registerView(
            View.create(
                View.Name.create("fireci/tasksuccess"),
                "Indicated success or failure.",
                M_SUCCESS,
                Aggregation.LastValue.create(),
                TAG_KEYS));
  }

  /** Records failure of the execution stage named {@code name}. */
  public void measureFailure(Task task) {

    TagContext ctx =
        Tags.getTagger()
            .toBuilder(globalContext)
            .put(STAGE, TagValue.create(task.getName()))
            .put(GRADLE_PROJECT, TagValue.create(task.getProject().getPath()))
            .build();
    Stats.getStatsRecorder().newMeasureMap().put(M_SUCCESS, 0).record(ctx);
  }

  /** Records success and latency of the execution stage named {@code name}. */
  public void measureSuccess(Task task, long elapsedTime) {

    TagContext ctx =
        Tags.getTagger()
            .toBuilder(globalContext)
            .put(STAGE, TagValue.create(task.getName()))
            .put(GRADLE_PROJECT, TagValue.create(task.getProject().getPath()))
            .build();
    Stats.getStatsRecorder()
        .newMeasureMap()
        .put(M_SUCCESS, 1)
        .put(M_LATENCY, elapsedTime)
        .record(ctx);
  }

  private void ensureStackdriver(Gradle gradle) {
    // make sure we only initialize stackdriver once as gradle daemon is not guaranteed to restart
    // across gradle invocations.
    if (!STACKDRIVER_INITIALIZED.compareAndSet(false, true)) {
      logger.lifecycle("Stackdriver exporter already initialized.");
      return;
    }
    logger.lifecycle("Initializing Stackdriver exporter.");

    try {
      StackdriverStatsExporter.createAndRegister(
          StackdriverStatsConfiguration.builder()
              .setExportInterval(Duration.fromMillis(STACKDRIVER_UPLOAD_PERIOD_MS))
              .build());

      // make sure gradle does not exit before metrics get uploaded to stackdriver.
      gradle.addBuildListener(new DrainingBuildListener(STACKDRIVER_UPLOAD_PERIOD_MS, logger));
    } catch (IOException e) {
      throw new GradleException("Could not configure metrics exporter", e);
    }
  }

  /** Extract opencensus context(if any) from environment. */
  private static TagContext deserializeContext() {
    String serializedContext = System.getenv("OPENCENSUS_STATS_CONTEXT");
    if (serializedContext == null) {
      return Tags.getTagger().empty();
    }

    TagContextBinarySerializer serializer = Tags.getTagPropagationComponent().getBinarySerializer();

    try {
      return serializer.fromByteArray(Base64.getDecoder().decode(serializedContext));
    } catch (TagContextDeserializationException e) {
      return Tags.getTagger().empty();
    }
  }
}
