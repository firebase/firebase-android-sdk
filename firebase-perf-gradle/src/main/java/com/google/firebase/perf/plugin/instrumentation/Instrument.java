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

import android.support.annotation.VisibleForTesting;
import com.android.SdkConstants;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.firebase.perf.plugin.FirebasePerfPlugin;
import com.google.firebase.perf.plugin.instrumentation.exceptions.AlreadyPerfInstrumentedException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;

/**
 * Sends files to instrumentation factories.
 *
 * @implNote Before the app's code is converted to DEX files, the Performance Monitoring plugin uses
 *     the Transform API (see https://tools.android.com/tech-docs/new-build-system/transform-api)
 *     and the ASM bytecode instrumentation framework (see https://asm.ow2.io/) to visit the app's
 *     compiled class files and to instrument the code (to measure performance).
 *     <p>During the bytecode instrumentation process the Plugin will decorate any usages of
 *     Fireperf SDK APIs (namely @AddTrace annotation, see
 *     https://firebase.google.com/docs/perf-mon/custom-code-traces?platform=android#add-trace-annotation)
 *     and Network calls (made using one of the supported libraries; see
 *     https://firebase.google.com/docs/perf-mon/network-traces?platform=android) with the bytecode
 *     instrumented APIs defined in the Fireperf SDK.
 */
@SuppressWarnings("UnstableApiUsage")
public class Instrument {

  // LINT.IfChange(asm_api_version)
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  protected static final int ASM_API_VERSION = Opcodes.ASM9;

  // LINT.ThenChange(firebase/firebase-android-sdk/firebase-perf-gradle/\
  //   gradle.properties:asm_api_version)

  private static final Logger logger = FirebasePerfPlugin.getLogger();
  private final InstrumentationConfig instrumentationConfig;
  private final ClassLoader classLoader;

  public Instrument(ClassLoader classLoader) {
    instrumentationConfig =
        new InstrumentationConfigFactory().newClassLoaderBasedInstrumentationConfig(classLoader);
    this.classLoader = classLoader;
  }

  /** Bytecode Instrument the {@code inputFile} and output it as {@code outputFile}. */
  public void instrumentClassFile(File inputFile, File outputFile) throws IOException {
    try (FileInputStream fis = new FileInputStream(inputFile);
        FileOutputStream fos = new FileOutputStream(outputFile)) {

      logger.debug(
          "instrumentClassFile() >> inputFile: '{}', outputFile: '{}'", inputFile, outputFile);

      // Note: We don't need to check for "isInstrumentable()" when processing directory inputs as
      // oppose to when processing jar inputs. Imagine either the developer uses the restricted
      // package names or one of those libraries wants to integrate with Firebase Performance. So we
      // would have to process all the files here, no matter what.
      fos.write(instrument(ByteStreams.toByteArray(fis)));

    } catch (final AlreadyPerfInstrumentedException e) {
      logger.error("Already instrumented class - {}", e.getMessage());

    } catch (final Exception e) {
      logger.error("Can't instrument because of {}. Copying as is.", e.getMessage());
      Files.copy(inputFile, outputFile);
    }
  }

  /**
   * Bytecode Instrument all the classes inside the {@code inputJar} and repackage them into {@code
   * outputJar}.
   */
  public void instrumentClassesInJar(File inputJar, File outputJar) throws IOException {
    try (ZipFile inputZip = new ZipFile(inputJar);
        FileInputStream fis = new FileInputStream(inputJar);
        ZipInputStream zis = new ZipInputStream(fis);
        FileOutputStream fos = new FileOutputStream(outputJar);
        ZipOutputStream zos = new ZipOutputStream(fos)) {

      // To avoid https://bugs.openjdk.java.net/browse/JDK-7183373 we extract the resources directly
      // as a zip file.
      for (ZipEntry inEntry = zis.getNextEntry(); inEntry != null; inEntry = zis.getNextEntry()) {
        final String entryName = inEntry.getName();

        if (!inEntry.isDirectory()) {
          try (BufferedInputStream bis =
              new BufferedInputStream(inputZip.getInputStream(inEntry))) {

            byte[] entryBytes = ByteStreams.toByteArray(bis);
            boolean isClassFile = entryName.endsWith(SdkConstants.DOT_CLASS);
            boolean isInstrumentable = isInstrumentable(entryName);

            logger.debug(
                "instrumentClassesInJar() >> entryName: {}, isClassFile: {}, isInstrumentable: {}",
                entryName,
                isClassFile,
                isInstrumentable);

            if (isClassFile && isInstrumentable) {
              try {
                entryBytes = instrument(entryBytes);

              } catch (final AlreadyPerfInstrumentedException e) {
                logger.error("Already instrumented class - {}", e.getMessage());
                continue;

              } catch (final Exception e) {
                logger.error("Can't instrument because of {}. Copying as is.", e.getMessage());
              }
            } else {
              logger.debug("Copying '{}' without instrumenting", entryName);
            }

            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(entryBytes);
            zos.closeEntry();
          }
        }
      }
    }
  }

