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
import shutil
import sys

import click
import pystache
import yaml

from fireci import ci_command
from fireci.dir_utils import chdir

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

  with chdir('macrobenchmark'):
    runners = [MacrobenchmarkTest(k, v, artifact_versions) for k, v in config.items()]
    results = await asyncio.gather(*[x.run() for x in runners], return_exceptions=True)

  if any(map(lambda x: isinstance(x, Exception), results)):
    _logger.error(f'Exceptions: {[x for x in results if (isinstance(x, Exception))]}')
    raise click.ClickException('Macrobenchmark test failed with above errors.')

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
  with open('macrobenchmark/config.yaml') as yaml_file:
    return yaml.safe_load(yaml_file)


async def _create_gradle_wrapper():
  with open('macrobenchmark/settings.gradle', 'w'):
    pass

  proc = await asyncio.subprocess.create_subprocess_exec(
    './gradlew',
    'wrapper',
    '--gradle-version',
    '6.9',
    '--project-dir',
    'macrobenchmark'
  )
  await proc.wait()


async def _copy_google_services():
  if 'FIREBASE_CI' in os.environ:
    src = os.environ['FIREBASE_GOOGLE_SERVICES_PATH']
    dst = 'macrobenchmark/template/app/google-services.json'
    _logger.info(f'Running on CI. Copying "{src}" to "{dst}"...')
    shutil.copyfile(src, dst)


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

  async def run(self):
    """Starts the workflow of src creation, apks assembling, and FTL testing in order."""
    await self._create_test_src()
    await self._assemble_apks()
    await self._upload_apks_to_ftl()

  async def _create_test_src(self):
    app_name = self.test_app_config['name']
    app_id = self.test_app_config['application-id']
    self.logger.info(f'Creating test app "{app_name}" with application-id "{app_id}"...')

    mustache_context = {
      'application-id': app_id,
      'plugins': self.test_app_config['plugins'] if 'plugins' in self.test_app_config else [],
      'dependencies': [
        {
          'key': x,
          'version': self.artifact_versions[x]
        } for x in self.test_app_config['dependencies']
      ] if 'dependencies' in self.test_app_config else [],
    }

    if app_name != 'baseline':
      mustache_context['plugins'].append('com.google.gms.google-services')

    shutil.copytree('template', self.test_app_dir)
    with chdir(self.test_app_dir):
      renderer = pystache.Renderer()
      mustaches = glob.glob('**/*.mustache', recursive=True)
      for mustache in mustaches:
        result = renderer.render_path(mustache, mustache_context)
        original_name = mustache[:-9]  # TODO(yifany): mustache.removesuffix('.mustache')
        with open(original_name, 'w') as file:
          file.write(result)

  async def _assemble_apks(self):
    executable = './gradlew'
    args = ['assemble', 'assembleAndroidTest', '--project-dir', self.test_app_dir]
    await self._exec_subprocess(executable, args)

  async def _upload_apks_to_ftl(self):
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
    args += ['--device', 'model=flame,version=30,locale=en,orientation=portrait']
    args += ['--directories-to-pull', '/sdcard/Download']
    args += ['--results-bucket', 'gs://fireescape-macrobenchmark']
    args += ['--environment-variables', ','.join(ftl_environment_variables)]
    args += ['--timeout', '30m']
    args += ['--project', 'fireescape-c4819']

    await self._exec_subprocess(executable, args)

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
