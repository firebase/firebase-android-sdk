# Copyright 2018 Google LLC
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

import fnmatch
import click
import contextlib
import os
import re

from fireci import ci_command


@click.option(
    '--ignore-path',
    '-i',
    default=(),
    multiple=True,
    type=str,
    help='Unix path pattern to ignore when searching for matching files. '
    'Multiple values allowed.',
)
@click.option(
    '--include-extension',
    '-e',
    default=(),
    multiple=True,
    type=str,
    help='File extensions to scan for copyright violation. '
    'Multiple values allowed.',
    required=True,
)
@click.option(
    '--expected-regex',
    '-r',
    default='.*Copyright [0-9]{4} Google LLC',
    type=str,
    help='Regex expected to be present in the file.',
)
@click.argument(
    'dir_to_scan',
    type=click.Path(exists=True, file_okay=False),
    default='.',
    nargs=1,
)
@ci_command()
def copyright_check(dir_to_scan, ignore_path, include_extension,
                    expected_regex):
  """Checks matching files' content for copyright information."""
  expression = re.compile(expected_regex)
  failed_files = []
  with chdir(dir_to_scan):
    for x in walk('.', ignore_path, include_extension):
      with open(x) as f:
        if not match_any(f, lambda line: expression.match(line)):
          failed_files.append(x)

  if failed_files:
    raise click.ClickException(
        "The following files do not have valid copyright information:\n{}"
        .format('\n'.join(failed_files)))


@contextlib.contextmanager
def chdir(directory):
  original_dir = os.getcwd()
  os.chdir(directory)
  try:
    yield
  finally:
    os.chdir(original_dir)


def match_any(iterable, predicate):
  """Returns True if at least one item in the iterable matches the predicate."""
  for x in iterable:
    if predicate(x):
      return True
  return False


def walk(dir_to_scan, ignore_paths, extensions_to_include):
  """Recursively walk the provided directory and yield matching paths."""
  for root, dirs, filenames in os.walk(dir_to_scan):
    dirs[:] = (
        x for x in dirs if not matches(os.path.join(root, x), ignore_paths))

    for f in filenames:
      filename = os.path.join(root, f)
      if os.path.splitext(f)[1][1:] in extensions_to_include and not matches(
          filename, ignore_paths):
        yield os.path.normpath(filename)


def matches(path, paths):
  path = os.path.normpath(path)
  return match_any(paths, lambda p: fnmatch.fnmatch(path, p))
