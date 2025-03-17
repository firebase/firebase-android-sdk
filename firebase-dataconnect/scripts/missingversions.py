#!/usr/bin/env python3

# Copyright 2025 Google LLC
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

# Run this script in the root directory of this Git repository to
# determine the versions of the Data Connect Toolkit that are missing
# from the DataConnectExecutableVersions.json file. The final output
# of this script will be the gradle command to run to update the json
# file.
#
# Make sure to run "pip install packaging" before running this script.

import json
import os
from packaging.version import Version
import re
import subprocess
import tempfile

regex = re.compile(r".*dataconnect-emulator-linux-v(\d+\.\d+\.\d+)")
json_path = os.path.abspath("firebase-dataconnect/gradleplugin/plugin/src/main/resources/com/google/firebase/dataconnect/gradle/plugin/DataConnectExecutableVersions.json")
min_version = Version("1.3.4")
bucket = "gs://firemat-preview-drop/emulator/"

args = ["gsutil", "ls", "-r", bucket]
print("Getting versions by running: " + subprocess.list2cmdline(args))
with tempfile.TemporaryFile() as f:
  subprocess.check_call(args, stdout=f)
  f.seek(0)
  filenames = f.read().decode("utf8", errors="strict").splitlines()

filename_matches = [regex.fullmatch(filename) for filename in filenames]
versions_set = set(match.group(1) for match in filename_matches if match is not None)
all_versions = sorted(versions_set, key=Version)
versions = [version for version in all_versions if Version(version) >= min_version]

try:
  invalid_version_index = versions.index("1.15.0")
except ValueError:
  pass
else:
  versions.pop(invalid_version_index)

print(f"Found {len(versions)} versions greater than {min_version}: {versions!r}")
print()

with open(json_path, "rb") as f:
  known_versions_map = json.load(f)
known_versions_set = frozenset(version_info["version"] for version_info in known_versions_map["versions"])
known_versions = sorted(known_versions_set, key=Version)
print(f"Found {len(known_versions)} versions in {os.path.basename(json_path)}: {known_versions!r}")
print()

missing_versions = [version for version in versions if version not in known_versions]
print(f"Found {len(missing_versions)} missing versions in {os.path.basename(json_path)}: {missing_versions!r}")
print()

print(f"Run this gradle command to update {json_path}:")
print(f"./gradlew :firebase-dataconnect:connectors:updateJson -Pversions={",".join(missing_versions)} -PdefaultVersion={versions[-1]}")
