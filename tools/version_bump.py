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

"""Bumps the version declared in gradle.properties for Firebase SDKs.

Only operates on SDKs declared in subprojects.cfg. Supports bumping a single
SDK, a comma-separated list of SDKs, or all SDKs via 'all' / '--all'.

Example usage:
  python3 tools/version_bump.py firebase-common minor
  python3 tools/version_bump.py firebase-common,firebase-firestore patch
  python3 tools/version_bump.py firebase-common patch --ignore-existing
  python3 tools/version_bump.py all patch --ignore-existing
  python3 tools/version_bump.py --all minor --ignore-existing
  python3 tools/version_bump.py all minor --dry-run --ignore-existing

All bumps are computed and validated before any file is written, so a failure
in one SDK leaves every gradle.properties file untouched.
"""

import argparse
import dataclasses
import os
import re
import subprocess
import sys


VALID_BUMP_TYPES = ("patch", "minor", "major")
# Keep version and latestReleasedVersion in sync unless explicitly releasing
# protolite-well-known-types (see b/285892320 and CheckHeadDependencies.kt).
EXCLUDED_FROM_ALL_SDKS = frozenset({"protolite-well-known-types"})
SEMVER_RE = re.compile(
    r"^(?P<major>\d+)\.(?P<minor>\d+)\.(?P<patch>\d+)"
    r"(?:-(?P<suffix_prefix>[A-Za-z](?:[A-Za-z0-9._-]*[A-Za-z._-])?)(?P<suffix_num>\d+)?)?$"
)
VERSION_LINE_RE = re.compile(
    r"^([ \t]*version[ \t]*=[ \t]*)([^\s#]+)([^\n]*)$", re.MULTILINE
)
LATEST_RELEASED_LINE_RE = re.compile(
    r"^([ \t]*latestReleasedVersion[ \t]*=[ \t]*)([^\s#]+)([^\n]*)$",
    re.MULTILINE,
)


class VersionBumpRuleError(ValueError):
  """Raised when a version bump violates the +1 semantic versioning rule."""


def get_git_root() -> str:
  """Returns the root directory of the git repository."""
  script_dir = os.path.dirname(os.path.abspath(__file__))
  try:
    res = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        capture_output=True,
        text=True,
        check=True,
        cwd=script_dir,
    )
    return res.stdout.strip()
  except (subprocess.CalledProcessError, OSError):
    return os.path.dirname(script_dir)


def get_sdk_subprojects(repo_root: str) -> dict[str, str]:
  """Reads subprojects.cfg and returns a mapping of Gradle path to relative dir path for SDKs."""
  cfg_path = os.path.join(repo_root, "subprojects.cfg")
  if not os.path.exists(cfg_path):
    raise FileNotFoundError(f"subprojects.cfg not found at {cfg_path}")

  sdks: dict[str, str] = {}
  with open(cfg_path, "r", encoding="utf-8") as f:
    for raw_line in f:
      line = raw_line.strip()
      if not line or line.startswith("#"):
        continue

      if "#" in line:
        proj_part, comment_part = line.split("#", 1)
        proj = proj_part.strip().strip(":")
        proj_type = comment_part.strip()
      else:
        proj = line.strip().strip(":")
        proj_type = "sdk"

      if proj and proj_type == "sdk":
        rel_dir = proj.replace(":", os.sep)
        sdks[proj] = rel_dir

  return sdks


def resolve_sdk_dir(sdk_arg: str, sdks: dict[str, str]) -> tuple[str, str]:
  """Resolves a user-provided SDK name or path to (gradle_path, rel_dir)."""
  cleaned = sdk_arg.strip().replace(os.sep, "/").strip("/")
  while cleaned.startswith("./"):
    cleaned = cleaned[2:].lstrip("/")
  normalized_colon = cleaned.replace("/", ":").strip(":")

  if normalized_colon in sdks:
    return normalized_colon, sdks[normalized_colon]

  matches = [
      (proj, rel_dir)
      for proj, rel_dir in sdks.items()
      if proj.split(":")[-1] == normalized_colon
  ]
  if len(matches) == 1:
    return matches[0]
  if len(matches) > 1:
    options = ", ".join(proj for proj, _ in matches)
    raise ValueError(
        f"Ambiguous SDK name '{sdk_arg}'. Matches multiple SDKs: {options}"
    )

  raise ValueError(
      f"'{sdk_arg}' is not a valid SDK declared in subprojects.cfg."
  )


