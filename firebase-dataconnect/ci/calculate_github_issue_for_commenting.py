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
import pathlib
import re
import typing

from util import fetch_pr_info, pr_number_from_github_ref


def main() -> None:
  args = parse_args()
  logging.basicConfig(format="%(message)s", level=logging.INFO)

  github_issue = calculate_github_issue(
    github_event_name=args.github_event_name,
    github_issue_for_scheduled_run=args.github_issue_for_scheduled_run,
    github_ref=args.github_ref,
    github_repository=args.github_repository,
    pr_body_github_issue_key=args.pr_body_github_issue_key,
  )

  issue_file_text = "" if github_issue is None else str(github_issue)
  logging.info("Writing '%s' to %s", issue_file_text, args.issue_output_file)
  args.issue_output_file.write_text(issue_file_text, encoding="utf8", errors="replace")


def calculate_github_issue(
  github_event_name: str,
  github_issue_for_scheduled_run: int,
  github_ref: str,
  github_repository: str,
  pr_body_github_issue_key: str,
) -> int | None:
  if github_event_name == "schedule":
    logging.info(
      "GitHub Event name is: %s; using GitHub Issue: %s",
      github_event_name,
      github_issue_for_scheduled_run,
    )
    return github_issue_for_scheduled_run

  logging.info("Extracting PR number from string: %s", github_ref)
  pr_number = pr_number_from_github_ref(github_ref)
  if pr_number is None:
    logging.info("No PR number extracted")
    return None
  typing.assert_type(pr_number, int)

  logging.info("PR number extracted: %s", pr_number)
  logging.info("Loading body text of PR: %s", pr_number)
  pr_info = fetch_pr_info(
    pr_number=pr_number,
    github_repository=github_repository,
  )

  logging.info("Looking for GitHub Issue key in PR body text: %s=NNNN", pr_body_github_issue_key)
  github_issue = github_issue_from_pr_body(
    pr_body=pr_info.body,
    issue_key=pr_body_github_issue_key,
  )

  if github_issue is None:
    logging.info("No GitHub Issue key found in PR body")
    return None
  typing.assert_type(github_issue, int)

  logging.info("Found GitHub Issue key in PR body: %s", github_issue)
  return github_issue


def github_issue_from_pr_body(pr_body: str, issue_key: str) -> int | None:
  expr = re.compile(r"\s*" + re.escape(issue_key) + r"\s*=\s*(\d+)\s*")
  for line in pr_body.splitlines():
    match = expr.fullmatch(line.strip())
    if match:
      return int(match.group(1))
  return None


class ParsedArgs(typing.Protocol):
  issue_output_file: pathlib.Path
  github_ref: str
  github_repository: str
  github_event_name: str
  pr_body_github_issue_key: str
  github_issue_for_scheduled_run: int


def parse_args() -> ParsedArgs:
  arg_parser = argparse.ArgumentParser()
  arg_parser.add_argument(
    "--issue-output-file",
    required=True,
    help="The file to which to write the calculated issue number"
    "if no issue number was found, then an empty file will be written",
  )
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
    type=int,
    required=True,
    help="The GitHub Issue number to use for commenting when --github-event-name is 'schedule'",
  )

  parse_result = arg_parser.parse_args()
  parse_result.issue_output_file = pathlib.Path(parse_result.issue_output_file)
  return typing.cast("ParsedArgs", parse_result)


if __name__ == "__main__":
  main()
