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

import unittest

from fireciplugins.copyright import (
    match_any,
    matches,
    walk,
)
from .fileutil import (
    Artifact,
    create_artifacts,
    in_tempdir,
)


class CopyrightCheckTest(unittest.TestCase):

  def test_match_any(self):
    test_data = (
        ((1, 2, 3), lambda x: x == 2, True),
        ((1, 2, 3), lambda x: x == 5, False),
        ((), lambda x: x == 1, False),
    )
    for iterable, predicate, expected_result in test_data:
      with self.subTest():
        self.assertEqual(match_any(iterable, predicate), expected_result)

  def test_matches(self):
    test_data = (
        ('file.py', '*.py', True),
        ('file.xml', '*.py', False),
        ('hello/file.py', '*.py', True),
        ('hello/file.xml', 'hello/**', True),
        ('some/file.xml', 'hello/**', False),
    )

    for path, path_to_match, expected_result in test_data:
      pass
      with self.subTest("'{}' matches '{}' must be {}".format(
          path, path_to_match, expected_result)):
        self.assertEqual(matches(path, [path_to_match]), expected_result)

  @in_tempdir
  def test_walk_in_empty_dir(self):
    paths = walk('.', [], ['py', 'xml'])
    self.assertTrue(len(list(paths)) == 0)

  @in_tempdir
  def test_walk_should_filter_out_non_matching_files(self):
    create_artifacts(
        Artifact('hello/world/foo.py'), Artifact('dir1/subdir2/file.py'),
        Artifact('hello/world.py'), Artifact('dir1/subdir2/file.py'),
        Artifact('dir1/subdir2/file.gradle'), Artifact('dir1/subdir2/file.xml'))
    paths = walk('.', ['hello/**'], ['py', 'xml'])

    self.assertEqual(
        set(paths), {'dir1/subdir2/file.py', 'dir1/subdir2/file.xml'})
