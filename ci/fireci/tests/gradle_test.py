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

import subprocess
import unittest

from fireci import gradle

from .fileutil import (
    Artifact,
    create_artifacts,
    in_tempdir,
)

_SCRIPT_WITH_EXIT_TEMPLATE = """\
#!/usr/bin/env python3
import sys
sys.exit({})
"""


def script_with_exit(exit_code):
  return _SCRIPT_WITH_EXIT_TEMPLATE.format(exit_code)


_SCRIPT_WITH_EXPECTED_ARGUMENTS_TEMPLATE = """\
#!/usr/bin/env python3
import sys, os
expected_args = [{}]
expected_env = {}
if sys.argv != expected_args:
    raise ValueError('Expected args: %s, but got %s' % (expected_args, sys.argv))
for k, v in expected_env.items():
    envval = os.environ.get(k, '')
    if envval != v:
        raise ValueError("Expected env[%s] == '%s', but got '%s'" % (k, v, envval))
"""


def script_with_expected_arguments(args, env):
  arg_string = ', '.join(['"{}"'.format(arg) for arg in args])
  return _SCRIPT_WITH_EXPECTED_ARGUMENTS_TEMPLATE.format(arg_string, env)


class GradleTest(unittest.TestCase):

  @in_tempdir
  def test_when_gradle_suceeds_should_not_throw(self):
    create_artifacts(
        Artifact('gradlew', content=script_with_exit(0), mode=0o744))
    self.assertEqual(gradle.run('tasks'), 0)

  @in_tempdir
  def test_when_gradle_suceeds_should_not_throw(self):
    create_artifacts(
        Artifact('gradlew', content=script_with_exit(1), mode=0o744))
    self.assertRaises(subprocess.CalledProcessError,
                      lambda: gradle.run('tasks'))

  @in_tempdir
  def test_gradle_passes_arguments_to_gradlew(self):
    args = ['--foo', '-Pbar=baz', 'task1', 'task2']
    gradle_opts = '--some --Dgradle_opts=foo'
    create_artifacts(
        Artifact(
            'gradlew',
            content=script_with_expected_arguments(
                ['./gradlew'] + args, {
                    'GRADLE_OPTS': gradle_opts,
                    'ADB_INSTALL_TIMEOUT': gradle.ADB_INSTALL_TIMEOUT
                }),
            mode=0o744,
        ))
    self.assertEqual(gradle.run(*args, gradle_opts=gradle_opts.split()), 0)
