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

import asyncio
import glob
import json
import logging
import os
import random
import re
import shutil
import sys
import tempfile
import uuid

import click
import numpy
import pystache
import yaml
from google.cloud import storage

from fireci import ci_command
from fireci import ci_utils
from fireci import uploader
from fireci.dir_utils import chdir

_logger = logging.getLogger('fireci.macrobenchmark')


@click.option(
  '--build-only/--no-build-only',
  default=False,
  help='Whether to only build tracing test apps or to also run them on FTL afterwards'
)
@ci_command()
def macrobenchmark(build_only):
  """Measures app startup times for Firebase SDKs."""
  asyncio.run(_launch_macrobenchmark_test(build_only))


async def _launch_macrobenchmark_test(build_only):
  _logger.info('Starting macrobenchmark test...')

  artifact_versions = await _assemble_all_artifacts()
  _logger.info(f'Artifact versions: {artifact_versions}')

  test_dir = await _prepare_test_directory()
  _logger.info(f'Directory for test apps: {test_dir}')

  config = await _process_config_yaml()
  _logger.info(f'Processed yaml configurations: {config}')

  tests = [MacrobenchmarkTest(app, artifact_versions, os.getcwd(), test_dir) for app in config['test-apps']]

  _logger.info(f'Building {len(tests)} macrobenchmark test apps...')
  # TODO(yifany): investigate why it is much slower with asyncio.gather
  #   - on corp workstations (9 min) than M1 macbook pro (3 min)
  #   - with gradle 7.5.1 (9 min) than gradle 6.9.2 (5 min)
  # await asyncio.gather(*[x.build() for x in tests])
  for test in tests:
    await test.build()

  if not build_only:
    _logger.info(f'Submitting {len(tests)} tests to Firebase Test Lab...')
    results = await asyncio.gather(*[x.test() for x in tests], return_exceptions=True)
    await _post_processing(results)

  _logger.info('Macrobenchmark test finished.')


async def _assemble_all_artifacts():
  await (await asyncio.create_subprocess_exec('./gradlew', 'assembleAllForSmokeTests')).wait()

  with open('build/m2repository/changed-artifacts.json') as json_file:
    artifacts = json.load(json_file)
  return dict(_artifact_key_version(x) for x in artifacts['headGit'])


def _artifact_key_version(artifact):
  group_id, artifact_id, version = artifact.split(':')
  return f'{group_id}:{artifact_id}', version


async def _process_config_yaml():
  with open('health-metrics/benchmark/config.yaml') as yaml_file:
    config = yaml.safe_load(yaml_file)
    for app in config['test-apps']:
      app['plugins'] = app.get('plugins', [])
      app['traces'] = app.get('traces', [])
      app['plugins'].extend(config['common-plugins'])
      app['traces'].extend(config['common-traces'])

    # Adding an empty android app for baseline comparison
    config['test-apps'].insert(0, {'sdk': 'baseline', 'name': 'baseline'})
    return config


async def _prepare_test_directory():
  test_dir = tempfile.mkdtemp(prefix='benchmark-test-')

  # Required for creating gradle wrapper, as the dir is not defined in the root settings.gradle
  open(os.path.join(test_dir, 'settings.gradle'), 'w').close()

  command = ['./gradlew', 'wrapper', '--gradle-version', '7.5.1', '--project-dir', test_dir]
  await (await asyncio.create_subprocess_exec(*command)).wait()

  return test_dir


async def _post_processing(results):
  _logger.info(f'Macrobenchmark results: {results}')

  if os.getenv('CI') is None:
    _logger.info('Running locally. Results upload skipped.')
    return

  # Upload successful measurements to the metric service
  measurements = []
  for result in results:
    if not isinstance(result, Exception):
      measurements.extend(result)

  log = ci_utils.ci_log_link()
  test_report = {'benchmarks': measurements, 'log': log}

  metrics_service_url = 'https://api.firebase-sdk-health-metrics.com'
  access_token = ci_utils.gcloud_identity_token()
  uploader.post_report(test_report, metrics_service_url, access_token, 'macrobenchmark')

  # Raise exceptions for failed measurements
  if any(map(lambda x: isinstance(x, Exception), results)):
    _logger.error(f'Exceptions: {[x for x in results if isinstance(x, Exception)]}')
    raise click.ClickException('Macrobenchmark test failed with above errors.')


