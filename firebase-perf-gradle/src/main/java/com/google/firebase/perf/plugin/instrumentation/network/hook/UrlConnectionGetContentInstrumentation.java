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

package com.google.firebase.perf.plugin.instrumentation.network.hook;

import com.google.firebase.perf.plugin.instrumentation.network.NetworkObjectInstrumentation;
import com.google.firebase.perf.plugin.instrumentation.network.NetworkObjectInstrumentationFactory;
import com.google.firebase.perf.plugin.util.AsmString;

/**
 * Instrumentation for Oracle Java function "URLConnection#getContent()" which will be replaced by
 * Firebase Perf SDK function "FirebasePerfUrlConnection#getContent(URL)".
 */
public class UrlConnectionGetContentInstrumentation extends BaseInstrumentation {

  public UrlConnectionGetContentInstrumentation(
      final String owner, final String name, final String desc) {
    super(owner, name, desc);
  }

  public static class Factory implements NetworkObjectInstrumentationFactory {

    @Override
    public NetworkObjectInstrumentation newObjectInstrumentation(
        final String className, final String methodName, final String methodDesc) {

      return new UrlConnectionGetContentInstrumentation(
          AsmString.CLASS_FIREBASE_PERF_URL_CONNECTION,
          AsmString.METHOD_FIREBASE_PERF_URL_CONNECTION_GET_CONTENT_WITH_URL,
          AsmString.METHOD_DESC_FIREBASE_PERF_URL_CONNECTION_GET_CONTENT_WITH_URL);
    }
  }
}
