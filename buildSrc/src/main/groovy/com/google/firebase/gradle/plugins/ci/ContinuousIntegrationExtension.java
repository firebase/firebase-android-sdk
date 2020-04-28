package com.google.firebase.gradle.plugins.ci;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Contains plugin configuration properties. */
public class ContinuousIntegrationExtension {

  /** List of paths that the plugin should ignore when querying the Git commit. */
  private List<Pattern> ignorePaths = new ArrayList<>();

  public List<Pattern> getIgnorePaths() {
    return ignorePaths;
  }

  public void setIgnorePaths(List<Pattern> ignorePaths) {
    this.ignorePaths = ignorePaths;
  }
}