def bump_version_string(version: str, bump_type: str) -> str:
  """Computes the next semantic version string."""
  if bump_type not in VALID_BUMP_TYPES:
    raise ValueError(
        f"Invalid bump type '{bump_type}'. Expected one of: {', '.join(VALID_BUMP_TYPES)}"
    )

  match = SEMVER_RE.match(version.strip())
  if not match:
    raise ValueError(f"Version '{version}' does not follow semantic versioning.")

  major = int(match.group("major"))
  minor = int(match.group("minor"))
  patch = int(match.group("patch"))
  suffix_prefix = match.group("suffix_prefix")
  suffix_num = match.group("suffix_num")

  if suffix_prefix is not None:
    reset_width = 1 if (suffix_num is not None and len(suffix_num) == 1) else 2
    if bump_type == "major":
      return f"{major + 1}.0.0-{suffix_prefix}{1:0{reset_width}d}"
    if bump_type == "minor":
      return f"{major}.{minor + 1}.0-{suffix_prefix}{1:0{reset_width}d}"
    patch_width = len(suffix_num) if suffix_num is not None else 2
    next_num = (int(suffix_num) if suffix_num is not None else 1) + 1
    return f"{major}.{minor}.{patch}-{suffix_prefix}{next_num:0{patch_width}d}"

  if bump_type == "major":
    return f"{major + 1}.0.0"
  if bump_type == "minor":
    return f"{major}.{minor + 1}.0"
  return f"{major}.{minor}.{patch + 1}"


def is_valid_plus_one_bump(new_version: str, latest_released_version: str) -> bool:
  """Checks if new_version is at most +1 major, +1 minor, or +1 patch bump from latest_released_version."""
  allowed_versions = {
      bump_version_string(latest_released_version, b_type)
      for b_type in VALID_BUMP_TYPES
  }
  return new_version in allowed_versions


def is_already_bumped(
    current_version: str, latest_released_version: str, bump_type: str
) -> bool:
  """Checks if current_version already has at least bump_type applied from latest_released_version."""
  req_idx = VALID_BUMP_TYPES.index(bump_type)
  existing_or_higher = {
      bump_version_string(latest_released_version, b_type)
      for b_type in VALID_BUMP_TYPES[req_idx:]
  }
  return current_version in existing_or_higher


@dataclasses.dataclass(frozen=True)
class PlannedBump:
  """A computed, not-yet-written version bump for a single SDK."""

  proj: str
  props_path: str
  old_version: str
  new_version: str
  new_content: str


@dataclasses.dataclass(frozen=True)
class AlreadyBumped:
  """Represents an SDK whose version already satisfies the requested bump."""

  proj: str
  current_version: str


def plan_sdk_bump(
    repo_root: str,
    proj: str,
    rel_dir: str,
    bump_type: str,
    ignore_existing: bool = False,
) -> PlannedBump | AlreadyBumped | None:
  """Computes the new version for the SDK's gradle.properties without writing.

  Returns a PlannedBump, AlreadyBumped when ignore_existing is True and the SDK
  already has at least bump_type applied, or None if gradle.properties or
  version= does not exist. Raises VersionBumpRuleError if the bump violates the
  +1 rule relative to latestReleasedVersion.
  """
  props_path = os.path.join(repo_root, rel_dir, "gradle.properties")
  if not os.path.exists(props_path):
    return None

  with open(props_path, "r", encoding="utf-8", newline="") as f:
    content = f.read()

  match = VERSION_LINE_RE.search(content)
  if not match:
    return None

  old_version = match.group(2)
  new_version = bump_version_string(old_version, bump_type)

  latest_match = LATEST_RELEASED_LINE_RE.search(content)
  if latest_match:
    latest_released = latest_match.group(2)
    if is_already_bumped(old_version, latest_released, bump_type):
      if ignore_existing:
        return AlreadyBumped(proj, old_version)
      allowed = [
          bump_version_string(latest_released, t) for t in VALID_BUMP_TYPES
      ]
      raise VersionBumpRuleError(
          f"Cannot bump {proj} from {old_version} to {new_version}:"
          f" version {old_version} already has a {bump_type} (or higher) bump"
          f" from latestReleasedVersion={latest_released}"
          f" (allowed target versions: {', '.join(allowed)};"
          " pass --ignore-existing to skip)."
      )
    if not is_valid_plus_one_bump(new_version, latest_released):
      allowed = [
          bump_version_string(latest_released, t) for t in VALID_BUMP_TYPES
      ]
      raise VersionBumpRuleError(
          f"Cannot bump {proj} from {old_version} to {new_version}: exceeds +1"
          f" version bump from latestReleasedVersion={latest_released}"
          f" (allowed target versions: {', '.join(allowed)})."
      )

  new_content = VERSION_LINE_RE.sub(
      lambda m: f"{m.group(1)}{new_version}{m.group(3)}",
      content,
      count=1,
  )

  return PlannedBump(proj, props_path, old_version, new_version, new_content)


def apply_bump(bump: PlannedBump) -> None:
  """Writes a previously planned bump to disk."""
  with open(bump.props_path, "w", encoding="utf-8", newline="") as f:
    f.write(bump.new_content)


