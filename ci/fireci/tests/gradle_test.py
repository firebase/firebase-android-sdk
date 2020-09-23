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
from . import scripts


class GradleTest(unittest.TestCase):

  @in_tempdir
  def test_when_gradle_suceeds_should_not_throw(self):
    create_artifacts(
        Artifact('gradlew', content=scripts.with_exit(0), mode=0o744))
    self.assertEqual(gradle.run('tasks').returncode, 0)

  @in_tempdir
  def test_when_gradle_suceeds_should_not_throw(self):
    create_artifacts(
        Artifact('gradlew', content=scripts.with_exit(1), mode=0o744))
    self.assertRaises(subprocess.CalledProcessError,
                      lambda: gradle.run('tasks'))

  @in_tempdir
  def test_gradle_passes_arguments_to_gradlew(self):
    args = ['--foo', '-Pbar=baz', 'task1', 'task2']
    gradle_opts = '--some --Dgradle_opts=foo'
    create_artifacts(
        Artifact(
            'gradlew',
            content=scripts.with_expected_arguments(
                ['./gradlew'] + args, {
                    'GRADLE_OPTS': gradle_opts,
                    'ADB_INSTALL_TIMEOUT': gradle.ADB_INSTALL_TIMEOUT
                }),
            mode=0o744,
        ))
    self.assertEqual(gradle.run(*args, gradle_opts=gradle_opts).returncode, 0)
