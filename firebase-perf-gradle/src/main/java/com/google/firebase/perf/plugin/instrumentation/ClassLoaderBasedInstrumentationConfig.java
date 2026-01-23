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
import com.google.firebase.perf.plugin.instrumentation.network.NetworkObjectInstrumentationConfig;
import com.google.firebase.perf.plugin.instrumentation.network.NetworkObjectInstrumentationFactory;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.objectweb.asm.Type;

/**
 * Implementation of {@link InstrumentationConfig} that uses {@link ClassLoader} to load classes
 * hierarchy data.
 */
public class ClassLoaderBasedInstrumentationConfig extends InstrumentationConfigBase {

  private final ClassLoader classLoader;

  // Collection of classes with instrumentations but weren't found in our classloader.
  // For example, anything from compat libs that aren't included or were stripped by proguard.
  private final Set<String> missingClasses = new HashSet<>();

  public ClassLoaderBasedInstrumentationConfig(
      final ClassLoader classLoader,
      final List<AnnotatedMethodInstrumentationConfig> annotatedMethodConfigs,
      final List<NetworkObjectInstrumentationConfig> networkObjectConfigs) {
    super(annotatedMethodConfigs, networkObjectConfigs);
    this.classLoader = classLoader != null ? classLoader : this.getClass().getClassLoader();
  }

  @Override
  @Nullable
  public NetworkObjectInstrumentationFactory getNetworkObjectInstrumentationFactory(
      final String className, final String methodName, final String methodDesc) {

    final Type classType = Type.getObjectType(className);

    // Skip array types
    if (classType.getSort() != Type.OBJECT) {
      return null;
    }

    Class<?> clazz;

    try {
      clazz = Class.forName(classType.getClassName(), /* initialize */ false, classLoader);

    } catch (final LinkageError | Exception e) {
      return null;
    }

    for (final NetworkObjectInstrumentationConfig networkObjectConfig : networkObjectConfigs) {
      final Type networkClassType = Type.getObjectType(networkObjectConfig.getClassName());
      final String networkClassName = networkClassType.getClassName();

      if (missingClasses.contains(networkClassName)) {
        continue;
      }

      try {
        final Class<?> networkClass =
            Class.forName(networkClassName, /* initialize */ false, classLoader);

        final boolean isInst = networkClass.isAssignableFrom(clazz);
        final boolean methodMatch = networkObjectConfig.getMethodName().equals(methodName);
        final boolean descMatch = networkObjectConfig.getMethodDesc().equals(methodDesc);

        if (isInst && methodMatch && descMatch) {
          return networkObjectConfig.getFactory();
        }

      } catch (final LinkageError | Exception e) {
        missingClasses.add(networkClassName);
      }
    }

    return null;
  }
}
