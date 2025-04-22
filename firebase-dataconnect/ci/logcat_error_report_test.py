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

from __future__ import annotations

import re

import pytest

import logcat_error_report as sut


class TestRegularExpressionPatterns:
  @pytest.mark.parametrize(
    "string",
    [
      "",
      "XTestRunner: started: fooTest1234",
      "TestRunner: started:fooTest1234",
      pytest.param(
        "TestRunner: started: fooTest1234",
        marks=pytest.mark.xfail(
          reason="make sure that the test would otherwise pass on match",
          strict=True,
        ),
      ),
    ],
  )
  def test_test_started_pattern_no_match(self, string: str) -> None:
    assert re.search(sut.TEST_STARTED_PATTERN, string) is None

  @pytest.mark.parametrize(
    ("string", "expected_name"),
    [
      ("TestRunner: started: fooTest1234", "fooTest1234"),
      ("  TestRunner: started: fooTest1234", "fooTest1234"),
      ("TestRunner: started:   fooTest1234", "fooTest1234"),
      ("TestRunner: started: fooTest1234  ", "fooTest1234"),
      ("TestRunner: started: fooTest1234(abc.123)", "fooTest1234(abc.123)"),
      ("TestRunner: started: a $ 2 ^ %% .  ", "a $ 2 ^ %% ."),
      pytest.param(
        "i do not match the pattern",
        None,
        marks=pytest.mark.xfail(
          reason="make sure that the test would otherwise pass on match",
          strict=True,
        ),
      ),
    ],
  )
  def test_test_started_pattern_match(self, string: str, expected_name: str) -> None:
    match = re.search(sut.TEST_STARTED_PATTERN, string)
    assert match is not None
    assert match.group("name") == expected_name

  @pytest.mark.parametrize(
    "string",
    [
      "",
      "XTestRunner: finished: fooTest1234",
      "TestRunner: finished:fooTest1234",
      pytest.param(
        "TestRunner: finished: fooTest1234",
        marks=pytest.mark.xfail(
          reason="make sure that the test would otherwise pass on match",
          strict=True,
        ),
      ),
    ],
  )
  def test_test_finished_pattern_no_match(self, string: str) -> None:
    assert re.search(sut.TEST_FINISHED_PATTERN, string) is None

  @pytest.mark.parametrize(
    ("string", "expected_name"),
    [
      ("TestRunner: finished: fooTest1234", "fooTest1234"),
      ("  TestRunner: finished: fooTest1234", "fooTest1234"),
      ("TestRunner: finished:   fooTest1234", "fooTest1234"),
      ("TestRunner: finished: fooTest1234  ", "fooTest1234"),
      ("TestRunner: finished: fooTest1234(abc.123)", "fooTest1234(abc.123)"),
      ("TestRunner: finished: a $ 2 ^ %% .  ", "a $ 2 ^ %% ."),
      pytest.param(
        "i do not match the pattern",
        None,
        marks=pytest.mark.xfail(
          reason="make sure that the test would otherwise pass on match",
          strict=True,
        ),
      ),
    ],
  )
  def test_test_finished_pattern_match(self, string: str, expected_name: str) -> None:
    match = re.search(sut.TEST_FINISHED_PATTERN, string)
    assert match is not None
    assert match.group("name") == expected_name

  @pytest.mark.parametrize(
    "string",
    [
      "",
      "XTestRunner: failed: fooTest1234",
      "TestRunner: failed:fooTest1234",
      pytest.param(
        "TestRunner: failed: fooTest1234",
        marks=pytest.mark.xfail(
          reason="make sure that the test would otherwise pass on match",
          strict=True,
        ),
      ),
    ],
  )
  def test_test_failed_pattern_no_match(self, string: str) -> None:
    assert re.search(sut.TEST_FAILED_PATTERN, string) is None

  @pytest.mark.parametrize(
    ("string", "expected_name"),
    [
      ("TestRunner: failed: fooTest1234", "fooTest1234"),
      ("  TestRunner: failed: fooTest1234", "fooTest1234"),
      ("TestRunner: failed:   fooTest1234", "fooTest1234"),
      ("TestRunner: failed: fooTest1234  ", "fooTest1234"),
      ("TestRunner: failed: fooTest1234(abc.123)", "fooTest1234(abc.123)"),
      ("TestRunner: failed: a $ 2 ^ %% .  ", "a $ 2 ^ %% ."),
      pytest.param(
        "i do not match the pattern",
        None,
        marks=pytest.mark.xfail(
          reason="make sure that the test would otherwise pass on match",
          strict=True,
        ),
      ),
    ],
  )
  def test_test_failed_pattern_match(self, string: str, expected_name: str) -> None:
    match = re.search(sut.TEST_FAILED_PATTERN, string)
    assert match is not None
    assert match.group("name") == expected_name
