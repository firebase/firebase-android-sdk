# Copyright 2020 Google LLC
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

import click
import logging
import os
import pathlib

from fireci import ci_command
from fireci import gradle
from fireci.dir_utils import chdir

_logger = logging.getLogger('fireci.fireperf')


@click.option(
  '--plugin_repo_dir',
  help='The location of the fireperf plugin repository.',
  required=True,
)
@click.option(
  '--target_environment',
  type=click.Choice(['prod', 'autopush'], case_sensitive=False),
  help='The target environment fireperf is built for.',
  required=True,
)
@ci_command()
def fireperf_e2e_test(target_environment, plugin_repo_dir):
  """Run Firebase Performance end-to-end test."""

  _logger.info('Building fireperf plugin ...')
  with chdir(plugin_repo_dir):
    build_plugin_task = ':firebase-performance:perf-plugin:publishToMavenLocal'
    gradle.run(build_plugin_task, gradle.P('publishMode', 'SNAPSHOT'))

  version = _find_fireperf_plugin_version()
  _logger.info(f'Setting environment variable: FIREBASE_PERF_PLUGIN_VERSION={version} ...')
  os.environ['FIREBASE_PERF_PLUGIN_VERSION'] = version

  fireperf_e2e_test_gradle_command = [
    '--build-cache',
    '--parallel',
    '--continue',
    ':firebase-perf:e2e-app:deviceCheck',
    gradle.P('instrumentFireperfE2ETest', 'true')
  ]
  if target_environment == 'autopush':
    fireperf_e2e_test_gradle_command += [gradle.P('fireperfBuildForAutopush', 'true')]
  _logger.info(f'Running fireperf e2e test with target environment: {target_environment} ...')
  gradle.run(*fireperf_e2e_test_gradle_command)


def _find_fireperf_plugin_version():
  local_maven_repo_dir = pathlib.Path.home().joinpath('.m2', 'repository')
  artifacts_path = local_maven_repo_dir.joinpath('com', 'google', 'firebase', 'perf-plugin')
  versions = [pom.parent.name for pom in artifacts_path.rglob('*.pom')]
  snapshots = list(filter(lambda v: v.endswith('-SNAPSHOT'), versions))

  if snapshots:
    return snapshots[0]
  else:
    raise click.ClickException('Cannot find a snapshot version in local maven repo.')
