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
import tempfile
import yaml

from .test_project_builder import TestProjectBuilder
from .utils import execute
from pathlib import Path
from typing import Dict


logger = logging.getLogger('fireci.macrobenchmark')


async def start(build_only: bool, local: bool, repeat: int, output: Path):
  logger.info('Starting macrobenchmark test ...')

  config = _process_config_yaml()
  product_versions = _assemble_all_products()
  test_dir = _prepare_test_directory()
  template_project_dir = Path('health-metrics/benchmark/template')

  test_projects = [
    TestProjectBuilder(
      test_config,
      test_dir,
      template_project_dir,
      product_versions,
    ).build() for test_config in config['test-apps']]

  if not build_only:
    if local:
      for test_project in test_projects:
        test_project.run_local(repeat)
    else:
      remote_runs = [test_project.run_remote(repeat) for test_project in test_projects]
      results = await asyncio.gather(*remote_runs, return_exceptions=True)
      test_outputs = [x for x in results if not isinstance(x, Exception)]
      exceptions = [x for x in results if isinstance(x, Exception)]

      with open(output, 'w') as file:
        json.dump(test_outputs, file)
        logger.info(f'Output of remote testing saved to: {output}')

      if exceptions:
        logger.error(f'Exceptions occurred: {exceptions}')
      for test_output in test_outputs:
        if test_output['exceptions']:
          logger.error(f'Exceptions occurred: {test_output["exceptions"]}')

      if exceptions or any(test_output['exceptions'] for test_output in test_outputs):
        raise click.ClickException('Macrobenchmark test failed with above exceptions')

  logger.info(f'Completed macrobenchmark test successfully')


def _assemble_all_products() -> Dict[str, str]:
  execute('./gradlew', 'assembleAllForSmokeTests', logger=logger)

  product_versions: Dict[str, str] = {}
  with open('build/m2repository/changed-artifacts.json') as json_file:
    artifacts = json.load(json_file)
    for artifact in artifacts['headGit']:
      group_id, artifact_id, version = artifact.split(':')
      product_versions[f'{group_id}:{artifact_id}'] = version

  logger.info(f'Product versions: {product_versions}')
  return product_versions


def _process_config_yaml():
  with open('health-metrics/benchmark/config.yaml') as yaml_file:
    config = yaml.safe_load(yaml_file)
    for app in config['test-apps']:
      app['plugins'] = app.get('plugins', [])
      app['traces'] = app.get('traces', [])
      app['plugins'].extend(config['common-plugins'])
      app['traces'].extend(config['common-traces'])
    return config


def _prepare_test_directory() -> Path:
  test_dir = tempfile.mkdtemp(prefix='benchmark-test-')
  logger.info(f'Temporary test directory created at: {test_dir}')
  return Path(test_dir)
