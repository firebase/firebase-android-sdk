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
import com.android.build.api.instrumentation.ClassData;
import com.google.firebase.perf.plugin.instrumentation.annotation.AnnotatedMethodInstrumentationConfig;
import com.google.firebase.perf.plugin.instrumentation.network.NetworkObjectInstrumentationConfig;
import com.google.firebase.perf.plugin.instrumentation.network.NetworkObjectInstrumentationFactory;
import java.util.List;
import javax.annotation.Nullable;
import org.objectweb.asm.Type;

/**
 * Implementation of {@link InstrumentationConfig} that uses {@link ClassContext} to load classes
 * hierarchy data.
 */
public class ClassDataBasedInstrumentationConfig extends InstrumentationConfigBase {
  private final ClassContext classContext;

  public ClassDataBasedInstrumentationConfig(
      ClassContext classContext,
      List<AnnotatedMethodInstrumentationConfig> annotatedMethodConfigs,
      List<NetworkObjectInstrumentationConfig> networkObjectConfigs) {
    super(annotatedMethodConfigs, networkObjectConfigs);
    this.classContext = classContext;
  }

  @Nullable
  @Override
  public NetworkObjectInstrumentationFactory getNetworkObjectInstrumentationFactory(
      String className, String methodName, String methodDesc) {
    final Type classType = Type.getObjectType(className);

    // Skip array types
    if (classType.getSort() != Type.OBJECT) {
      return null;
    }

    ClassData classData =
        classContext.loadClassData(
            // convert the class internal name to the class name, see Type#getInternalName
            className.replace('/', '.'));

    if (classData == null) {
      return null;
    }

    for (final NetworkObjectInstrumentationConfig networkObjectConfig : networkObjectConfigs) {
      final Type networkClassType = Type.getObjectType(networkObjectConfig.getClassName());
      final String networkClassName = networkClassType.getClassName();

      final boolean isInstance =
          classData.getClassName().equals(networkClassName)
              || classData.getInterfaces().contains(networkClassName)
              || classData.getSuperClasses().contains(networkClassName);
      final boolean methodMatch = networkObjectConfig.getMethodName().equals(methodName);
      final boolean descMatch = networkObjectConfig.getMethodDesc().equals(methodDesc);

      if (isInstance && methodMatch && descMatch) {
        return networkObjectConfig.getFactory();
      }
    }

    return null;
  }
}
