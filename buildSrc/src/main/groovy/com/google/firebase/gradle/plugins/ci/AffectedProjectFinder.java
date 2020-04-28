package com.google.firebase.gradle.plugins.ci;

import com.google.common.collect.ImmutableSet;

import com.google.common.io.CharStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

/** Determines a set of subprojects that own the 'changedPaths'. */
public class AffectedProjectFinder {

  private final Project project;
  private final Set<String> changedPaths;

  public AffectedProjectFinder(Project project, List<Pattern> ignorePaths) {
    this(project, changedPaths(project.getRootDir()), ignorePaths);
  }

  private AffectedProjectFinder(
      Project project, Set<String> changedPaths, List<Pattern> ignorePaths) {
    this.project = project;
    this.changedPaths =
        changedPaths.stream()
            .filter(
                p -> {
                  for (Pattern path : ignorePaths) {
                    if (path.matcher(p).matches()) {
                      return false;
                    }
                  }
                  return true;
                })
            .collect(Collectors.toSet());
  }

  Set<Project> find() {
    Set<String> paths = new HashSet<>(changedPaths);
    Set<Project> projects = changedSubProjects(project, paths);

    if (!containsRootProject(projects)) {
      return projects;
    }
    return project.getSubprojects();
  }

  private static Set<String> changedPaths(File workDir) {
    try {
      Process process =
          Runtime.getRuntime().exec("git diff --name-only --submodule=diff", null, workDir);
      process.waitFor();
      return ImmutableSet.copyOf(
          CharStreams.readLines(new InputStreamReader(process.getInputStream())));
    } catch (IOException e) {
      throw new GradleException("Could not determine changed files", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  /**
   * Performs a post-order project tree traversal and returns a set of projects that own the
   * 'changedPaths'.
   */
  private static Set<Project> changedSubProjects(Project project, Set<String> changedPaths) {
    // project.subprojects include all descendants of a given project, we only want immediate
    // children.
    Stream<Project> immediateChildProjects =
        project.getSubprojects().stream().filter(p -> project.equals(p.getParent()));

    Set<Project> projects =
        immediateChildProjects
            .flatMap(p -> changedSubProjects(p, changedPaths).stream())
            .collect(Collectors.toSet());
    String relativePath =
        project.getRootDir().toURI().relativize(project.getProjectDir().toURI()).toString();

    Iterator<String> itr = changedPaths.iterator();
    while (itr.hasNext()) {
      String file = itr.next();
      if (file.startsWith(relativePath)) {
        System.out.println("Claiming file " + file + " for project " + project);
        itr.remove();
        projects.add(project);
      }
    }
    return projects;
  }

  private static boolean containsRootProject(Set<Project> projects) {
    return projects.stream().anyMatch(p -> p.getRootProject().equals(p));
  }
}
