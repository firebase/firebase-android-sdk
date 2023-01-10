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
import functools
import glob
import itertools
import logging
import os
import shutil

from contextlib import contextmanager, nullcontext

_logger = logging.getLogger('fireci')


def _ensure_dir(directory):
  if not os.path.exists(directory):
    os.makedirs(directory)


@contextmanager
def _artifact_handler(target_directory, artifact_patterns):
  _logger.debug(
      'Artifacts will be searched for in directories matching {} patterns and placed in {}'
      .format(artifact_patterns, target_directory))
  try:
    yield
  finally:
    _ensure_dir(target_directory)
    paths = itertools.chain(*(glob.iglob(x, recursive=True)
                              for x in artifact_patterns))
    for path in paths:
      target_name = os.path.join(target_directory, "_".join(path.split('/')))
      _logger.debug('Copying artifact {} to {}'.format(path, target_name))
      if os.path.isdir(path):
        shutil.copytree(path, target_name, dirs_exist_ok=True)
      else:
        shutil.copyfile(path, target_name)


class _CommonOptions:
  pass


_pass_options = click.make_pass_decorator(_CommonOptions, ensure=True)


@click.group()
@click.option(
    '--artifact-target-dir',
    default='_artifacts',
    help='Directory where artifacts will be copied to.',
    type=click.Path(dir_okay=True, resolve_path=True),
)
@click.option(
    '--artifact-patterns',
    default=('**/build/test-results', '**/build/reports'),
    help=
    'Shell-style artifact patterns that are copied into `artifact-target-dir`. '
    'Can be specified multiple times.',
    multiple=True,
    type=str,
)
@_pass_options
def main(options, **kwargs):
  """Main command group.

       Should be the "main" entrypoint of the binary.
    """
  for k, v in kwargs.items():
    setattr(options, k, v)


def ci_command(name=None, cls=click.Command, group=main):
  """Decorator to use for CI commands.

       The differences from the standard @click.command are:

       * Allows configuration of artifacts that are uploaded for later viewing in CI.
       * Registers the command automatically.

       :param name:  Optional name of the task. Defaults to the function name that is decorated with this decorator.
       :param cls:   Specifies whether the func is a command or a command group. Defaults to `click.Command`.
       :param group: Specifies the group the command belongs to. Defaults to the `main` command group.
    """

  def ci_command(f):
    actual_name = f.__name__ if name is None else name

    @click.command(name=actual_name, cls=cls, help=f.__doc__)
    @_pass_options
    @click.pass_context
    def new_func(ctx, options, *args, **kwargs):
      with _artifact_handler(
          options.artifact_target_dir,
          options.artifact_patterns,
      ) if cls is click.Command else nullcontext():
        return ctx.invoke(f, *args, **kwargs)

    group.add_command(new_func)

    return functools.update_wrapper(new_func, f)

  return ci_command
