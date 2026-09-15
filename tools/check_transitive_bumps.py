#!/usr/bin/env python3
#
# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Script to audit Gradle dependency version bumps caused by transitive dependencies.

For a given subproject (or all subprojects), this script runs Gradle to inspect
all actual dependencies used across resolvable classpaths. For any dependency
bumped to a higher version by a transitive dependency, it verifies whether that
bump corresponds to a dependency explicitly declared in the subproject.

When an explicitly declared dependency is bumped by a transitive dependency,
it prints:
  - The name of the dependency (group:artifact)
  - The declared dependency version and configuration
  - The actual resolved dependency version
  - Which transitive dependency is pulling it to the higher version (with dependency path)
"""

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile

GRADLE_INIT_SCRIPT_TEMPLATE = """
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector

allprojects {
    tasks.register("inspectDependencyBumpsInternal") {
        doLast {
            val projectPath = project.path

            // 1. Gather all explicitly declared dependencies per configuration
            val declaredDeps = mutableMapOf<String, MutableList<Map<String, String>>>()
            project.configurations.forEach { conf ->
                conf.dependencies.withType(ExternalModuleDependency::class.java).forEach { dep ->
                    val g = dep.group ?: ""
                    val n = dep.name
                    val v = dep.version
                        ?: dep.versionConstraint.requiredVersion.takeIf { it.isNotEmpty() }
                        ?: dep.versionConstraint.strictVersion.takeIf { it.isNotEmpty() }
                        ?: dep.versionConstraint.preferredVersion.takeIf { it.isNotEmpty() }
                        ?: ""
                    if (g.isNotEmpty() && n.isNotEmpty() && v.isNotEmpty()) {
                        val key = "$g:$n"
                        declaredDeps.getOrPut(key) { mutableListOf() }.add(mapOf(
                            "config" to conf.name,
                            "version" to v
                        ))
                    }
                }
            }

            // 2. Filter target resolvable configurations
            val targetConfigs = project.configurations.filter {
                it.isCanBeResolved && it.name.endsWith("Classpath", ignoreCase = true)
            }

            // Pre-resolve all classpaths to allow cross-referencing alignment constraints
            val resolvedCache = mutableMapOf<String, Map<String, ResolvedComponentResult>>()
            val rootMap = mutableMapOf<String, ResolvedComponentResult>()

            for (resolvable in targetConfigs) {
                try {
                    val result = resolvable.incoming.resolutionResult
                    rootMap[resolvable.name] = result.root
                    val map = mutableMapOf<String, ResolvedComponentResult>()
                    result.allComponents.forEach { comp ->
                        val mv = comp.moduleVersion
                        if (mv != null) {
                            map["${mv.group}:${mv.name}"] = comp
                        }
                    }
                    resolvedCache[resolvable.name] = map
                } catch (e: Exception) {
                    // Ignore unresolvable configurations
                }
            }

            // Helper to get dependency path from root using BFS
            fun getPathFromRoot(root: ResolvedComponentResult, target: ResolvedComponentResult): List<String> {
                val queue = ArrayDeque<List<ResolvedComponentResult>>()
                queue.add(listOf(root))
                val visited = mutableSetOf<String>()
                visited.add(root.id.displayName)

                while (queue.isNotEmpty()) {
                    val path = queue.removeFirst()
                    val curr = path.last()
                    if (curr == target) {
                        return path.map { it.id.displayName }
                    }
                    curr.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { dep ->
                        val child = dep.selected
                        if (visited.add(child.id.displayName)) {
                            queue.add(path + child)
                        }
                    }
                }
                return listOf(target.id.displayName)
            }

            val configResults = mutableListOf<Map<String, Any>>()

            for (resolvable in targetConfigs) {
                val confName = resolvable.name
                val resolvedComponents = resolvedCache[confName] ?: continue
                val root = rootMap[confName] ?: continue

                val hierarchyNames = resolvable.hierarchy.map { it.name }.toSet()
                val isUnitTest = confName.contains("UnitTest", ignoreCase = true) || confName.startsWith("test", ignoreCase = true)
                val isAndroidTest = confName.contains("AndroidTest", ignoreCase = true)
                val isRuntime = confName.endsWith("RuntimeClasspath", ignoreCase = true)

                // Find matching runtime classpath for compile classpaths to trace AGP alignment constraints
                val runtimeConfName = when {
                    confName.endsWith("CompileClasspath") -> confName.replace("CompileClasspath", "RuntimeClasspath")
                    confName == "compileClasspath" -> "runtimeClasspath"
                    confName == "testCompileClasspath" -> "testRuntimeClasspath"
                    else -> null
                }
                val runtimeResolved = if (runtimeConfName != null) resolvedCache[runtimeConfName] else null
                val runtimeRoot = if (runtimeConfName != null) rootMap[runtimeConfName] else null

                val bumps = mutableListOf<Map<String, Any>>()

                for ((key, comp) in resolvedComponents) {
                    val actualVersion = comp.moduleVersion?.version ?: continue
                    val group = comp.moduleVersion?.group ?: continue
                    val module = comp.moduleVersion?.name ?: continue

                    // Find declarations applicable to this configuration
                    val allDeclaredList = declaredDeps[key] ?: emptyList()
                    val applicableDeclared = allDeclaredList.filter { decl ->
                        val declConf = decl["config"] ?: ""
                        val inHierarchy = declConf in hierarchyNames
                        val isMainDecl = declConf in setOf("api", "implementation", "compileOnly", "runtimeOnly", "releaseApi", "releaseImplementation", "debugApi", "debugImplementation")

                        // compileOnly does not apply to RuntimeClasspath
                        if (declConf.endsWith("compileOnly", ignoreCase = true) && isRuntime) {
                            return@filter false
                        }

                        inHierarchy || (isMainDecl && (isUnitTest || isAndroidTest))
                    }

                    val isExplicitlyDeclared = applicableDeclared.isNotEmpty()

                    // Check all requesters in this configuration
                    val requesters = mutableListOf<Map<String, Any>>()

                    comp.dependents.forEach { dep ->
                        val fromComp = dep.from
                        val requested = dep.requested
                        val reqVer = if (requested is ModuleComponentSelector) requested.version else ""
                        val isConstraint = dep.isConstraint
                        val isFromRoot = (fromComp == root)

                        val isWinning = (reqVer == actualVersion) || (isConstraint && reqVer.isEmpty())
                        val path = if (!isFromRoot) getPathFromRoot(root, fromComp) else listOf(fromComp.id.displayName)

                        requesters.add(mapOf(
                            "from" to fromComp.id.displayName,
                            "requestedVersion" to reqVer,
                            "isConstraint" to isConstraint,
                            "isFromRoot" to isFromRoot,
                            "isWinning" to isWinning,
                            "path" to path
                        ))
                    }

                    // Identify winning transitive requesters
                    var winningTransitive = requesters.filter {
                        (it["isWinning"] == true) && (it["isFromRoot"] == false)
                    }.toMutableList()

                    // If compile classpath only shows root alignment constraint, look up runtime classpath
                    if (winningTransitive.isEmpty() && runtimeResolved != null && runtimeRoot != null) {
                        val runtimeComp = runtimeResolved[key]
                        if (runtimeComp != null && runtimeComp.moduleVersion?.version == actualVersion) {
                            runtimeComp.dependents.forEach { dep ->
                                val fromComp = dep.from
                                val requested = dep.requested
                                val reqVer = if (requested is ModuleComponentSelector) requested.version else ""
                                val isConstraint = dep.isConstraint
                                val isFromRoot = (fromComp == runtimeRoot)
                                if (!isFromRoot && (reqVer == actualVersion || isConstraint)) {
                                    val rPath = getPathFromRoot(runtimeRoot, fromComp)
                                    winningTransitive.add(mapOf(
                                        "from" to "${fromComp.id.displayName} (via ${runtimeConfName} alignment)",
                                        "requestedVersion" to reqVer,
                                        "isConstraint" to isConstraint,
                                        "isFromRoot" to false,
                                        "isWinning" to true,
                                        "path" to rPath
                                    ))
                                }
                            }
                        }
                    }

                    bumps.add(mapOf(
                        "name" to key,
                        "group" to group,
                        "module" to module,
                        "actualVersion" to actualVersion,
                        "isExplicitlyDeclared" to isExplicitlyDeclared,
                        "declaredVersions" to applicableDeclared,
                        "allDeclaredVersions" to allDeclaredList,
                        "selectionReason" to comp.selectionReason.toString(),
                        "winningTransitiveRequesters" to winningTransitive,
                        "allRequesters" to requesters
                    ))
                }

                if (bumps.isNotEmpty()) {
                    configResults.add(mapOf(
                        "configuration" to confName,
                        "bumps" to bumps
                    ))
                }
            }

            val payload = mapOf(
                "project" to projectPath,
                "configurations" to configResults
            )

            val outPath = if (project.hasProperty("depBumpsOutputPath")) {
                project.property("depBumpsOutputPath").toString()
            } else {
                null
            }

            val jsonText = groovy.json.JsonBuilder(payload).toPrettyString()
            if (outPath != null && outPath.isNotEmpty()) {
                java.io.File(outPath).writeText(jsonText)
            } else {
                println("---JSON_START---")
                println(jsonText)
                println("---JSON_END---")
            }
        }
    }
}
"""


def parse_version(v):
  """Splits version string into numeric and string components for comparison."""
  parts = []
  for token in re.split(r"[\.-]", v):
    if token.isdigit():
      parts.append((0, int(token), ""))
    else:
      # Non-numeric qualifiers like alpha, beta, rc, android, etc.
      parts.append((1, 0, token.lower()))
  return parts


def is_higher_version(v_actual, v_declared):
  """Returns True if v_actual is strictly greater than v_declared."""
  if not v_actual or not v_declared:
    return False
  if v_actual == v_declared:
    return False

  p_act = parse_version(v_actual)
  p_dec = parse_version(v_declared)

  for a, d in zip(p_act, p_dec):
    if a != d:
      if a[0] == 0 and d[0] == 0:
        return a[1] > d[1]
      return a > d

  return len(p_act) > len(p_dec)


def get_git_root():
  """Returns the root directory of the git repository."""
  res = subprocess.run(
      ["git", "rev-parse", "--show-toplevel"],
      capture_output=True,
      text=True,
      check=True,
  )
  return res.stdout.strip()


def normalize_project_path(project_arg):
  """Normalizes user-supplied subproject names into a Gradle project path."""
  p = project_arg.strip().replace(os.sep, ":").replace("/", ":")
  if not p.startswith(":"):
    p = ":" + p
  return p


def get_all_subprojects(repo_root):
  """Reads subprojects.cfg and returns a list of active Gradle project paths."""
  cfg_path = os.path.join(repo_root, "subprojects.cfg")
  subprojects = []
  if not os.path.exists(cfg_path):
    return subprojects

  with open(cfg_path, "r", encoding="utf-8") as f:
    for line in f:
      line = line.strip()
      if not line or line.startswith("#"):
        continue
      proj_part = line.split("#")[0].strip()
      if proj_part:
        comment = line.split("#")[1].strip() if "#" in line else ""
        if comment == "directory":
          continue
        subprojects.append(normalize_project_path(proj_part))
  return subprojects


def run_gradle_inspection(repo_root, project_path, output_json_path):
  """Runs the Gradle inspection task for the specified project."""
  gradlew = os.path.join(
      repo_root, "gradlew" if os.name != "nt" else "gradlew.bat"
  )

  with tempfile.NamedTemporaryFile(
      mode="w", suffix=".init.gradle.kts", delete=False, encoding="utf-8"
  ) as init_file:
    init_file.write(GRADLE_INIT_SCRIPT_TEMPLATE)
    init_file_path = init_file.name

  try:
    cmd = [
        gradlew,
        f"{project_path}:inspectDependencyBumpsInternal",
        "-I",
        init_file_path,
        f"-PdepBumpsOutputPath={output_json_path}",
        "-q",
        "--no-configuration-cache",
    ]
    res = subprocess.run(cmd, cwd=repo_root, capture_output=True, text=True)
    if res.returncode != 0:
      raise RuntimeError(
          f"Gradle execution failed for {project_path} (exit code"
          f" {res.returncode}):\n{res.stderr.strip() or res.stdout.strip()}"
      )
  finally:
    if os.path.exists(init_file_path):
      os.remove(init_file_path)


def filter_relevant_configurations(
    configurations, requested_config=None, all_configs=False
):
  """Filters configurations to user-relevant classpaths."""
  excluded_prefixes = (
      "kotlinCompiler",
      "kotlinBuildTools",
      "kotlinKlib",
      "lint",
      "detekt",
      "ktlint",
      "javadoc",
  )

  results = []
  for conf in configurations:
    cname = conf.get("configuration", "")
    if requested_config:
      if cname == requested_config:
        results.append(conf)
    elif all_configs:
      results.append(conf)
    else:
      if any(cname.startswith(p) for p in excluded_prefixes):
        continue
      results.append(conf)
  return results


def check_project_bumps(
    repo_root,
    project_path,
    requested_config=None,
    all_configs=False,
    show_all=False,
    verbose=False,
):
  """Checks dependency bumps for a project and returns structured results."""
  with tempfile.NamedTemporaryFile(
      mode="w", suffix=".json", delete=False, encoding="utf-8"
  ) as tmp_json:
    output_json_path = tmp_json.name

  try:
    run_gradle_inspection(repo_root, project_path, output_json_path)
    if not os.path.exists(output_json_path) or os.path.getsize(
        output_json_path
    ) == 0:
      return {"project": project_path, "configurations": []}

    with open(output_json_path, "r", encoding="utf-8") as f:
      raw_data = json.load(f)
  finally:
    if os.path.exists(output_json_path):
      os.remove(output_json_path)

  configurations = filter_relevant_configurations(
      raw_data.get("configurations", []),
      requested_config=requested_config,
      all_configs=all_configs,
  )

  processed_configs = []
  for conf in configurations:
    cname = conf.get("configuration", "")
    bumps = conf.get("bumps", [])

    filtered_bumps = []
    for b in bumps:
      is_declared = b.get("isExplicitlyDeclared", False)
      actual_ver = b.get("actualVersion", "")
      declared_list = b.get("declaredVersions", [])
      winning_transitive = b.get("winningTransitiveRequesters", [])

      if is_declared:
        # Check if actual version is higher than any declared version
        bumped_declarations = [
            d for d in declared_list if is_higher_version(actual_ver, d.get("version", ""))
        ]
        if not bumped_declarations:
          continue

        b["bumpedDeclaredVersions"] = bumped_declarations
        filtered_bumps.append(b)
      elif show_all:
        # Check if undeclared dependency had lower version requests bumped by transitive
        all_requesters = b.get("allRequesters", [])
        has_lower = any(
            r.get("requestedVersion")
            and r.get("requestedVersion") != actual_ver
            for r in all_requesters
        )
        if has_lower and winning_transitive:
          filtered_bumps.append(b)

    if filtered_bumps:
      processed_configs.append(
          {"configuration": cname, "bumps": filtered_bumps}
      )

  return {"project": project_path, "configurations": processed_configs}


def print_text_report(project_results, verbose=False):
  """Prints formatted human-readable report for project results."""
  project_path = project_results["project"]
  configs = project_results["configurations"]

  print("=" * 80)
  print(f"Subproject: {project_path}")
  print("=" * 80)

  if not configs:
    print(
        "No explicitly declared dependencies were bumped by transitive"
        " dependencies.\n"
    )
    return

  for conf in configs:
    cname = conf["configuration"]
    print(f"\nConfiguration: {cname}")
    print("-" * 80)

    for b in conf["bumps"]:
      dep_name = b["name"]
      actual_ver = b["actualVersion"]
      is_declared = b["isExplicitlyDeclared"]
      declared_list = b.get(
          "bumpedDeclaredVersions", b.get("declaredVersions", [])
      )

      print(f"Dependency: {dep_name}")

      if is_declared:
        declared_str = ", ".join(
            f"{d['version']} (in {d['config']})" for d in declared_list
        )
        print(f"  Declared:  {declared_str}")
      else:
        print("  Declared:  (none - transitive dependency)")

      print(f"  Actual:    {actual_ver}")

      winning_requesters = b.get("winningTransitiveRequesters", [])
      if winning_requesters:
        requester_names = []
        for r in winning_requesters:
          name = r.get("from", "")
          req_v = r.get("requestedVersion", "")
          if req_v and req_v not in name:
            requester_names.append(f"{name} (requested {req_v})")
          else:
            requester_names.append(name)
        print(f"  Pulled by: {', '.join(requester_names)}")

        if verbose:
          for r in winning_requesters:
            path = r.get("path", [])
            if len(path) > 1:
              print(f"  Path:      {' -> '.join(path)} -> {dep_name}:{actual_ver}")
      else:
        print("  Pulled by: (dependency constraint / alignment)")

      print()


def main():
  parser = argparse.ArgumentParser(
      description=(
          "Audit Gradle dependency version bumps caused by transitive"
          " dependencies."
      )
  )
  parser.add_argument(
      "subproject",
      nargs="?",
      default=None,
      help=(
          "Name or path of the subproject to inspect (e.g. firebase-config,"
          " :firebase-config, appcheck/firebase-appcheck)"
      ),
  )
  parser.add_argument(
      "--all",
      action="store_true",
      dest="check_all",
      help="Inspect all subprojects listed in subprojects.cfg",
  )
  parser.add_argument(
      "-c",
      "--configuration",
      default=None,
      help=(
          "Specific resolvable configuration to inspect (e.g."
          " releaseRuntimeClasspath)"
      ),
  )
  parser.add_argument(
      "--all-configs",
      action="store_true",
      help="Inspect all resolvable configurations, including compiler/tool classpaths",
  )
  parser.add_argument(
      "--show-all",
      action="store_true",
      help=(
          "Include dependencies bumped transitively that are NOT explicitly"
          " declared"
      ),
  )
  parser.add_argument(
      "-v",
      "--verbose",
      action="store_true",
      help="Display full dependency chain paths from root project",
  )
  parser.add_argument(
      "--json",
      action="store_true",
      dest="output_json",
      help="Output raw results in JSON format",
  )

  args = parser.parse_args()

  repo_root = get_git_root()

  if not args.subproject and not args.check_all:
    parser.error(
        "Please provide a subproject name (e.g. :firebase-config) or use --all."
    )

  if args.check_all:
    subprojects = get_all_subprojects(repo_root)
    if not subprojects:
      print("No subprojects found in subprojects.cfg", file=sys.stderr)
      sys.exit(1)
  else:
    subprojects = [normalize_project_path(args.subproject)]

  all_results = []
  for proj in subprojects:
    try:
      results = check_project_bumps(
          repo_root=repo_root,
          project_path=proj,
          requested_config=args.configuration,
          all_configs=args.all_configs,
          show_all=args.show_all,
          verbose=args.verbose,
      )
      all_results.append(results)
      if not args.output_json:
        print_text_report(results, verbose=args.verbose)
    except Exception as e:
      print(f"Error inspecting {proj}: {e}", file=sys.stderr)
      if not args.check_all:
        sys.exit(1)

  if args.output_json:
    print(json.dumps(all_results, indent=2))


if __name__ == "__main__":
  main()
