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

import functools
import os
import shutil
import tempfile


class Artifact:
  """Represents an artifact that can be either a file or a directory."""

  def __init__(self, path, *, content='', mode=0o644, is_dir=False):
    if content and is_dir:
      raise ValueError('Directories cannot contain content')
    self.path = path
    self.content = content
    self.mode = mode
    self.is_dir = is_dir


def create_artifacts(*artifacts):
  """Persists the specified artifacts to disk."""
  for artifact in artifacts:
    if artifact.is_dir:
      os.makedirs(artifact.path)
    else:
      dirname = os.path.dirname(artifact.path)
      if dirname:
        os.makedirs(os.path.dirname(artifact.path), exist_ok=True)
      with open(artifact.path, 'w') as opened_file:
        opened_file.write(artifact.content)
    os.chmod(artifact.path, artifact.mode)


def in_tempdir(func):
  """Decorator that runs a function while in a dedicated temporary directory."""

  def do_in_temp_dir(*args, **kwargs):
    tempdir_path = tempfile.mkdtemp()
    original_workdir = os.getcwd()
    os.chdir(tempdir_path)
    try:
      func(*args, **kwargs)
    finally:
      os.chdir(original_workdir)
      shutil.rmtree(tempdir_path)

  return functools.update_wrapper(do_in_temp_dir, func)


def with_env(env, extend=True):

  def inner(func):

    def decorated(*args, **kwargs):
      original_env = os.environ
      new_env = original_env if extend else {}
      expanded_env = {
          os.path.expandvars(k): os.path.expandvars(v)
          for (k, v) in env.items()
      }

      os.environ = {**new_env, **expanded_env}
      try:
        func(*args, **kwargs)
      finally:
        os.environ = original_env

    return functools.update_wrapper(decorated, func)

  return inner
