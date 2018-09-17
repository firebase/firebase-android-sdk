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
import unittest

from fireci.internal import _artifact_handler

from .fileutil import (
    Artifact,
    create_artifacts,
    in_tempdir,
)

_ARTIFACTS = '_artifacts'


class ArtifactHandlerTest(unittest.TestCase):

  @in_tempdir
  def test_when_target_dir_exists_should_not_fail(self):
    os.mkdir(_ARTIFACTS)
    with _artifact_handler(_ARTIFACTS, ['**/build/reports/results.xml']):
      pass
    root = pathlib.Path(_ARTIFACTS)
    self.assertTrue(root.is_dir())

  @in_tempdir
  def test_when_pattern_matches_file_should_copy_file(self):
    with _artifact_handler(_ARTIFACTS, ['**/build/reports/results.xml']):
      create_artifacts(
          Artifact('sdk1/build/reports/results.xml', content='hello'))

    root = pathlib.Path(_ARTIFACTS)

    self.assertTrue(root.is_dir())
    sdk1_results = root / 'sdk1_build_reports_results.xml'
    self.assertTrue(sdk1_results.is_file())

  @in_tempdir
  def test_when_pattern_matches_dirs_should_copy_dirs(self):
    with _artifact_handler(_ARTIFACTS, ['**/build/reports']):
      create_artifacts(
          Artifact('sdk1/build/reports/results.xml', content='hello'),
          Artifact('sdk2/build/reports', is_dir=True),
          Artifact('sdk2/build/should_not_match/file.txt'),
      )
    root = pathlib.Path(_ARTIFACTS)

    self.assertTrue(root.is_dir())
    sdk1_results = root / 'sdk1_build_reports' / 'results.xml'
    self.assertTrue(sdk1_results.is_file())
    with sdk1_results.open() as f:
      self.assertEqual(f.read(), 'hello')

    sdk2_results = root / 'sdk2_build_reports'
    self.assertTrue(sdk2_results.is_dir())

    should_not_match = root / 'sdk2_build_should_not_match'
    self.assertFalse(should_not_match.exists())
