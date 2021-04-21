# Copyright 2021 Google LLC
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

import contextlib
import glob
import json
import logging
import os
import pystache
import shutil
import subprocess
import yaml

from fireci import ci_command
from fireci import gradle

_logger = logging.getLogger('fireci.startup_time')


@ci_command()
def startup_time():
  """Measures app startup times for Firebase SDKs."""

  gradle.run('assembleAllForSmokeTests')
  artifact_versions = _parse_artifacts()

  os.chdir('macrobenchmark')

  config = _parse_config_yaml()
  _populate_test_apps(config, artifact_versions)

  gradle.run('assemble', 'assembleAndroidTest')

  _run_all_benchmark_tests()


def _parse_artifacts():
  with open('build/m2repository/changed-artifacts.json', 'r') as json_file:
    artifacts = json.load(json_file)
    return dict(_artifact_key_version(x) for x in artifacts['headGit'])


def _artifact_key_version(artifact):
  group_id, artifact_id, version = artifact.split(':')
  return f'{group_id}:{artifact_id}', version


def _parse_config_yaml():
  with open('config.yaml', 'r') as yaml_file:
    return yaml.safe_load(yaml_file)


def _populate_test_apps(config, artifact_versions):
  for sdk, test_app_configs in config.items():
    _logger.info(f'Creating test apps for "{sdk}" ...')
    for cfg in test_app_configs:
        _create_test_app(cfg, artifact_versions)


def _create_test_app(cfg, artifact_versions):
  _logger.info(f'Creating test app "{cfg["name"]}" with app-id "{cfg["application-id"]}" ...')

  mustache_context = {
    'application-id': cfg['application-id'],
    'plugins': cfg['plugins'] if 'plugins' in cfg else False,
    'dependencies': [
      {
        'key': x,
        'version': artifact_versions[x]
      } for x in cfg['dependencies']
    ] if 'dependencies' in cfg else False,
  }

  output_path = f'test-apps/{cfg["name"]}'

  shutil.copytree('template', output_path)

  with chdir(output_path):
    renderer = pystache.Renderer()
    mustaches = glob.glob('**/*.mustache', recursive=True)
    for mustache in mustaches:
      result = renderer.render_path(mustache, mustache_context)
      # TODO(yifany): mustache.removesuffix('.mustache') with python 3.9
      original_name = mustache[:-9]
      with open(original_name, 'w') as file:
        file.write(result)


def _run_all_benchmark_tests():
  test_apps = os.listdir('test-apps')
  benchmark_tests = [
    {
      'name': test_app,
      'app_apk_path': glob.glob(f'test-apps/{test_app}/app/**/*.apk', recursive=True)[0],
      'test_apk_path': glob.glob(f'test-apps/{test_app}/benchmark/**/*.apk', recursive=True)[0],
    } for test_app in test_apps
  ]
  for test in benchmark_tests:
    _logger.info(f'Running macrobenchmark test for "{test["name"]}" ...')
    _run_on_ftl(test['app_apk_path'], test['test_apk_path'])


def _run_on_ftl(app_apk_path, test_apk_path, results_bucket=None):
  ftl_environment_variables = [
      'clearPackageData=true',
      'additionalTestOutputDir=/sdcard/Download',
      'no-isolated-storage=true',
  ]
  commands = ['gcloud', 'firebase', 'test', 'android', 'run']
  commands += ['--type', 'instrumentation']
  commands += ['--app', app_apk_path]
  commands += ['--test', test_apk_path]
  commands += ['--device', 'model=flame,version=30,locale=en,orientation=portrait']
  commands += ['--directories-to-pull', '/sdcard/Download']
  commands += ['--results-bucket', results_bucket] if results_bucket else []
  commands += ['--environment-variables', ','.join(ftl_environment_variables)]
  commands += ['--timeout', '30m']
  commands += ['--project', 'fireescape-c4819']
  return subprocess.run(commands, stdout=subprocess.PIPE, check=True)


# TODO(yifany): same as the one in fierperf.py
@contextlib.contextmanager
def chdir(directory):
  """Change working dir to `directory` and restore to original afterwards."""
  original_dir = os.getcwd()
  abs_directory = os.path.join(original_dir, directory)
  _logger.debug(f'Changing directory from {original_dir} to: {abs_directory} ...')
  os.chdir(directory)
  try:
    yield
  finally:
    _logger.debug(f'Restoring directory to: {original_dir} ...')
    os.chdir(original_dir)