  /** Checks if the file/directory is not an Android file or part of the SDK. */
  private static boolean isInstrumentable(String name) {
    // Instrument Fireperf E2E test app if gradle property instrumentFireperfE2ETest is set.
    if (FirebasePerfPlugin.getInstrumentFireperfE2ETest()
        && name.startsWith("com/google/firebase/testing/fireperf")) {
      return true;
    }

    // TODO(b/169622737, b/121342941): Filter out packages/classes that don't need to be
    // instrumented.
    // If it's an Android class or part of the Performance SDK, no need for instrumentation
    // TODO(b/452626742): Investigate a change to instrument classes in the E2E test app.
    return !name.equals("android")
        && !name.startsWith("android/")
        && !name.startsWith("firebase/perf/")
        && !name.startsWith("com/google/firebase/")
        && !name.startsWith("com/google/firebase/perf/")
        && !name.startsWith("com/google/protobuf/")
        && !name.startsWith("com/google/android/apps/common/proguard/")
        && !name.startsWith("com/google/android/datatransport/")
        && !name.startsWith("com/google/android/gms/")
        && !name.startsWith("com/google/common")
        // Don't instrument okhttp library classes
        && !name.startsWith("okhttp3/")
        && !name.startsWith("okio/")
        && !name.toLowerCase(Locale.US).startsWith("meta-inf/");
  }

  /** Checks if the class is not an Android class or part of the SDK. */
  public static boolean isClassInstrumentable(String className) {
    return isInstrumentable(className.replace('.', '/'));
  }

  public byte[] instrument(final byte[] in) {
    final ClassReader cr = new ClassReader(in);

    // COMPUTE_MAXS is needed when we instrument enter/exit of a method.
    // XXX DO NOT use the ClassWriter constructor that takes a ClassReader for optimization.
    // It breaks Android's dex verifier on the device with a VerifyError.
    final ClassWriter cw =
        new FirebasePerfClassWriter(
            /* flags */ ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

    cr.accept(
        new InstrumentationVisitor(ASM_API_VERSION, cw, instrumentationConfig),
        new Attribute[] {new PerfInstrumentedAttribute(/* extra */ "")},
        ClassReader.SKIP_FRAMES);

    return cw.toByteArray();
  }

  /**
   * A modified ClassWriter that knows about the configured classpath for looking up classes
   * encountered during instrumentation. The {@link #getCommonSuperClass(String, String)} override
   * is copied directly from the ASM {@link ClassWriter#getCommonSuperClass(String, String)} except
   * it looks at our config for a classloader to query instead of the class's own loader.
   */
  private class FirebasePerfClassWriter extends ClassWriter {

    public FirebasePerfClassWriter(final int flags) {
      super(flags);
    }

    public FirebasePerfClassWriter(final ClassReader classReader, final int flags) {
      super(classReader, flags);
    }

    @Override
    protected String getCommonSuperClass(final String type1, final String type2) {
      Class<?> class1;
      Class<?> class2;

      try {
        class1 =
            Class.forName(
                type1.replace(/* oldChar */ '/', /* newChar */ '.'),
                /* initialize */ false,
                classLoader);

        class2 =
            Class.forName(
                type2.replace(/* oldChar */ '/', /* newChar */ '.'),
                /* initialize */ false,
                classLoader);

      } catch (final Throwable e) {
        logger.warn(e.toString());

        return "java/lang/Object";
      }

      if (class1.isAssignableFrom(class2)) {
        return type1;
      }

      if (class2.isAssignableFrom(class1)) {
        return type2;
      }

      if (class1.isInterface() || class2.isInterface()) {
        return "java/lang/Object";

      } else {
        do {
          class1 = class1.getSuperclass();

        } while (!class1.isAssignableFrom(class2));

        return class1.getName().replace(/* oldChar */ '.', /* newChar */ '/');
      }
    }
  }
}
