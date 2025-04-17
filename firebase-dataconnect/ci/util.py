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

import dataclasses
import json
import logging
import re
import subprocess
import typing

if typing.TYPE_CHECKING:
  from collections.abc import Iterable


@dataclasses.dataclass(frozen=True)
class GitHubPrInfo:
  title: str
  body: str


def fetch_pr_info(pr_number: int, github_repository: str) -> GitHubPrInfo:
  gh_args = _fetch_pr_gh_args(pr_number=pr_number, github_repository=github_repository)
  gh_args = tuple(gh_args)
  logging.info("Running command: %s", subprocess.list2cmdline(gh_args))
  output_str = subprocess.check_output(gh_args, encoding="utf8", errors="replace")  # noqa: S603
  logging.info("%s", output_str.strip())
  output = json.loads(output_str)
  return GitHubPrInfo(
    title=output["title"],
    body=output["body"],
  )


def _fetch_pr_gh_args(pr_number: int, github_repository: str) -> Iterable[str]:
  yield "gh"
  yield "issue"
  yield "view"
  yield str(pr_number)
  yield "--json"
  yield "title,body"
  yield "-R"
  yield github_repository


def pr_number_from_github_ref(github_ref: str) -> int | None:
  match = re.fullmatch("refs/pull/([0-9]+)/merge", github_ref)
  return int(match.group(1)) if match else None
