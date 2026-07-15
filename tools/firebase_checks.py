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

import os
import sys
import subprocess

def get_git_root():
    res = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True)
    return res.stdout.strip()

def get_modified_files(repo_root):
    res = subprocess.run(
        ["git", "status", "--porcelain", "-z"],
        cwd=repo_root,
        capture_output=True,
        text=True,
        check=True
    )
    files = []
    entries = res.stdout.split("\0")
    i = 0
    while i < len(entries):
        entry = entries[i]
        if not entry:
            i += 1
            continue
        status = entry[:2]
        path = entry[3:]
        if "R" in status or "C" in status:
            i += 1
            if i < len(entries):
                path = entries[i]
        files.append(path)
        i += 1
    return files

def get_subprojects_info(repo_root):
    cfg_path = os.path.join(repo_root, "subprojects.cfg")
    sdk_subprojects = set()
    subproject_types = {}
    all_subprojects = set()
    if not os.path.exists(cfg_path):
        return sdk_subprojects, subproject_types, all_subprojects

    with open(cfg_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if "#" in line:
                proj_part, comment_part = line.split("#", 1)
                proj = proj_part.strip()
                comment = comment_part.strip()
                dir_path = proj.replace(":", os.sep)
                subproject_types[dir_path] = comment
                all_subprojects.add(dir_path)
                if comment == "sdk":
                    sdk_subprojects.add(dir_path)
            else:
                dir_path = line.strip().replace(":", os.sep)
                all_subprojects.add(dir_path)
                subproject_types[dir_path] = "sdk"
                sdk_subprojects.add(dir_path)
    return sdk_subprojects, subproject_types, all_subprojects

def is_test_app(dir_path, subproject_types):
    if os.path.basename(dir_path) == "test-app" or dir_path.endswith("test-app"):
        return True
    if subproject_types.get(dir_path) == "test":
        return True
    return False

def is_build_file(filename):
    if filename.startswith("settings.gradle") or filename.startswith("init.gradle"):
        return False
    return filename.startswith("build.gradle") or filename.endswith(".gradle") or filename.endswith(".gradle.kts")

def contains_build_gradle(dir_path):
    if not os.path.isdir(dir_path):
        return False
    try:
        entries = os.listdir(dir_path)
    except OSError:
        return False
    return any(is_build_file(f) for f in entries)

def find_subproject_dir(file_path, repo_root, all_subprojects):
    abs_repo_root = os.path.abspath(repo_root)
    abs_file_path = os.path.abspath(os.path.join(repo_root, file_path))

    curr_dir = abs_file_path if os.path.isdir(abs_file_path) else os.path.dirname(abs_file_path)

    while True:
        if not curr_dir.startswith(abs_repo_root) or curr_dir == abs_repo_root:
            return None

        rel_dir = os.path.relpath(curr_dir, abs_repo_root)
        if (all_subprojects and rel_dir in all_subprojects) or (not all_subprojects and contains_build_gradle(curr_dir)):
            return rel_dir

        parent = os.path.dirname(curr_dir)
        if parent == curr_dir:
            return None
        curr_dir = parent

def has_resources_parent(file_path):
    parts = os.path.normpath(file_path).split(os.sep)
    parent_parts = parts[:-1] if len(parts) > 1 else []
    return "resources" in parent_parts

def main():
    repo_root = get_git_root()
    modified_files = get_modified_files(repo_root)

    if not modified_files:
        print("No modified files detected.")
        return

    sdk_subprojects, subproject_types, all_subprojects = get_subprojects_info(repo_root)
    target_dirs = []
    seen_dirs = set()

    for rel_file in modified_files:
        if has_resources_parent(rel_file):
            continue
        subproj_dir = find_subproject_dir(rel_file, repo_root, all_subprojects)
        if subproj_dir and subproj_dir not in seen_dirs:
            seen_dirs.add(subproj_dir)
            target_dirs.append(subproj_dir)

    if not target_dirs:
        print("No subproject directories with build Gradle files found for modified files.")
        return

    print("Directories for which tasks will be run:")
    gradle_tasks = []
    for d in target_dirs:
        print(f"  - {d}")
        gradle_project_path = ":" + d.replace(os.sep, ":")
        if not is_test_app(d, subproject_types) and subproject_types.get(d) != "directory":
            gradle_tasks.append(f"{gradle_project_path}:sApp")
        if d in sdk_subprojects:
            gradle_tasks.append(f"{gradle_project_path}:generateApiTxtFile")

    if not gradle_tasks:
        print("No Gradle tasks to execute.")
        return

    print("\nExecuting Gradle tasks...")
    cmd = ["./gradlew"] + gradle_tasks
    res = subprocess.run(cmd, cwd=repo_root)
    if res.returncode != 0:
        sys.exit(res.returncode)

if __name__ == "__main__":
    main()
