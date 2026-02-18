# Copyright 2024 Google LLC
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

import click
import logging

from fireci import ci_command
from fireci import ci_utils
from fireci import dir_utils
from typing import Tuple, List, Callable, Union
from termcolor import colored

log = logging.getLogger('fireci.clean')

@click.argument("projects", 
  nargs=-1,
  type=click.Path(), 
  required=False
)
@click.option('--gradle/--no-gradle', default=False, help="Delete the local .gradle caches.")
@click.option('--build/--no-build', default=True, help="Delete the local build caches.")
@click.option('--transforms/--no-transforms', default=False, help="Delete the system-wide transforms cache.")
@click.option('--build-cache/--no-build-cache', default=False, help="Delete the system-wide build cache.")

@click.option('--deep/--no-deep', default=False, help="Delete all of the system-wide files for gradle.")
@click.option('--cache/--no-cache', default=False, help="Delete all of the system-wide caches for gradle.")
@ci_command(epilog="""
  Clean a subset of projects:

  \b
  $ fireci clean firebase-common

  Clean all projects:

  $ fireci clean
""")
def clean(projects, gradle, build, transforms, build_cache, deep, cache):
  """
  Delete files cached by gradle.

  Alternative to the standard `gradlew clean`, which runs outside the scope of gradle,
  and provides deeper cache cleaning capabilities.
  """
  if not projects:
    log.debug("No projects specified, so we're defaulting to all projects.")
    projects = ci_utils.get_projects()

  cache = cache or deep
  gradle = gradle or cache

  cleaners = []

  if build:
    cleaners.append(delete_build)
  if gradle:
    cleaners.append(delete_gradle)

  results = [call_and_sum(projects, cleaner) for cleaner in cleaners]
  local_count = tuple(map(sum, zip(*results)))

  cleaners = []

  if deep:
    cleaners.append(delete_deep)
  elif cache:
    cleaners.append(delete_cache)
  else:
    if transforms:
      cleaners.append(delete_transforms)
    if build_cache:
      cleaners.append(delete_build_cache)

  results = [cleaner() for cleaner in cleaners]
  system_count = ci_utils.counts(results)

  [deleted, skipped] = tuple(a + b for a, b in zip(local_count, system_count))

  log.info(f"""
  Clean results:

    {colored("Deleted:", None, attrs=["bold"])} {colored(deleted, "red")}
    {colored("Already deleted:", None, attrs=["bold"])} {colored(skipped, "grey")}
  """)


def call_and_sum(variables: List[str], func: Callable[[str], Union[bool, int]]) -> Tuple[int, int]:
  results = list(map(lambda var: func(var), variables))
  return ci_utils.counts(results)

def delete_build(dir: str) -> bool:
  return dir_utils.rmdir(f"{dir}/build")

def delete_gradle(dir: str) -> bool:
  return dir_utils.rmdir(f"{dir}/.gradle")

def delete_transforms() -> int:
  return dir_utils.rmglob("~/.gradle/caches/transforms-*")

def delete_build_cache() -> int:
  return dir_utils.rmglob("~/.gradle/caches/build-cache-*")

def delete_deep() -> bool:
  return dir_utils.rmdir("~/.gradle")

def delete_cache() -> bool:
  return dir_utils.rmdir("~/.gradle/caches")
