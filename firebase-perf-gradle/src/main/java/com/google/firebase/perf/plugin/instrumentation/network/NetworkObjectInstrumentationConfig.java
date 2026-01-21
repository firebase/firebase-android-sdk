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

package com.google.firebase.perf.plugin.instrumentation.network;

/**
 * Creates a factory config object for each network call that is being instrumented which contains
 * the className, methodName, methodDesc and id of the function call.
 */
public abstract class NetworkObjectInstrumentationConfig {

  private final NetworkObjectInstrumentationFactory networkObjectFactory;

  private final String className;
  private final String methodName;
  private final String methodDesc;
  private final String id;

  public NetworkObjectInstrumentationConfig(
      final NetworkObjectInstrumentationFactory networkObjectFactory,
      final String className,
      final String methodName,
      final String methodDesc) {

    super();

    this.networkObjectFactory = networkObjectFactory;
    this.className = className;
    this.methodName = methodName;
    this.methodDesc = methodDesc;

    id = getId(className, methodName, methodDesc);
  }

  public NetworkObjectInstrumentationFactory getFactory() {
    return networkObjectFactory;
  }

  public String getClassName() {
    return className;
  }

  public String getMethodName() {
    return methodName;
  }

  public String getMethodDesc() {
    return methodDesc;
  }

  @Override
  public boolean equals(final Object obj) {
    if (!(obj instanceof NetworkObjectInstrumentationConfig)) {
      return false;
    }

    final NetworkObjectInstrumentationConfig that = (NetworkObjectInstrumentationConfig) obj;
    return that.id.equals(this.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  public static String getId(
      final String className, final String methodName, final String methodDesc) {

    return className + methodName + methodDesc;
  }
}
