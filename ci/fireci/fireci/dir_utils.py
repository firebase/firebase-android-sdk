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
import logging
import os
import pathlib
import shutil
import glob

_logger = logging.getLogger('fireci.dir_utils')


@contextlib.contextmanager
def chdir(directory):
  """Change working dir to `directory` and restore to original afterwards."""
  _logger.debug(f'Changing directory to: {directory} ...')
  original_dir = os.getcwd()
  os.chdir(directory)
  try:
    yield
  finally:
    _logger.debug(f'Restoring directory to: {original_dir} ...')
    os.chdir(original_dir)

def rmdir(path: str) -> bool:
  """Recursively deletes a directory, and returns a boolean indicating if the dir was deleted."""
  dir = pathlib.Path(path)
  if not dir.exists():
    _logger.debug(f"Directory already deleted: {dir}")
    return False

  _logger.debug(f"Deleting directory: {dir}")
  shutil.rmtree(dir)
  return True

def rmglob(pattern: str) -> int:
  """Deletes all files that match a given pattern, and returns the amount of (root) files deleted"""
  files = glob.glob(os.path.expanduser(pattern))
  for file in files:
    path = pathlib.Path(file)
    if path.is_dir():
      rmdir(file)
    else:
      _logger.debug(f"Deleting file: {path}")
      os.remove(path)
  
  return len(files)
