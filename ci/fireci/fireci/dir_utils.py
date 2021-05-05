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
