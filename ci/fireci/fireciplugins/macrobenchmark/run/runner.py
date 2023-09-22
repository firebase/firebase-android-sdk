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
from typing import Dict, List, Set


logger = logging.getLogger('fireci.macrobenchmark')


async def start(
    build_only: bool,
    local: bool,
    repeat: int,
    output: Path,
    changed_modules_file: Path = None
):
  logger.info('Starting macrobenchmark test ...')

  config = _process_config_yaml()
  product_versions = _assemble_all_products()
  test_dir = _prepare_test_directory()
  changed_traces = _process_changed_modules(changed_modules_file)
  template_project_dir = Path('health-metrics/benchmark/template')

  test_projects = [
    TestProjectBuilder(
      test_config,
      test_dir,
      template_project_dir,
      product_versions,
      changed_traces,
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


def _process_changed_modules(path: Path) -> List[str]:
  trace_names = {
    ":appcheck": ["fire-app-check"],
    ":firebase-abt": ["fire-abt"],
    ":firebase-appdistribution": ["fire-appdistribution"],
    ":firebase-config": ["fire-rc"],
    ":firebase-common": ["Firebase", "ComponentDiscovery", "Runtime"],
    ":firebase-components": ["Firebase", "ComponentDiscovery", "Runtime"],
    ":firebase-database": ["fire-rtdb"],
    ":firebase-datatransport": ["fire-transport"],
    ":firebase-dynamic-links": ["fire-dl"],
    ":firebase-crashlytics": ["fire-cls"],
    ":firebase-crashlytics-ndk": ["fire-cls"],
    ":firebase-firestore": ["fire-fst"],
    ":firebase-functions": ["fire-fn"],
    ":firebase-inappmessaging": ["fire-fiam"],
    ":firebase-inappmessaging-display": ["fire-fiamd"],
    ":firebase-installations": ["fire-installations"],
    ":firebase-installations-interop": ["fire-installations"],
    ":firebase-messaging": ["fire-fcm"],
    ":firebase-messaging-directboot": ["fire-fcm"],
    ":firebase-ml-modeldownloader": ["firebase-ml-modeldownloader"],
    ":firebase-perf": ["fire-perf"],
    ":firebase-sessions": ["fire-sessions"],
    ":firebase-storage": ["fire-gcs"],
    ":transport": ["fire-transport"],
  }

  results: Set[str] = set()
  if path:
    with open(path) as changed_modules_file:
      changed_modules = json.load(changed_modules_file)
      for module in changed_modules:
        for product in trace_names:
          if module.startswith(product):
            results.update(trace_names[product])
  logger.info(f"Extracted changed traces {results} from {path}")
  return list(results)
