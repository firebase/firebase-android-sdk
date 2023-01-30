# Copyright 2022 Google LLC
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

import random
import sys

from logging import Logger, LoggerAdapter
from typing import Union


RESET_CODE = '\x1b[m'


class LogDecorator(LoggerAdapter):
  """Decorates log messages with colors in console output."""

  def __init__(self, logger: Union[Logger, LoggerAdapter], key: str):
    super().__init__(logger, {})
    self.key = key
    self.color_code = self._random_color_code()

  def process(self, msg, kwargs):
    colored, uncolored = self._produce_prefix()
    result = f'{colored if sys.stderr.isatty() else uncolored} {msg}'
    return result, kwargs

  @staticmethod
  def _random_color_code():
    code = random.randint(16, 231)  # https://en.wikipedia.org/wiki/ANSI_escape_code#8-bit
    return f'\x1b[38;5;{code}m'

  def _produce_prefix(self):
    if hasattr(super(), '_produce_prefix'):
      colored_super, uncolored_super = getattr(super(), '_produce_prefix')()
      colored = f'{colored_super} {self.color_code}[{self.key}]{RESET_CODE}'
      uncolored = f'{uncolored_super} [{self.key}]'
    else:
      colored = f'{self.color_code}[{self.key}]{RESET_CODE}'
      uncolored = f'[{self.key}]'
    return colored, uncolored
