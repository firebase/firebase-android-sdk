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
import dataclasses
import logging
import pathlib
import subprocess
import tempfile
import typing

from util import fetch_pr_info, pr_number_from_github_ref

if typing.TYPE_CHECKING:
  from collections.abc import Iterable, Sequence


def main() -> None:
  args = parse_args()
  logging.basicConfig(format="%(message)s", level=logging.INFO)

  message_lines = tuple(generate_message_lines(args))

  issue_url = f"{args.github_repository_html_url}/issues/{args.github_issue}"
  logging.info("Posting the following comment to GitHub Issue %s", issue_url)
  for line in message_lines:
    logging.info(line)

  message_bytes = "\n".join(message_lines).encode("utf8", errors="replace")
  with tempfile.TemporaryDirectory() as tempdir_path:
    message_file = pathlib.Path(tempdir_path) / "message_text.txt"
    message_file.write_bytes(message_bytes)
    post_github_issue_comment(
      issue_number=args.github_issue,
      body_file=message_file,
      github_repository=args.github_repository,
    )


def generate_message_lines(data: ParsedArgs) -> Iterable[str]:
  logging.info("Extracting PR number from string: %s", data.github_ref)
  pr_number = pr_number_from_github_ref(data.github_ref)
  pr_title: str | None
  if pr_number is None:
    logging.info("No PR number extracted")
    pr_title = None
  else:
    pr_info = fetch_pr_info(
      pr_number=pr_number,
      github_repository=data.github_repository,
    )
    pr_title = pr_info.title

  if pr_number is not None:
    yield (
      f"Posting from Pull Request {data.github_repository_html_url}/pull/{pr_number} ({pr_title})"
    )

  yield f"Result of workflow '{data.github_workflow}' at {data.github_sha}:"

  for job_result in data.job_results:
    result_symbol = "✅" if job_result.result == "success" else "❌"
    yield f"  - {job_result.job_id}: {result_symbol} {job_result.result}"

  yield ""
  yield f"{data.github_repository_html_url}/actions/runs/{data.github_run_id}"

  yield ""
  yield (
    f"event_name=`{data.github_event_name}` "
    f"run_id=`{data.github_run_id}` "
    f"run_number=`{data.github_run_number}` "
    f"run_attempt=`{data.github_run_attempt}`"
  )


def post_github_issue_comment(
  issue_number: int, body_file: pathlib.Path, github_repository: str
) -> None:
  gh_args = post_issue_comment_gh_args(
    issue_number=issue_number, body_file=body_file, github_repository=github_repository
  )
  gh_args = tuple(gh_args)
  logging.info("Running command: %s", subprocess.list2cmdline(gh_args))
  subprocess.check_call(gh_args)  # noqa: S603


def post_issue_comment_gh_args(
  issue_number: int, body_file: pathlib.Path, github_repository: str
) -> Iterable[str]:
  yield "gh"
  yield "issue"

  yield "comment"
  yield str(issue_number)
  yield "--body-file"
  yield str(body_file)
  yield "-R"
  yield github_repository


@dataclasses.dataclass(frozen=True)
class JobResult:
  job_id: str
  result: str

  @classmethod
  def parse(cls, s: str) -> JobResult:
    colon_index = s.find(":")
    if colon_index < 0:
      raise ParseError(
        "no colon (:) character found in job result specification, "
        "which is required to delimit the job ID from the job result"
      )
    job_id = s[:colon_index]
    job_result = s[colon_index + 1 :]
    return cls(job_id=job_id, result=job_result)


class ParsedArgs(typing.Protocol):
  job_results: Sequence[JobResult]
  github_issue: int
  github_repository: str
  github_event_name: str
  github_ref: str
  github_workflow: str
  github_sha: str
  github_repository_html_url: str
  github_run_id: str
  github_run_number: str
  github_run_attempt: str


class ParseError(Exception):
  pass


def parse_args() -> ParsedArgs:
  arg_parser = argparse.ArgumentParser()
  arg_parser.add_argument(
    "job_results",
    nargs="+",
    help="The results of the jobs in question, of the form "
    "'job-id:${{ needs.job-id.result }}' where 'job-id' is the id of the corresponding job "
    "in the 'needs' section of the job.",
  )
  arg_parser.add_argument(
    "--github-issue",
    type=int,
    required=True,
    help="The GitHub Issue number to which to post a comment",
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
    "--github-ref",
    required=True,
    help="The value of ${{ github.ref }} in the workflow",
  )
  arg_parser.add_argument(
    "--github-workflow",
    required=True,
    help="The value of ${{ github.workflow }} in the workflow",
  )
  arg_parser.add_argument(
    "--github-sha",
    required=True,
    help="The value of ${{ github.sha }} in the workflow",
  )
  arg_parser.add_argument(
    "--github-repository-html-url",
    required=True,
    help="The value of ${{ github.event.repository.html_url }} in the workflow",
  )
  arg_parser.add_argument(
    "--github-run-id",
    required=True,
    help="The value of ${{ github.run_id }} in the workflow",
  )
  arg_parser.add_argument(
    "--github-run-number",
    required=True,
    help="The value of ${{ github.run_number }} in the workflow",
  )
  arg_parser.add_argument(
    "--github-run-attempt",
    required=True,
    help="The value of ${{ github.run_attempt }} in the workflow",
  )

  parse_result = arg_parser.parse_args()

  job_results: list[JobResult] = []
  for job_result_str in parse_result.job_results:
    try:
      job_result = JobResult.parse(job_result_str)
    except ParseError as e:
      arg_parser.error(f"invalid job result specification: {job_result_str} ({e})")
      typing.assert_never("the line above should have raised an exception")
    else:
      job_results.append(job_result)
  parse_result.job_results = tuple(job_results)

  return typing.cast("ParsedArgs", parse_result)


if __name__ == "__main__":
  main()
