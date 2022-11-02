# Copyright 2022 Google LLC
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

import asyncio
import click

from .analyze import analyzer
from .run import runner
from fireci import ci_command
from pathlib import Path


@ci_command(cls=click.Group)
def macrobenchmark():
  """Macrobenchmark testing command group."""
  pass


@click.option(
  '--build-only',
  is_flag=True,
  default=False,
  show_default=True,
  help='Build the test projects without running the test.'
)
@click.option(
  '--local/--remote',
  required=True,
  help='Run the test on local devices or Firebase Test Lab.'
)
@click.option(
  '--repeat',
  default=1,
  show_default=True,
  help='Number of times to repeat the test (for obtaining more data points).'
)
@click.option(
  '--output',
  type=click.Path(dir_okay=True, resolve_path=True, path_type=Path),
  default='macrobenchmark-output.json',
  show_default=True,
  help='The file for saving macrobenchmark test output. If running locally, the file contains '
       'the directory name of local test reports. If running remotely, the file contains Firebase '
       'Test Lab results directory names.'
)
@ci_command(group=macrobenchmark)
def run(build_only: bool, local: bool, repeat: int, output: Path):
  """Run macrobenchmark test."""
  asyncio.run(runner.start(build_only, local, repeat, output))


@click.option(
  '--diff-mode',
  is_flag=True,
  default=False,
  help='Compare two sets of macrobenchmark result.'
)
@click.option(
  '--ftl-results-dir',
  multiple=True,
  help='Firebase Test Lab results directory name. Can be specified multiple times.'
)
@click.option(
  '--local-reports-dir',
  type=click.Path(dir_okay=True, resolve_path=True, path_type=Path),
  help='Path to the directory of local test reports.'
)
@click.option(
  '--ctl-ftl-results-dir',
  multiple=True,
  help='FTL results dir of the control group, if running in diff mode. '
       'Can be specified multiple times.'
)
@click.option(
  '--ctl-local-reports-dir',
  type=click.Path(dir_okay=True, resolve_path=True, path_type=Path),
  help='Path to the local test reports of the control group, if running in diff mode.'
)
@click.option(
  '--exp-ftl-results-dir',
  multiple=True,
  help='FTL results dir of the experimental group, if running in diff mode. '
       'Can be specified multiple times.'
)
@click.option(
  '--exp-local-reports-dir',
  type=click.Path(dir_okay=True, resolve_path=True, path_type=Path),
  help='Path to the local test reports of the experimental group, if running in diff mode.'
)
@click.option(
  '--output-dir',
  type=click.Path(dir_okay=True, resolve_path=True, path_type=Path),
  help='The directory for saving macrobenchmark analysis result.'
)
@ci_command(group=macrobenchmark)
def analyze(
    diff_mode: bool,
    ftl_results_dir: list[str],
    local_reports_dir: Path,
    ctl_ftl_results_dir: list[str],
    ctl_local_reports_dir: Path,
    exp_ftl_results_dir: list[str],
    exp_local_reports_dir: Path,
    output_dir: Path
):
  """Analyze macrobenchmark result."""
  analyzer.start(
    diff_mode,
    ftl_results_dir,
    local_reports_dir,
    ctl_ftl_results_dir,
    ctl_local_reports_dir,
    exp_ftl_results_dir,
    exp_local_reports_dir,
    output_dir,
  )

# TODO(yifany): support of command chaining
