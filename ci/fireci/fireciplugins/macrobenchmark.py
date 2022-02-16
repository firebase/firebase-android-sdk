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
import uuid

import click
import numpy
import pystache
import yaml
from google.cloud import storage

from fireci import ci_command
from fireci.dir_utils import chdir
from fireci import prow_utils
from fireci import uploader

_logger = logging.getLogger('fireci.macrobenchmark')


@ci_command()
def macrobenchmark():
  """Measures app startup times for Firebase SDKs."""
  asyncio.run(_launch_macrobenchmark_test())


async def _launch_macrobenchmark_test():
  _logger.info('Starting macrobenchmark test...')

  artifact_versions, config, _, _ = await asyncio.gather(
    _parse_artifact_versions(),
    _parse_config_yaml(),
    _create_gradle_wrapper(),
    _copy_google_services(),
  )

  _logger.info(f'Artifact versions: {artifact_versions}')

  with chdir('health-metrics/macrobenchmark'):
    runners = [MacrobenchmarkTest(k, v, artifact_versions) for k, v in config.items()]
    results = await asyncio.gather(*[x.run() for x in runners], return_exceptions=True)

  await _post_processing(results)

  _logger.info('Macrobenchmark test finished.')


async def _parse_artifact_versions():
  proc = await asyncio.subprocess.create_subprocess_exec('./gradlew', 'assembleAllForSmokeTests')
  await proc.wait()

  with open('build/m2repository/changed-artifacts.json') as json_file:
    artifacts = json.load(json_file)
  return dict(_artifact_key_version(x) for x in artifacts['headGit'])


def _artifact_key_version(artifact):
  group_id, artifact_id, version = artifact.split(':')
  return f'{group_id}:{artifact_id}', version


async def _parse_config_yaml():
  with open('health-metrics/macrobenchmark/config.yaml') as yaml_file:
    return yaml.safe_load(yaml_file)


async def _create_gradle_wrapper():
  with open('health-metrics/macrobenchmark/settings.gradle', 'w'):
    pass

  proc = await asyncio.subprocess.create_subprocess_exec(
    './gradlew',
    'wrapper',
    '--gradle-version',
    '6.9',
    '--project-dir',
    'health-metrics/macrobenchmark'
  )
  await proc.wait()


async def _copy_google_services():
  if 'FIREBASE_CI' in os.environ:
    src = os.environ['FIREBASE_GOOGLE_SERVICES_PATH']
    dst = 'health-metrics/macrobenchmark/template/app/google-services.json'
    _logger.info(f'Running on CI. Copying "{src}" to "{dst}"...')
    shutil.copyfile(src, dst)


async def _post_processing(results):
  # Upload successful measurements to the metric service
  measurements = []
  for result in results:
    if not isinstance(result, Exception):
      measurements.extend(result)

  metrics_service_url = os.getenv('METRICS_SERVICE_URL')
  access_token = prow_utils.gcloud_identity_token()
  uploader.post_report(measurements, metrics_service_url, access_token, metric='macrobenchmark')

  # Raise exceptions for failed measurements
  if any(map(lambda x: isinstance(x, Exception), results)):
    _logger.error(f'Exceptions: {[x for x in results if isinstance(x, Exception)]}')
    raise click.ClickException('Macrobenchmark test failed with above errors.')


class MacrobenchmarkTest:
  """Builds the test based on configurations and runs the test on FTL."""
  def __init__(
      self,
      sdk_name,
      test_app_config,
      artifact_versions,
      logger=_logger
  ):
    self.sdk_name = sdk_name
    self.test_app_config = test_app_config
    self.artifact_versions = artifact_versions
    self.logger = MacrobenchmarkLoggerAdapter(logger, sdk_name)
    self.test_app_dir = os.path.join('test-apps', test_app_config['name'])
    self.test_results_bucket = 'fireescape-benchmark-results'
    self.test_results_dir = str(uuid.uuid4())
    self.gcs_client = storage.Client()

  async def run(self):
    """Starts the workflow of src creation, apks assembly, FTL testing and results upload."""
    await self._create_benchmark_projects()
    await self._assemble_benchmark_apks()
    await self._execute_benchmark_tests()
    return await self._aggregate_benchmark_results()

  async def _create_benchmark_projects(self):
    app_name = self.test_app_config['name']
    self.logger.info(f'Creating test app "{app_name}"...')

    mustache_context = await self._prepare_mustache_context()

    shutil.copytree('template', self.test_app_dir)
    with chdir(self.test_app_dir):
      renderer = pystache.Renderer()
      mustaches = glob.glob('**/*.mustache', recursive=True)
      for mustache in mustaches:
        result = renderer.render_path(mustache, mustache_context)
        original_name = mustache[:-9]  # TODO(yifany): mustache.removesuffix('.mustache')
        with open(original_name, 'w') as file:
          file.write(result)

  async def _assemble_benchmark_apks(self):
    executable = './gradlew'
    args = ['assemble', 'assembleAndroidTest', '--project-dir', self.test_app_dir]
    await self._exec_subprocess(executable, args)

  async def _execute_benchmark_tests(self):
    app_apk_path = glob.glob(f'{self.test_app_dir}/app/**/*.apk', recursive=True)[0]
    test_apk_path = glob.glob(f'{self.test_app_dir}/benchmark/**/*.apk', recursive=True)[0]

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
    args += ['--device', 'model=redfin,version=30,locale=en,orientation=portrait']
    args += ['--directories-to-pull', '/sdcard/Download']
    args += ['--results-bucket', f'gs://{self.test_results_bucket}']
    args += ['--results-dir', self.test_results_dir]
    args += ['--environment-variables', ','.join(ftl_environment_variables)]
    args += ['--timeout', '30m']
    args += ['--project', 'fireescape-c4819']

    await self._exec_subprocess(executable, args)

  async def _prepare_mustache_context(self):
    app_name = self.test_app_config['name']

    mustache_context = {
      'plugins': [],
      'dependencies': [],
    }

    if app_name != 'baseline':
      mustache_context['plugins'].append('com.google.gms.google-services')

    if 'plugins' in self.test_app_config:
      mustache_context['plugins'].extend(self.test_app_config['plugins'])

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
        runs = benchmark['metrics']['startupMs']['runs']
        results.append({
          'sdk': self.sdk_name,
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
