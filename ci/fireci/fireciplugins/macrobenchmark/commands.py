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
import json
import logging

from .analyze import analyzer
from .run import runner
from fireci import ci_command, ci_utils, uploader
from pathlib import Path
from typing import List

logger = logging.getLogger('fireci.macrobenchmark')


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
  default=True,
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
  help='The file for saving macrobenchmark test output if running on Firebase Test Lab.'
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
    ftl_results_dir: List[str],
    local_reports_dir: Path,
    ctl_ftl_results_dir: List[str],
    ctl_local_reports_dir: Path,
    exp_ftl_results_dir: List[str],
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


@click.option(
  '--pull-request/--push',
  required=True,
  help='Whether the test is running for a pull request or a push event.'
)
@click.option(
  '--changed-modules-file',
  type=click.Path(resolve_path=True, path_type=Path),
  help='Contains a list of changed modules in the current pull request.'
)
@click.option(
  '--repeat',
  default=10,
  show_default=True,
  help='Number of times to repeat the test (for obtaining more data points).'
)
@ci_command(group=macrobenchmark)
def ci(pull_request: bool, changed_modules_file: Path, repeat: int):
  """Run tests in CI and upload results to the metric service."""

  output_path = Path("macrobenchmark-test-output.json")
  exception = None

  try:
    if pull_request:
      asyncio.run(
        runner.start(
          build_only=False,
          local=False,
          repeat=repeat,
          output=output_path,
          changed_modules_file=changed_modules_file,
        )
      )
    else:
      asyncio.run(runner.start(build_only=False, local=False, repeat=repeat, output=output_path))
  except Exception as e:
    logger.error(f"Error: {e}")
    exception = e

  with open(output_path) as output_file:
    output = json.load(output_file)
    project_name = 'test-changed' if pull_request else 'test-all'
    ftl_dirs = list(filter(lambda x: x['project'] == project_name, output))[0]['successful_runs']
    ftl_bucket_name = 'fireescape-benchmark-results'

    log = ci_utils.ci_log_link()
    ftl_results = list(map(lambda x: {'bucket': ftl_bucket_name, 'dir': x}, ftl_dirs))
    startup_time_data = {'log': log, 'ftlResults': ftl_results}

    if ftl_results:
      metric_service_url = 'https://metric-service-tv5rmd4a6q-uc.a.run.app'
      access_token = ci_utils.gcloud_identity_token()
      uploader.post_report(
        test_report=startup_time_data,
        metrics_service_url=metric_service_url,
        access_token=access_token,
        metric_type='startup-time',
        asynchronous=True
      )

  if exception:
    raise exception

# TODO(yifany): support of command chaining
