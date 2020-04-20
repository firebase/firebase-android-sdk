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

import logging
import os
import subprocess
import sys

from . import stats

_logger = logging.getLogger('fireci.gradle')

ADB_INSTALL_TIMEOUT = '5'


def P(name, value):
  """Returns name and value in the format of gradle's project property cli argument."""
  return '-P{}={}'.format(name, value)


@stats.measure_call('gradle')
def run(*args, gradle_opts='', workdir=None, check=True):
  """Invokes gradle with specified args and gradle_opts."""
  new_env = dict(os.environ)
  if gradle_opts:
    new_env['GRADLE_OPTS'] = gradle_opts
  new_env[
      'ADB_INSTALL_TIMEOUT'] = ADB_INSTALL_TIMEOUT  # 5 minutes, rather than 2 minutes
  stats.propagate_context_into(new_env)

  command = ['./gradlew'] + list(args)
  _logger.info('Executing gradle command: "%s" in directory: "%s"',
               " ".join(command), workdir if workdir else '.')
  return subprocess.run(
      command,
      cwd=workdir,
      env=new_env,
      check=check,
  )
