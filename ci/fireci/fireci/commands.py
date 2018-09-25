# Copyright 2018 Google LLC
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
import os

from . import gradle
from . import ci_command


@click.argument('task', required=True, nargs=-1)
@click.option(
    '--gradle-opts',
    default='',
    help='GRADLE_OPTS passed to the gradle invocation.')
@ci_command('gradle')
def gradle_command(task, gradle_opts):
  """Runs the specified gradle commands."""
  gradle.run(*task, gradle_opts=gradle_opts)


@click.option(
    '--app-build-variant',
    type=click.Choice(['debug', 'release']),
    default='release',
    help=
    'App build variant to use while running the smoke Tests. One of release|debug'
)
@ci_command()
def smoke_tests(app_build_variant):
  """Builds all SDKs in release mode and then tests test-apps against them."""
  gradle.run('publishAllToBuildDir')

  cwd = os.getcwd()
  gradle.run(
      'connectedCheck',
      '-PtestBuildType=%s' % (app_build_variant),
      gradle_opts='-Dmaven.repo.local={}'.format(
          os.path.join(cwd, 'build', 'm2repository')),
      workdir=os.path.join(cwd, 'test-apps'),
  )
