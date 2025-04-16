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

import argparse
import logging
import re
import sys
import typing

if typing.TYPE_CHECKING:
  from _typeshed import SupportsWrite

type ExitCode = int


def main() -> None:
  args = parse_args()
  logging.basicConfig(format="%(message)s", level=logging.INFO)
  run(args, sys.stdout)


def run(args: ParsedArgs, stdout: SupportsWrite[str]) -> None:

  # Determine the number of the pull request triggering this job, if any.


def parse_github_ref(github_ref: str) -> int | None:
  logging.info("Checking the value of github_ref for a PR number: %s", args.github_ref)
  match = re.fullmatch("refs/pull/([0-9]+)/merge", args.github_ref)
  if not match:
    return None



class ParsedArgs(typing.Protocol):
  github_ref: str
  github_repository: str
  github_event_name: str
  pr_body_github_issue_key: str
  github_issue_for_scheduled_run: str


def parse_args() -> ParsedArgs:
  arg_parser = argparse.ArgumentParser()
  arg_parser.add_argument(
    "--github-ref",
    required=True,
    help="The value of ${{ github.ref }} in the workflow",
  )
  arg_parser.add_argument(
    "--github-repository",
    required=True,
    help="The value of ${{ github.repository }} in the workflow",
  )
  arg_parser.add_argument(
    "--github-event-name",
    required=True,
    help="The value of ${{ github.event_name }} in the workflow",
  )
  arg_parser.add_argument(
    "--pr-body-github-issue-key",
    required=True,
    help="The string to search for in a Pull Request body to determine the GitHub Issue number "
    "for commenting. For example, if the value is 'foobar' then this script searched a PR "
    "body for a line of the form 'foobar=NNNN' where 'NNNN' is the GitHub issue number",
  )
  arg_parser.add_argument(
    "--github-issue-for-scheduled-run",
    required=True,
    help="The GitHub Issue number to use for commenting when --github-event-name is 'schedule'",
  )

  parse_result = arg_parser.parse_args()
  return typing.cast("ParsedArgs", parse_result)


if __name__ == "__main__":
  main()
