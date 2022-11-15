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

import datetime
import string
import random

from asyncio import create_subprocess_exec
from asyncio.subprocess import PIPE as ASYNC_PIPE, STDOUT as ASYNC_STDOUT
from logging import Logger, LoggerAdapter
from subprocess import Popen, PIPE, STDOUT
from typing import Union


def generate_test_run_id() -> str:
  now = datetime.datetime.now()
  date = now.date()
  time = now.time()
  name = ''.join(random.choices(string.ascii_letters, k=4))
  return f'{date}_{time}_{name}'


def execute(program: str, *args: str, logger: Union[Logger, LoggerAdapter]) -> None:
  command = " ".join([program, *args])
  logger.info(f'Executing subprocess: "{command}" ...')

  popen = Popen([program, *args], stdout=PIPE, stderr=STDOUT)
  for line in popen.stdout:
    logger.info(f'[{program}] {line.decode("utf-8").strip()}')
  popen.communicate()

  if popen.returncode == 0:
    logger.info(f'"{command}" succeeded')
  else:
    message = f'"{command}" failed with return code {popen.returncode}'
    logger.error(message)
    raise RuntimeError(message)


async def execute_async(program: str, *args: str, logger: Union[Logger, LoggerAdapter]) -> None:
  command = " ".join([program, *args])
  logger.info(f'Executing subprocess: "{command}" ...')

  process = await create_subprocess_exec(program, *args, stdout=ASYNC_PIPE, stderr=ASYNC_STDOUT)
  async for line in process.stdout:
    logger.info(f'[{program}] {line.decode("utf-8").strip()}')
  await process.communicate()

  if process.returncode == 0:
    logger.info(f'"{command}" succeeded')
  else:
    message = f'"{command}" failed with return code {process.returncode}'
    logger.error(message)
    raise RuntimeError(message)
