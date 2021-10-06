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
