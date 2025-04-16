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

import hypothesis
import hypothesis.strategies as st
import pytest

import calculate_github_issue_for_commenting as sut


class Test_pr_number_from_github_ref:
  @hypothesis.given(number=st.integers(min_value=0, max_value=10000))
  def test_returns_number_from_valid_github_ref(self, number: int) -> None:
    github_ref = f"refs/pull/{number}/merge"
    assert sut.pr_number_from_github_ref(github_ref) == number

  @hypothesis.given(invalid_github_ref=st.text())
  def test_returns_none_on_random_input(self, invalid_github_ref: str) -> None:
    assert sut.pr_number_from_github_ref(invalid_github_ref) is None

  @pytest.mark.parametrize(
    "invalid_number",
    [
      "",
      "123a",
      "a123",
      "12a34",
      "1.2",
      pytest.param(
        "1234",
        marks=pytest.mark.xfail(
          reason="make sure that the test would otherwise pass on valid int values",
          strict=True,
        ),
      ),
    ],
  )
  def test_returns_none_on_invalid_number(self, invalid_number: str) -> None:
    invalid_github_ref = f"refs/pull/{invalid_number}/merge"
    assert sut.pr_number_from_github_ref(invalid_github_ref) is None

  @pytest.mark.parametrize(
    "malformed_ref",
    [
      "",
      "refs",
      "refs/",
      "refs/pull",
      "refs/pull/",
      "refs/pull/1234",
      "refs/pull/1234/",
      "Refs/pull/1234/merge",
      "refs/Pull/1234/merge",
      "refs/pull/1234/Merge",
      "Arefs/pull/1234/merge",
      "refs/pull/1234/mergeZ",
      " refs/pull/1234/merge",
      "refs/pull/1234/merge ",
      pytest.param(
        "refs/pull/1234/merge",
        marks=pytest.mark.xfail(
          reason="make sure that the test would otherwise pass on valid ref",
          strict=True,
        ),
      ),
    ],
  )
  def test_returns_none_on_malformed_ref(self, malformed_ref: str) -> None:
    assert sut.pr_number_from_github_ref(malformed_ref) is None
