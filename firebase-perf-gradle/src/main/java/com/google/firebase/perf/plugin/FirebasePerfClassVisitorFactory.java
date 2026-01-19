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

package com.google.firebase.perf.plugin;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.FramesComputationMode;
import com.android.build.api.instrumentation.InstrumentationParameters;
import com.android.build.api.instrumentation.InstrumentationScope;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.ApplicationVariant;
import com.google.firebase.perf.plugin.instrumentation.Instrument;
import com.google.firebase.perf.plugin.instrumentation.InstrumentationConfigFactory;
import com.google.firebase.perf.plugin.instrumentation.InstrumentationVisitor;
import javax.annotation.Nonnull;
import kotlin.Unit;
import org.gradle.api.Project;
import org.objectweb.asm.ClassVisitor;
import org.slf4j.Logger;

/**
 * An {@link AsmClassVisitorFactory} implementation to enable registration of {@link InstrumentationVisitor}
 * instances to visit and modify classes.
 *
 * AGP will call {@link #isInstrumentable} first to determine if the factory is interested in visiting a particular
 * class. If returned true, {@link #createClassVisitor} will be called and the returned {@link ClassVisitor} will
 * visit the class defined by {@link ClassContext}.
 */
public abstract class FirebasePerfClassVisitorFactory
    implements AsmClassVisitorFactory<InstrumentationParameters.None> {

  private static final Logger logger = FirebasePerfPlugin.getLogger();

  @Nonnull
  @Override
  public ClassVisitor createClassVisitor(
      @Nonnull ClassContext classContext, @Nonnull ClassVisitor classVisitor) {
    return new InstrumentationVisitor(
        getInstrumentationContext().getApiVersion().get(),
        classVisitor,
        new InstrumentationConfigFactory().newClassDataBasedInstrumentationConfig(classContext));
  }

  @Override
  public boolean isInstrumentable(@Nonnull ClassData classData) {
    return Instrument.isClassInstrumentable(classData.getClassName());
  }

  /**
   * Registers the {@link FirebasePerfClassVisitorFactory} to the ASM classes transform pipeline for all variants
   * with instrumentation enabled.
   */
  public static void registerForProject(
      Project project,
      InstrumentationFlagState instrumentationFlagState,
      boolean useInstrumentationApi) {
    AndroidComponentsExtension androidComponents =
        project.getExtensions().getByType(AndroidComponentsExtension.class);
    androidComponents.onVariants(
        androidComponents.selector().all(),
        variant -> {
          registerForVariant(
              (ApplicationVariant) variant, instrumentationFlagState, useInstrumentationApi);
        });
  }

  private static void registerForVariant(
      ApplicationVariant appVariant,
      InstrumentationFlagState instrumentationFlagState,
      boolean useInstrumentationApi) {
    boolean instrumentationEnabled = instrumentationFlagState.isEnabledFor(appVariant);
    logger.debug("{} isInstrumentationEnabled: {}", appVariant.getName(), instrumentationEnabled);
    if (instrumentationEnabled) {
      // Use the new instrumentation APIs for AGP version >= 7.2.0
      if (useInstrumentationApi) {
        appVariant
            .getInstrumentation()
            .transformClassesWith(
                FirebasePerfClassVisitorFactory.class,
                InstrumentationScope.ALL,
                params -> Unit.INSTANCE);
        appVariant
            .getInstrumentation()
            .setAsmFramesComputationMode(
                FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES);
      } else {
        // Use the old bytecode instrumentation AGP version < 7.2.0
        // TODO: Remove this after bumping up minimum AGP version to 7.2.0
        appVariant.transformClassesWith(
            FirebasePerfClassVisitorFactory.class,
            InstrumentationScope.ALL,
            params -> Unit.INSTANCE);
        // This mode is equivalent to frames computation in Instrument#instrument
        appVariant.setAsmFramesComputationMode(
            FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES);
      }
    }
  }
}
