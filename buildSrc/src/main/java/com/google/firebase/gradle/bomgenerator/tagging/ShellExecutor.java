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

  public ShellExecutor(File cwd) {
    this.runtime = Runtime.getRuntime();
    this.cwd = cwd;
  }

  public void execute(String command, Consumer<List<String>> consumer) {
    try {
      Process p = runtime.exec(command, null, cwd);
      p.waitFor();
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
