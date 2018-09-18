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

_SCRIPT_WITH_EXIT_TEMPLATE = """\
#!/usr/bin/env python3
import sys
sys.exit({})
"""


def with_exit(exit_code):
  """Python script that exits with provided status."""
  return _SCRIPT_WITH_EXIT_TEMPLATE.format(exit_code)


_SCRIPT_WITH_EXPECTED_ARGUMENTS_TEMPLATE = """\
#!/usr/bin/env python3
import sys, os
expected_args = [{}]
expected_env = {}
if sys.argv != expected_args:
  raise ValueError('Expected args: %s, but got %s' % (expected_args, sys.argv))
for k, v in expected_env.items():
  envval = os.environ.get(k, '')
  if envval != v:
    raise ValueError("Expected env[%s] == '%s', but got '%s'" % (k, v, envval))
"""

_SCRIPT_WITH_ARTIFACTS = _SCRIPT_WITH_EXPECTED_ARGUMENTS_TEMPLATE + """
artifacts = {}
for path, content in artifacts:
  basedir = os.path.dirname(path)
  if basedir and not os.path.exists(basedir):
    os.makedirs(basedir)
  with open(path, 'w') as f:
    f.write(content)
"""


def with_expected_arguments(args, env={}):
  """Python script that checks its argv and environment."""
  arg_string = ', '.join(['"{}"'.format(arg) for arg in args])
  return _SCRIPT_WITH_EXPECTED_ARGUMENTS_TEMPLATE.format(arg_string, env)


def with_expected_arguments_and_artifacts(args, env, *artifacts):
  """Python script that checks its argv, environment and creates provided files/directories."""
  arg_string = ', '.join(['"{}"'.format(arg) for arg in args])
  return _SCRIPT_WITH_ARTIFACTS.format(arg_string, env, artifacts)


_SCRIPT_WAITING_FOR_STATUS = """\
#!/bin/sh
'''true'
exec python3 -u "$0" "$@"
'''
import sys
print(' '.join(sys.argv), file=sys.stdout)
print('stderr', file=sys.stderr)
stdin = sys.stdin.read()
if stdin:
    sys.exit(int(stdin))
"""


def waiting_for_status():
  """Python script with unbuffered that:
     * Prints argv to stdout
     * Prints 'stderr to stderr
     * Waits for exit status to be written to its stdin and exits with it.
  """
  return _SCRIPT_WAITING_FOR_STATUS
