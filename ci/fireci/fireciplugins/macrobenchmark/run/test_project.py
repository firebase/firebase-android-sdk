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
import glob
import re
import shutil

from .log_decorator import LogDecorator
from .utils import execute, execute_async, generate_test_run_id
from fireci.dir_utils import chdir
from logging import getLogger, Logger, LoggerAdapter
from pathlib import Path
from typing import TypedDict

logger = getLogger('fireci.macrobenchmark')


class TestOutput(TypedDict, total=False):
  project: str
  local_reports_dir: str
  ftl_results_dirs: list[str]


class TestProject:
  def __init__(self, name: str, project_dir: Path, custom_logger: Logger | LoggerAdapter):
    self.name = name
    self.test_project_dir = project_dir
    self.logger = custom_logger

  def run_local(self, repeat: int) -> TestOutput:
    self.logger.info(f'Running test locally for {repeat} times ...')
    local_reports_dir = self.test_project_dir.joinpath('_reports')

    with chdir(self.test_project_dir):
      for index in range(repeat):
        run_id = generate_test_run_id()
        run_logger = LogDecorator(self.logger, f'run-{index}')
        run_logger.info(f'Run-{index}: {run_id}')
        execute('./gradlew', ':macrobenchmark:connectedCheck', logger=run_logger)

        reports = self.test_project_dir.rglob('build/**/*-benchmarkData.json')
        run_dir = local_reports_dir.joinpath(run_id)
        for report in reports:
          device = re.search(r'benchmark/connected/([^/]*)/', str(report)).group(1)
          device_dir = run_dir.joinpath(device)
          device_dir.mkdir(parents=True, exist_ok=True)
          shutil.copy(report, device_dir)
          run_logger.debug(f'Copied report file "{report}" to "{device_dir}"')

    self.logger.info(f'Completed all {repeat} runs, reports saved at "{local_reports_dir}"')
    return TestOutput(project=self.name, local_reports_dir=str(local_reports_dir))

  async def run_remote(self, repeat: int) -> TestOutput:
    self.logger.info(f'Running test remotely for {repeat} times ...')

    with chdir(self.test_project_dir):
      await execute_async('./gradlew', 'assemble', logger=self.logger)
      app_apk_path = glob.glob('**/app-benchmark.apk', recursive=True)[0]
      test_apk_path = glob.glob('**/macrobenchmark-benchmark.apk', recursive=True)[0]
      self.logger.info(f'App apk: "{app_apk_path}", Test apk: "{test_apk_path}"')

      async def run(index: int, results_dir: str):
        run_logger = LogDecorator(self.logger, f'run-{index}')
        run_logger.info(f'Run-{index}: {results_dir}')
        ftl_environment_variables = [
          'clearPackageData=true',
          'additionalTestOutputDir=/sdcard/Download',
          'no-isolated-storage=true',
        ]
        executable = 'gcloud'
        args = ['firebase', 'test', 'android', 'run']
        args += ['--type', 'instrumentation']
        args += ['--app', app_apk_path]
        args += ['--test', test_apk_path]
        args += ['--device', 'model=f2q,version=30,locale=en,orientation=portrait']
        args += ['--device', 'model=oriole,version=32,locale=en,orientation=portrait']
        args += ['--device', 'model=redfin,version=30,locale=en,orientation=portrait']
        args += ['--directories-to-pull', '/sdcard/Download']
        args += ['--results-bucket', 'fireescape-benchmark-results']
        args += ['--results-dir', results_dir]
        args += ['--environment-variables', ','.join(ftl_environment_variables)]
        args += ['--timeout', '30m']
        args += ['--project', 'fireescape-c4819']
        await execute_async(executable, *args, logger=run_logger)

      ftl_results_dirs = [generate_test_run_id() for _ in range(repeat)]
      runs = [run(i, ftl_results_dirs[i]) for i in range(repeat)]
      await asyncio.gather(*runs)
    self.logger.info(f'Completed all {repeat} runs, ftl results dirs: {ftl_results_dirs}')
    return TestOutput(project=self.name, ftl_results_dirs=ftl_results_dirs)
