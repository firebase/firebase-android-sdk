// Copyright 2019 Google LLC
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

package com.google.firebase.gradle.plugins;

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.logging.Logger;

/** Gradle transform that copies classes from "exports" dependencies into the final aar. */
class VendorTransform extends Transform {
  private static final String TRANSFORM_NAME = "firebaseVendorTransform";

  private final Logger logger;
  private final Configuration configuration;

  VendorTransform(Logger logger, Configuration configuration) {
    this.logger = logger;
    this.configuration = configuration;
  }

  @Override
  public String getName() {
    return TRANSFORM_NAME;
  }

  @Override
  public Set<QualifiedContent.ContentType> getInputTypes() {
    return Collections.singleton(QualifiedContent.DefaultContentType.CLASSES);
  }

  @Override
  public Set<? super QualifiedContent.Scope> getScopes() {
    return Collections.singleton(QualifiedContent.Scope.PROJECT);
  }

  @Override
  public boolean isIncremental() {
    return true;
  }

  @Override
  public void transform(TransformInvocation transformInvocation) throws IOException {
    TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();

    for (TransformInput transformInput : transformInvocation.getInputs()) {
      for (DirectoryInput input : transformInput.getDirectoryInputs()) {
        moveDirectoriesUnchanged(
            input,
            outputProvider.getContentLocation(
                TRANSFORM_NAME, input.getContentTypes(), input.getScopes(), Format.DIRECTORY));
      }

      for (JarInput input : transformInput.getJarInputs()) {
        unpackAndMoveJar(
            input.getFile(),
            outputProvider.getContentLocation(
                TRANSFORM_NAME, input.getContentTypes(), input.getScopes(), Format.DIRECTORY));
      }

      List<Pattern> directDeps =
          configuration.getDependencies().stream()
              .map(
                  d -> {
                    if (d instanceof ProjectDependency) {
                      return Pattern.compile(
                          String.format(".*build/libs/%s.*\\.jar$", d.getName()));
                    }
                    return Pattern.compile(
                        String.format(".*%s/%s/%s$", d.getGroup(), d.getName(), d.getVersion()));
                  })
              .collect(Collectors.toList());
      for (File jar : configuration.resolve()) {
        if (directDeps.stream().noneMatch(dep -> dep.matcher(jar.getAbsolutePath()).matches())) {
          continue;
        }

        unpackAndMoveJar(
            jar,
            outputProvider.getContentLocation(
                TRANSFORM_NAME,
                Collections.singleton(QualifiedContent.DefaultContentType.CLASSES),
                Collections.singleton(QualifiedContent.Scope.PROJECT),
                Format.DIRECTORY));
      }
    }
  }

  private void moveDirectoriesUnchanged(DirectoryInput directoryInput, File outDir)
      throws IOException {
    logger.debug("Moving directory {} into {}", directoryInput, outDir);
    FileUtils.deleteDirectory(outDir);
    FileUtils.copyDirectory(directoryInput.getFile(), outDir);
  }

  /** Note wee must unpack the JARs due to the limitation of the retrolambda plugin. */
  private void unpackAndMoveJar(File jarInput, File outDir) throws IOException {
    logger.debug("Unpacking jar {} into {}", jarInput, outDir);
    if (outDir.getParentFile().mkdirs() || outDir.getParentFile().isDirectory()) {

      JarInputStream jis =
          new JarInputStream(new BufferedInputStream(new FileInputStream(jarInput)));

      JarEntry inEntry;
      while ((inEntry = jis.getNextJarEntry()) != null) {

        final String name = inEntry.getName();

        if (!inEntry.isDirectory()) {
          File outFile = new File(/* pathname */ outDir, name);
          outFile.getParentFile().mkdirs();
          outFile.createNewFile();

          try (BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(outFile))) {
            IOUtils.copy(jis, fos);
          } finally {
            jis.closeEntry();
          }
        }
      }

    } else {
      throw new IOException("Couldn't create transform output: " + outDir.getParentFile());
    }
  }
}
