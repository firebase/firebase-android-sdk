/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.perf.plugin.instrumentation;

import com.google.firebase.perf.plugin.instrumentation.annotation.AnnotatedMethodInstrumentationConfig;
import com.google.firebase.perf.plugin.instrumentation.annotation.AnnotatedMethodInstrumentationFactory;
import com.google.firebase.perf.plugin.instrumentation.network.NetworkObjectInstrumentationConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

public abstract class InstrumentationConfigBase implements InstrumentationConfig {

  protected final List<AnnotatedMethodInstrumentationConfig> annotatedMethodConfigs;
  protected final List<NetworkObjectInstrumentationConfig> networkObjectConfigs;

  public InstrumentationConfigBase(
      final List<AnnotatedMethodInstrumentationConfig> annotatedMethodConfigs,
      final List<NetworkObjectInstrumentationConfig> networkObjectConfigs) {
    this.annotatedMethodConfigs = Collections.unmodifiableList(annotatedMethodConfigs);
    this.networkObjectConfigs = Collections.unmodifiableList(networkObjectConfigs);
  }

  @Nullable
  public List<AnnotatedMethodInstrumentationFactory> getAnnotatedMethodInstrumentationFactories(
      final String classDesc) {

    List<AnnotatedMethodInstrumentationFactory> annotatedMethodFactoryList = null;

    for (final AnnotatedMethodInstrumentationConfig annotatedMethodConfig :
        annotatedMethodConfigs) {

      if (annotatedMethodConfig.getClassDesc().equals(classDesc)) {

        if (annotatedMethodFactoryList == null) {
          annotatedMethodFactoryList = new ArrayList<>();
        }

        annotatedMethodFactoryList.add(annotatedMethodConfig.getFactory());
      }
    }

    return annotatedMethodFactoryList;
  }
}
