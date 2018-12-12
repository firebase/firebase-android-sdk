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

import os
import pathlib
import subprocess
import unittest

from click.testing import CliRunner
from fireci.main import cli

from . import scripts
from .fileutil import (
    Artifact,
    create_artifacts,
    in_tempdir,
)


class CliInvocationTests(unittest.TestCase):
  runner = CliRunner()

  @in_tempdir
  def test_gradle_invocation(self):
    args = ['--arg1', 'task1']
    create_artifacts(
        Artifact(
            'gradlew',
            content=scripts.with_expected_arguments_and_artifacts(
                ['./gradlew'] + args,
                {'GRADLE_OPTS': 'opts'},
                ('sdk1/build/output/file1', 'content1'),
                ('sdk1/build/outputss/file2', 'content2'),
            ),
            mode=0o744))
    result = self.runner.invoke(cli, [
        '--artifact-patterns', '**/build/output', 'gradle', '--gradle-opts',
        'opts', '--'
    ] + args)
    self.assertEqual(result.exit_code, 0)

    artifacts = pathlib.Path('_artifacts')
    self.assertTrue(artifacts.exists())

    output_file = artifacts / 'sdk1_build_outputss' / 'file2'
    self.assertFalse(output_file.exists())

    output_file = artifacts / 'sdk1_build_output' / 'file1'
    self.assertTrue(output_file.is_file())

    with output_file.open() as f:
      self.assertEqual(f.read(), 'content1')

  @in_tempdir
  def test_smoke_test_when_build_fails_should_fail(self):
    create_artifacts(
        Artifact('gradlew', content=scripts.with_exit(1), mode=0o744))
    result = self.runner.invoke(cli, ['smoke_tests'])
    self.assertNotEqual(result.exit_code, 0)

  @in_tempdir
  def test_smoke_test_when_build_succeeds_and_tests_fails_should_fail(self):
    create_artifacts(
        Artifact('gradlew', content=scripts.with_exit(0), mode=0o744),
        Artifact('test-apps/gradlew', content=scripts.with_exit(1), mode=0o744),
    )
    result = self.runner.invoke(cli, ['smoke_tests'])
    self.assertNotEqual(result.exit_code, 0)

  @in_tempdir
  def test_smoke_test_when_build_succeeds_and_tests_succeed_should_succeed(
      self):
    create_artifacts(
        Artifact('gradlew', content=scripts.with_exit(0), mode=0o744),
        Artifact('test-apps/gradlew', content=scripts.with_exit(0), mode=0o744),
    )
    result = self.runner.invoke(cli, ['smoke_tests'])
    self.assertEqual(result.exit_code, 0)

  @in_tempdir
  def test_smoke_test_no_buildType_should_invoke_gradle_with_release_build_type(
      self):
    create_artifacts(
        Artifact(
            'gradlew',
            content=scripts.with_expected_arguments(
                ['./gradlew', 'publishAllToBuildDir']),
            mode=0o744),
        Artifact(
            'test-apps/gradlew',
            content=scripts.with_expected_arguments(
                ['./gradlew', 'connectedCheck', '-PtestBuildType=release'], {
                    'GRADLE_OPTS':
                        '-Dmaven.repo.local={}'.format(
                            os.path.join(os.getcwd(), 'build', 'm2repository'))
                }),
            mode=0o744),
    )
    result = self.runner.invoke(cli, ['smoke_tests'])
    self.assertEqual(result.exit_code, 0)

  @in_tempdir
  def test_smoke_test_with_buildType_should_invoke_gradle_with_release_build_type(
      self):
    create_artifacts(
        Artifact(
            'gradlew',
            content=scripts.with_expected_arguments(
                ['./gradlew', 'publishAllToBuildDir']),
            mode=0o744),
        Artifact(
            'test-apps/gradlew',
            content=scripts.with_expected_arguments(
                ['./gradlew', 'connectedCheck', '-PtestBuildType=debug'], {
                    'GRADLE_OPTS':
                        '-Dmaven.repo.local={}'.format(
                            os.path.join(os.getcwd(), 'build', 'm2repository'))
                }),
            mode=0o744),
    )
    result = self.runner.invoke(cli,
                                ['smoke_tests', '--app-build-variant', 'debug'])
    self.assertEqual(result.exit_code, 0)

  @in_tempdir
  def test_copyright_check_when_no_violating_files_should_succeed(self):
    create_artifacts(
        Artifact('dir/file.py', content='# Copyright 2018 Google LLC'))

    result = self.runner.invoke(cli, ['copyright_check', '-e', 'py'])
    self.assertEqual(result.exit_code, 0)

  @in_tempdir
  def test_copyright_check_when_violating_files_exist_should_fail(self):
    create_artifacts(
        Artifact('dir/file.py', content='# Copyright 2018 Google LLC'),
        Artifact('dir/file2.py', content='# hello'),
        Artifact('dir2/file3.xml', content='# hello'),
    )

    result = self.runner.invoke(cli,
                                ['copyright_check', '-e', 'py', '-e'
                                 'xml'])
    self.assertEqual(result.exit_code, 1)
    self.assertFalse('dir/file.py' in result.output)
    self.assertTrue('dir/file2.py' in result.output)
    self.assertTrue('dir2/file3.xml' in result.output)

  @in_tempdir
  def test_copyright_check_when_violating_files_exist_should_fail2(self):
    create_artifacts(
        Artifact('dir/file.py', content='# Copyright 2018 Google LLC'),
        Artifact('dir/file2.py', content='# hello'),
        Artifact('dir2/file3.xml', content='# hello'),
        Artifact('dir2/subdir/file4.xml', content='# hello'),
    )

    result = self.runner.invoke(
        cli, ['copyright_check', '-e', 'py', '-e'
              'xml', '-i', 'dir2/**'])

    self.assertEqual(result.exit_code, 1)
    self.assertFalse('dir/file.py' in result.output)
    self.assertTrue('dir/file2.py' in result.output)
    self.assertFalse('dir2/file3.xml' in result.output)
    self.assertFalse('dir2/subdir/file4.xml' in result.output)

  @in_tempdir
  def test_copyright_check_when_violating_files_exist_should_fail3(self):
    create_artifacts(
        Artifact('dir/subdir/file.py', content='# Copyright 2018 Google LLC'),
        Artifact('dir/subdir/file2.py', content='# hello'),
        Artifact('dir/subdir2/file3.xml', content='# hello'),
        Artifact('dir/subdir4/file4.xml', content='# hello'),
    )

    result = self.runner.invoke(
        cli,
        ['copyright_check', '-e', 'py', '-e'
         'xml', '-i', 'subdir2/**', 'dir'])

    self.assertEqual(result.exit_code, 1)
    self.assertFalse('subdir/file.py' in result.output)
    self.assertTrue('subdir/file2.py' in result.output)
    self.assertTrue('subdir4/file4.xml' in result.output)
