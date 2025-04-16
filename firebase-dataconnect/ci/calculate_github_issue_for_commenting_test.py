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

  @hypothesis.given(number=st.integers(min_value=0, max_value=10000))
  def test_ignores_leading_zeroes(self, number: int) -> None:
    github_ref = f"refs/pull/0{number}/merge"
    assert sut.pr_number_from_github_ref(github_ref) == number

  @hypothesis.given(invalid_github_ref=st.text())
  def test_returns_none_on_random_input(self, invalid_github_ref: str) -> None:
    assert sut.pr_number_from_github_ref(invalid_github_ref) is None

  @pytest.mark.parametrize(
    "invalid_number",
    [
      "",
      "-1",
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


class Test_github_issue_from_pr_body:
  @hypothesis.given(number=st.integers(min_value=0, max_value=10000))
  def test_returns_number(self, number: int) -> None:
    text = f"zzyzx={number}"
    assert sut.github_issue_from_pr_body(text, "zzyzx") == number

  @hypothesis.given(number=st.integers(min_value=0, max_value=10000))
  def test_ignores_leading_zeroes(self, number: int) -> None:
    text = f"zzyzx=0{number}"
    assert sut.github_issue_from_pr_body(text, "zzyzx") == number

  @hypothesis.given(number=st.integers(min_value=0, max_value=10000))
  def test_ignores_whitespace(self, number: int) -> None:
    text = f"  zzyzx  =  {number}  "
    assert sut.github_issue_from_pr_body(text, "zzyzx") == number

  @hypothesis.given(
    number1=st.integers(min_value=0, max_value=10000),
    number2=st.integers(min_value=0, max_value=10000),
  )
  def test_does_not_ignore_whitespace_in_key(self, number1: int, number2: int) -> None:
    text = f"zzyzx={number1}\n  z z y z x  =  {number2}  "
    assert sut.github_issue_from_pr_body(text, "z z y z x") == number2

  @hypothesis.given(
    number1=st.integers(min_value=0, max_value=10000),
    number2=st.integers(min_value=0, max_value=10000),
  )
  def test_returns_first_number_ignoring_second(self, number1: int, number2: int) -> None:
    text = f"zzyzx={number1}\nzzyzx={number2}"
    assert sut.github_issue_from_pr_body(text, "zzyzx") == number1

  @hypothesis.given(number=st.integers(min_value=0, max_value=10000))
  def test_returns_first_valid_number_ignoring_invalid(self, number: int) -> None:
    text = f"zzyzx=12X34\nzzyzx={number}"
    assert sut.github_issue_from_pr_body(text, "zzyzx") == number

  @hypothesis.given(number=st.integers(min_value=0, max_value=10000))
  def test_returns_number_amidst_other_lines(self, number: int) -> None:
    text = f"line 1\nline 2\nzzyzx={number}\nline 3"
    assert sut.github_issue_from_pr_body(text, "zzyzx") == number

  @hypothesis.given(number=st.integers(min_value=0, max_value=10000))
  def test_returns_escapes_regex_special_chars_in_key(self, number: int) -> None:
    text = f"*+={number}"
    assert sut.github_issue_from_pr_body(text, "*+") == number

  @pytest.mark.parametrize(
    "text",
    [
      "",
      "asdf",
      "zzyzx=",
      "=zzyzx",
      "zzyzx=a",
      "zzyzx=-1",
      "zzyzx=a123",
      "zzyzx=123a",
      "zzyzx=1.2",
      "a zzyzx=1234",
      "zzyzx=1234 a",
      pytest.param(
        "zzyzx=1234",
        marks=pytest.mark.xfail(
          reason="make sure that the test would otherwise pass on valid text",
          strict=True,
        ),
      ),
    ],
  )
  def test_returns_none_when_key_not_found_or_cannot_parse_int(self, text: str) -> None:
    assert sut.github_issue_from_pr_body(text, "zzyzx") is None
