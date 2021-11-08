// Copyright 2021 Google LLC
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

package com.google.firebase.gradle.bomgenerator.tagging;

import com.google.common.io.CharStreams;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.function.Consumer;
import org.gradle.api.GradleException;

public class ShellExecutor {
  private final Runtime runtime;
  private final File cwd;
  private final Consumer<String> logger;

  public ShellExecutor(File cwd, Consumer<String> logger) {
    this.runtime = Runtime.getRuntime();
    this.cwd = cwd;
    this.logger = logger;
  }

  public void execute(String command, Consumer<List<String>> consumer) {
    try {
      logger.accept("[shell] Executing: \"" + command + "\" at: " + cwd.getAbsolutePath());
      Process p = runtime.exec(command, null, cwd);
      int code = p.waitFor();
      logger.accept("[shell] Command: \"" + command + "\" returned with code: " + code);
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
      consumer.accept(CharStreams.readLines(reader));
    } catch (IOException e) {
      throw new GradleException("Failed when executing command: " + command, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }
}
