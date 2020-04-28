package com.google.firebase.gradle.plugins.ci;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Environment {

  public static String expand(String value) {
    Matcher m = ENV_PATTERN.matcher(value);
    while (m.find()) {
      value = value.replace(m.group(), env(m.group(1)));
    }

    return value;
  }

  private static String env(String varName) {
    return Optional.ofNullable(System.getenv(varName)).orElse("");
  }

  private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\(([A-Za-z0-9_-]+)\\)");
}
