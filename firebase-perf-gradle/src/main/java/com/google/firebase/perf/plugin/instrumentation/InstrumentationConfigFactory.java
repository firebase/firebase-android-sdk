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

import com.android.build.api.instrumentation.ClassContext;
import com.google.common.collect.ImmutableList;
import com.google.firebase.perf.plugin.instrumentation.annotation.AnnotatedMethodInstrumentationConfig;
import com.google.firebase.perf.plugin.instrumentation.annotation.FirebaseTimerAnnotationConfig;
import com.google.firebase.perf.plugin.instrumentation.network.NetworkObjectInstrumentationConfig;
import com.google.firebase.perf.plugin.instrumentation.network.config.HttpClientExecuteHostRequestContextIC;
import com.google.firebase.perf.plugin.instrumentation.network.config.HttpClientExecuteHostRequestHandlerContextIC;
import com.google.firebase.perf.plugin.instrumentation.network.config.HttpClientExecuteHostRequestHandlerIC;
import com.google.firebase.perf.plugin.instrumentation.network.config.HttpClientExecuteHostRequestIC;
import com.google.firebase.perf.plugin.instrumentation.network.config.HttpClientExecuteRequestContextIC;
import com.google.firebase.perf.plugin.instrumentation.network.config.HttpClientExecuteRequestHandlerContextIC;
import com.google.firebase.perf.plugin.instrumentation.network.config.HttpClientExecuteRequestHandlerIC;
import com.google.firebase.perf.plugin.instrumentation.network.config.HttpClientExecuteRequestIC;
import com.google.firebase.perf.plugin.instrumentation.network.config.OkHttpClientCallEnqueueIC;
import com.google.firebase.perf.plugin.instrumentation.network.config.OkHttpClientCallExecuteIC;
import com.google.firebase.perf.plugin.instrumentation.network.config.UrlConnectionGetContentClassIC;
import com.google.firebase.perf.plugin.instrumentation.network.config.UrlConnectionGetContentIC;
import com.google.firebase.perf.plugin.instrumentation.network.config.UrlConnectionOpenConnectionIC;
import com.google.firebase.perf.plugin.instrumentation.network.config.UrlConnectionOpenConnectionProxyIC;
import com.google.firebase.perf.plugin.instrumentation.network.config.UrlConnectionOpenStreamIC;
import java.util.List;

/**
 * Assembles all the configurations into a single {@link InstrumentationConfig}. Each configuration
 * class returned by {@link #newClassDataBasedInstrumentationConfig} or
 * {@link #newClassLoaderBasedInstrumentationConfig} represents a class and methods that will get instrumented
 * by manipulating the app's bytecode using ASM.
 */
public class InstrumentationConfigFactory {

  private final List<AnnotatedMethodInstrumentationConfig> annotatedMethodConfigs =
      ImmutableList.of(new FirebaseTimerAnnotationConfig());

  private final List<NetworkObjectInstrumentationConfig> networkObjectConfigs =
      ImmutableList.of(
          new UrlConnectionOpenConnectionIC(),
          new UrlConnectionOpenConnectionProxyIC(),
          new UrlConnectionOpenStreamIC(),
          new UrlConnectionGetContentIC(),
          new UrlConnectionGetContentClassIC(),
          new HttpClientExecuteRequestIC(),
          new HttpClientExecuteRequestContextIC(),
          new HttpClientExecuteRequestHandlerIC(),
          new HttpClientExecuteRequestHandlerContextIC(),
          new HttpClientExecuteHostRequestIC(),
          new HttpClientExecuteHostRequestContextIC(),
          new HttpClientExecuteHostRequestHandlerIC(),
          new HttpClientExecuteHostRequestHandlerContextIC(),
          new OkHttpClientCallExecuteIC(),
          new OkHttpClientCallEnqueueIC());

  public ClassLoaderBasedInstrumentationConfig newClassLoaderBasedInstrumentationConfig(
      ClassLoader classLoader) {
    return new ClassLoaderBasedInstrumentationConfig(
        classLoader, annotatedMethodConfigs, networkObjectConfigs);
  }

  public ClassDataBasedInstrumentationConfig newClassDataBasedInstrumentationConfig(
      ClassContext classContext) {
    return new ClassDataBasedInstrumentationConfig(
        classContext, annotatedMethodConfigs, networkObjectConfigs);
  }
}
