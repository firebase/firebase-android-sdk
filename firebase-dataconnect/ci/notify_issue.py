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

import argparse
import dataclasses
import logging
import sys
import typing
from collections.abc import Sequence

type ExitCode = int

def main(args: Sequence[str], stdout: typing.TextIO, stderr: typing.TextIO) -> ExitCode:
  try:
    parsed_args = parse_args(args[0], args[1:], stdout)
  except MyArgumentParser.Error as e:
    if e.exit_code != 0:
      print(f"ERROR: invalid command-line arguments: {e}", file=stderr)
      print(f"Run with --help for help", file=stderr)
    return e.exit_code

  return 0

@dataclasses.dataclass(frozen=True)
class GetIssueNumberCommand:
  github_ref: str
  github_event_name: str
  default_github_issue: int

@dataclasses.dataclass(frozen=True)
class ParsedArgs:
  log_level: int
  command: GetIssueNumberCommand

class MyArgumentParser(argparse.ArgumentParser):

  def __init__(self, prog: str, stdout: typing.TextIO) -> None:
    super().__init__(prog=prog, usage="%(prog)s <command> [options]")
    self.stdout = stdout

  @typing.override
  def exit(self, status: int = 0, message: str | None = None) -> typing.Never:
    raise self.Error(exit_code=status, message=message)

  @typing.override
  def error(self, message: str) -> typing.Never:
    self.exit(2, message)

  @typing.override
  def print_usage(self, file: typing.TextIO | None = None) -> None:
    file = file if file is not None else self.stdout
    super().print_usage(file)

  @typing.override
  def print_help(self, file: typing.TextIO | None =None) -> None:
    file = file if file is not None else self.stdout
    super().print_help(file)

  class Error(Exception):
    def __init__(self, exit_code: ExitCode, message: str | None) -> None:
      super().__init__(message)
      self.exit_code = exit_code


def parse_args(prog: str, args: Sequence[str], stdout: typing.TextIO) -> ExitCode:
  arg_parser = MyArgumentParser(prog, stdout)
  parsed_args = arg_parser.parse_args(args)

if __name__ == "__main__":
  try:
    exit_code = main(sys.argv, sys.stdout, sys.stderr)
  except KeyboardInterrupt:
    print("ERROR: application terminated by keyboard interrupt", file=sys.stderr)
    exit_code = 1

  sys.exit(exit_code)