def parse_args(
    argv: list[str] | None = None,
) -> tuple[bool, list[str], str, bool, bool]:
  """Parses CLI arguments and returns (bump_all, sdk_names, bump_type, dry_run, ignore_existing)."""
  parser = argparse.ArgumentParser(
      description=(
          "Bump the version declared in gradle.properties for SDKs in"
          " subprojects.cfg."
      )
  )
  parser.add_argument(
      "target",
      help=(
          "Name of the SDK or comma-separated list of SDKs (for example,"
          " firebase-common,firebase-firestore), 'all' to bump all SDKs, or"
          " the bump type when --all is passed."
      ),
  )
  parser.add_argument(
      "bump_type",
      nargs="?",
      help="Version part to bump: patch, minor, or major.",
  )
  parser.add_argument(
      "--all",
      action="store_true",
      dest="bump_all",
      help="Bump all SDKs declared in subprojects.cfg.",
  )
  parser.add_argument(
      "--dry-run",
      action="store_true",
      dest="dry_run",
      help="Print the planned version bumps without modifying any files.",
  )
  parser.add_argument(
      "--ignore-existing",
      action="store_true",
      dest="ignore_existing",
      help=(
          "Do not fail if an SDK already has the requested (or higher) version"
          " bump relative to latestReleasedVersion."
      ),
  )

  args = parser.parse_args(argv)

  if args.bump_all:
    if args.bump_type is None:
      bump_type = args.target.lower()
    elif args.target.lower() == "all":
      bump_type = args.bump_type.lower()
    else:
      parser.error("Cannot specify both SDK names and --all.")
    sdk_names: list[str] = []
    bump_all = True
  else:
    if args.bump_type is None:
      parser.error(
          "Both target SDK(s) (or 'all') and bump type (patch, minor, major)"
          " are required."
      )
    raw_targets = [s.strip() for s in args.target.split(",") if s.strip()]
    if not raw_targets:
      parser.error("At least one SDK name must be provided.")
    if len(raw_targets) == 1 and raw_targets[0].lower() == "all":
      bump_all = True
      sdk_names = []
    elif any(t.lower() == "all" for t in raw_targets):
      parser.error("Cannot combine 'all' with specific SDK names.")
    else:
      bump_all = False
      sdk_names = raw_targets
    bump_type = args.bump_type.lower()

  if bump_type not in VALID_BUMP_TYPES:
    parser.error(
        f"Invalid bump type '{bump_type}'. Choose from: {', '.join(VALID_BUMP_TYPES)}"
    )

  return bump_all, sdk_names, bump_type, args.dry_run, args.ignore_existing


def main(argv: list[str] | None = None) -> int:
  bump_all, sdk_names, bump_type, dry_run, ignore_existing = parse_args(argv)
  repo_root = get_git_root()
  try:
    sdks = get_sdk_subprojects(repo_root)
  except OSError as e:
    print(f"Error: {e}", file=sys.stderr)
    return 1

  # Resolve the set of (proj, rel_dir) targets to operate on.
  targets: list[tuple[str, str]] = []
  had_error = False
  if bump_all:
    targets = [
        (proj, rel_dir)
        for proj, rel_dir in sdks.items()
        if proj not in EXCLUDED_FROM_ALL_SDKS
    ]
  else:
    seen_projs: set[str] = set()
    for sdk_name in sdk_names:
      try:
        proj, rel_dir = resolve_sdk_dir(sdk_name, sdks)
      except ValueError as e:
        print(f"Error: {e}", file=sys.stderr)
        had_error = True
        continue
      if proj not in seen_projs:
        seen_projs.add(proj)
        targets.append((proj, rel_dir))

  # Phase 1: compute every bump without touching the filesystem, so that a
  # failure in any SDK leaves the repository untouched.
  outcomes: list[PlannedBump | AlreadyBumped] = []
  for proj, rel_dir in targets:
    try:
      outcome = plan_sdk_bump(
          repo_root, proj, rel_dir, bump_type, ignore_existing=ignore_existing
      )
    except (ValueError, OSError) as e:
      print(f"Error: {e}", file=sys.stderr)
      had_error = True
      continue

    if outcome is None:
      if not bump_all:
        print(
            f"Error: Could not find a 'version=' declaration in"
            f" {rel_dir}/gradle.properties.",
            file=sys.stderr,
        )
        had_error = True
      continue
    outcomes.append(outcome)

  if had_error:
    print("Error: No files were modified.", file=sys.stderr)
    return 1

  if not outcomes:
    print("Error: No SDK versions were updated.", file=sys.stderr)
    return 1

  # Phase 2: write all planned bumps and report results.
  for outcome in outcomes:
    if isinstance(outcome, AlreadyBumped):
      print(f"{outcome.proj}: {outcome.current_version} (already bumped)")
      continue
    if not dry_run:
      try:
        apply_bump(outcome)
      except OSError as e:
        print(
            f"Error: Failed to write {outcome.props_path}: {e}", file=sys.stderr
        )
        return 1
    print(f"{outcome.proj}: {outcome.old_version} -> {outcome.new_version}")

  if dry_run:
    print("Dry run: no files were modified.")

  return 0


if __name__ == "__main__":
  sys.exit(main())
