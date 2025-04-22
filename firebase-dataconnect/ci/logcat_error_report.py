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
import re
import tempfile
import typing

if typing.TYPE_CHECKING:
  from _typeshed import SupportsWrite

TEST_STARTED_TOKEN = "TestRunner: started:"  # noqa: S105
TEST_STARTED_PATTERN = r"(\W|^)" + re.escape(TEST_STARTED_TOKEN) + r"\s+(?P<name>.*\S)"
TEST_FAILED_TOKEN = "TestRunner: failed:"  # noqa: S105
TEST_FAILED_PATTERN = r"(\W|^)" + re.escape(TEST_FAILED_TOKEN) + r"\s+(?P<name>.*\S)"
TEST_FINISHED_TOKEN = "TestRunner: finished:"  # noqa: S105
TEST_FINISHED_PATTERN = r"(\W|^)" + re.escape(TEST_FINISHED_TOKEN) + r"\s+(?P<name>.*\S)"


@dataclasses.dataclass
class TestResult:
  test_name: str
  output_file: pathlib.Path
  passed: bool


def main() -> None:
  args = parse_args()
  logging.basicConfig(format="%(message)s", level=args.log_level)

  if args.work_dir is None:
    work_temp_dir = tempfile.TemporaryDirectory("dd9rh9apdf")
    work_dir = pathlib.Path(work_temp_dir.name)
    logging.debug("Using temporary directory as work directory: %s", work_dir)
  else:
    work_temp_dir = None
    work_dir = args.work_dir
    logging.debug("Using specified directory as work directory: %s", work_dir)
    work_dir.mkdir(parents=True, exist_ok=True)

  logging.info("Extracting test failures from %s", args.logcat_file)
  test_results: list[TestResult] = []
  cur_test_result: TestResult | None = None
  cur_test_result_output_file: SupportsWrite[str] | None = None

  with args.logcat_file.open("rt", encoding="utf8", errors="ignore") as logcat_file_handle:
    for line in logcat_file_handle:
      test_started_match = TEST_STARTED_TOKEN in line and re.search(TEST_STARTED_PATTERN, line)
      if test_started_match:
        test_name = test_started_match.group("name")
        logging.debug('Found "Test Started" logcat line for test: %s', test_name)
        if cur_test_result_output_file is not None:
          cur_test_result_output_file.close()
        test_output_file = work_dir / f"{len(test_results)}.txt"
        cur_test_result = TestResult(test_name=test_name, output_file=test_output_file, passed=True)
        test_results.append(cur_test_result)
        cur_test_result_output_file = test_output_file.open("wt", encoding="utf8", errors="replace")

      if cur_test_result_output_file is not None:
        cur_test_result_output_file.write(line)

      test_failed_match = TEST_FAILED_TOKEN in line and re.search(TEST_FAILED_PATTERN, line)
      if test_failed_match:
        test_name = test_failed_match.group("name")
        logging.warning("FAILED TEST: %s", test_name)
        if cur_test_result is None:
          logging.warning(
            "WARNING: failed test reported without matching test started: %s", test_name
          )
        else:
          cur_test_result.passed = False

      test_finished_match = TEST_FINISHED_TOKEN in line and re.search(TEST_FINISHED_PATTERN, line)
      if test_finished_match:
        test_name = test_finished_match.group("name")
        logging.debug('Found "Test Finished" logcat line for test: %s', test_name)
        if cur_test_result_output_file is not None:
          cur_test_result_output_file.close()
        cur_test_result_output_file = None
        cur_test_result = None

  if cur_test_result_output_file is not None:
    cur_test_result_output_file.close()
  del cur_test_result_output_file

  passed_tests = [test_result for test_result in test_results if test_result.passed]
  failed_tests = [test_result for test_result in test_results if not test_result.passed]
  print_line(
    f"Found results for {len(test_results)} tests: "
    f"{len(passed_tests)} passed, {len(failed_tests)} failed"
  )

  if len(failed_tests) > 0:
    fail_number = 0
    for failed_test_result in failed_tests:
      fail_number += 1
      print_line("")
      print_line(f"Failure {fail_number}/{len(failed_tests)}: {failed_test_result.test_name}:")
      try:
        with failed_test_result.output_file.open(
          "rt", encoding="utf8", errors="ignore"
        ) as test_output_file:
          for line in test_output_file:
            print_line(line.rstrip())
      except OSError:
        logging.warning("WARNING: reading file failed: %s", failed_test_result.output_file)
        continue

  if work_temp_dir is not None:
    logging.debug("Cleaning up temporary directory: %s", work_dir)
  del work_dir
  del work_temp_dir


def print_line(line: str) -> None:
  print(line)  # noqa: T201


class ParsedArgs(typing.Protocol):
  logcat_file: pathlib.Path
  log_level: int
  work_dir: pathlib.Path | None


def parse_args() -> ParsedArgs:
  arg_parser = argparse.ArgumentParser()
  arg_parser.add_argument(
    "--logcat-file",
    required=True,
    help="The text file containing the logcat logs to scan.",
  )
  arg_parser.add_argument(
    "--work-dir",
    default=None,
    help="The directory into which to write temporary files; "
    "if not specified, use a temporary directory that is deleted "
    "when this script completes; this is primarily intended for "
    "developers of this script to use in testing and debugging",
  )
  arg_parser.add_argument(
    "--verbose",
    action="store_const",
    dest="log_level",
    default=logging.INFO,
    const=logging.DEBUG,
    help="Include debug logging output",
  )

  parse_result = arg_parser.parse_args()

  parse_result.logcat_file = pathlib.Path(parse_result.logcat_file)
  parse_result.work_dir = (
    None if parse_result.work_dir is None else pathlib.Path(parse_result.work_dir)
  )
  return typing.cast("ParsedArgs", parse_result)


if __name__ == "__main__":
  main()