class MacrobenchmarkTest:
  """Builds the test based on configurations and runs the test on FTL."""
  def __init__(
      self,
      test_app_config,
      artifact_versions,
      repo_root_dir,
      test_dir,
      logger=_logger
  ):
    self.test_app_config = test_app_config
    self.artifact_versions = artifact_versions
    self.repo_root_dir = repo_root_dir
    self.test_dir = test_dir
    self.logger = MacrobenchmarkLoggerAdapter(logger, test_app_config['sdk'])
    self.test_app_dir = os.path.join(test_dir, test_app_config['name'])
    self.test_results_bucket = 'fireescape-benchmark-results'
    self.test_results_dir = str(uuid.uuid4())
    self.gcs_client = storage.Client()

  async def build(self):
    """Creates test app project and assembles app and test apks."""
    await self._create_benchmark_projects()
    await self._assemble_benchmark_apks()

  async def test(self):
    """Runs benchmark tests on FTL and fetches FTL results from GCS."""
    await self._execute_benchmark_tests()
    return await self._aggregate_benchmark_results()

  async def _create_benchmark_projects(self):
    app_name = self.test_app_config['name']
    self.logger.info(f'Creating test app "{app_name}"...')

    self.logger.info(f'Copying project template files into "{self.test_app_dir}"...')
    template_dir = os.path.join(self.repo_root_dir, 'health-metrics/benchmark/template')
    shutil.copytree(template_dir, self.test_app_dir)

    self.logger.info(f'Copying gradle wrapper binary into "{self.test_app_dir}"...')
    shutil.copy(os.path.join(self.test_dir, 'gradlew'), self.test_app_dir)
    shutil.copy(os.path.join(self.test_dir, 'gradlew.bat'), self.test_app_dir)
    shutil.copytree(os.path.join(self.test_dir, 'gradle'), os.path.join(self.test_app_dir, 'gradle'))

    with chdir(self.test_app_dir):
      mustache_context = await self._prepare_mustache_context()
      renderer = pystache.Renderer()
      mustaches = glob.glob('**/*.mustache', recursive=True)
      for mustache in mustaches:
        self.logger.info(f'Processing template file: {mustache}')
        result = renderer.render_path(mustache, mustache_context)
        original_name = mustache.removesuffix('.mustache')
        with open(original_name, 'w') as file:
          file.write(result)

  async def _assemble_benchmark_apks(self):
    with chdir(self.test_app_dir):
      await self._exec_subprocess('./gradlew', ['assemble'])

  async def _execute_benchmark_tests(self):
    app_apk_path = glob.glob(f'{self.test_app_dir}/**/app-benchmark.apk', recursive=True)[0]
    test_apk_path = glob.glob(f'{self.test_app_dir}/**/macrobenchmark-benchmark.apk', recursive=True)[0]

    self.logger.info(f'App apk: {app_apk_path}')
    self.logger.info(f'Test apk: {test_apk_path}')

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
    args += ['--device', 'model=oriole,version=32,locale=en,orientation=portrait']
    args += ['--directories-to-pull', '/sdcard/Download']
    args += ['--results-bucket', f'gs://{self.test_results_bucket}']
    args += ['--results-dir', self.test_results_dir]
    args += ['--environment-variables', ','.join(ftl_environment_variables)]
    args += ['--timeout', '30m']
    args += ['--project', 'fireescape-c4819']

    await self._exec_subprocess(executable, args)

  async def _prepare_mustache_context(self):
    mustache_context = {
      'm2repository': os.path.join(self.repo_root_dir, 'build/m2repository'),
      'plugins': self.test_app_config.get('plugins', []),
      'traces': self.test_app_config.get('traces', []),
      'dependencies': [],
    }

    if 'dependencies' in self.test_app_config:
      for dep in self.test_app_config['dependencies']:
        if '@' in dep:
          key, version = dep.split('@', 1)
          dependency = {'key': key, 'version': version}
        else:
          dependency = {'key': dep, 'version': self.artifact_versions[dep]}
        mustache_context['dependencies'].append(dependency)

    return mustache_context

  async def _aggregate_benchmark_results(self):
    results = []
    blobs = self.gcs_client.list_blobs(self.test_results_bucket, prefix=self.test_results_dir)
    files = [x for x in blobs if re.search(r'sdcard/Download/[^/]*\.json', x.name)]
    for file in files:
      device = re.search(r'([^/]*)/artifacts/', file.name).group(1)
      benchmarks = json.loads(file.download_as_bytes())['benchmarks']
      for benchmark in benchmarks:
        method = benchmark['name']
        clazz = benchmark['className'].split('.')[-1]
        runs = benchmark['metrics']['timeToInitialDisplayMs']['runs']
        results.append({
          'sdk': self.test_app_config['sdk'],
          'device': device,
          'name': f'{clazz}.{method}',
          'min': min(runs),
          'max': max(runs),
          'p50': numpy.percentile(runs, 50),
          'p90': numpy.percentile(runs, 90),
          'p99': numpy.percentile(runs, 99),
          'unit': 'ms',
        })
    self.logger.info(f'Benchmark results: {results}')
    return results

  async def _exec_subprocess(self, executable, args):
    command = " ".join([executable, *args])
    self.logger.info(f'Executing command: "{command}"...')

    proc = await asyncio.subprocess.create_subprocess_exec(
      executable,
      *args,
      stdout=asyncio.subprocess.PIPE,
      stderr=asyncio.subprocess.PIPE
    )
    await asyncio.gather(
      self._stream_output(executable, proc.stdout),
      self._stream_output(executable, proc.stderr)
    )

    await proc.communicate()
    if proc.returncode == 0:
      self.logger.info(f'"{command}" finished.')
    else:
      message = f'"{command}" exited with return code {proc.returncode}.'
      self.logger.error(message)
      raise click.ClickException(message)

  async def _stream_output(self, executable, stream: asyncio.StreamReader):
    async for line in stream:
      self.logger.info(f'[{executable}] {line.decode("utf-8").strip()}')


class MacrobenchmarkLoggerAdapter(logging.LoggerAdapter):
  """Decorates log messages for a sdk to make them more distinguishable."""

  reset_code = '\x1b[m'

  @staticmethod
  def random_color_code():
    code = random.randint(16, 231)  # https://en.wikipedia.org/wiki/ANSI_escape_code#8-bit
    return f'\x1b[38;5;{code}m'

  def __init__(self, logger, sdk_name, color_code=None):
    super().__init__(logger, {})
    self.sdk_name = sdk_name
    self.color_code = self.random_color_code() if color_code is None else color_code

  def process(self, msg, kwargs):
    colored = f'{self.color_code}[{self.sdk_name}]{self.reset_code} {msg}'
    uncolored = f'[{self.sdk_name}] {msg}'
    return colored if sys.stderr.isatty() else uncolored, kwargs
