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

import io
import os
import pathlib
import threading
import time
import unittest

from concurrent import futures

from fireci.internal import _emulator_handler
from fireci.emulator import EMULATOR_FLAGS
from .fileutil import (
    Artifact,
    create_artifacts,
    in_tempdir,
    with_env,
)
from . import scripts


class ProcessChannel:
  """Write-only communication channel with a process through its stdin."""

  def __init__(self, fd):
    self._fd = fd

  def send(self, value, close=False):
    os.write(self._fd, bytes(str(value), encoding='utf8'))
    if close:
      os.close(self._fd)


class EmulatorTests(unittest.TestCase):
  executor = futures.ThreadPoolExecutor(max_workers=1)

  @in_tempdir
  def test_emulator_when_not_requested_should_not_produce_logs(self):
    with _emulator_handler(False):
      pass
    self.assertFalse(os.listdir(os.getcwd()))

  @in_tempdir
  def test_emulator_when_emulator_fails_to_start_should_fail(self):
    create_artifacts(
        Artifact('emulator', content=scripts.waiting_for_status(), mode=0o744),
        Artifact('adb', content=scripts.waiting_for_status(), mode=0o744),
    )
    future, emulator, waiter, logcat = self._invoke_emulator(
        './emulator', './adb')

    # emulator exits before wait-for-device
    emulator.send(0, close=True)

    with self.assertRaisesRegex(RuntimeError, 'Emulator failed to launch'):
      future.result(timeout=1)

  @in_tempdir
  def test_emulator_when_wait_for_device_fails_should_fail(self):
    create_artifacts(
        Artifact('emulator', content=scripts.waiting_for_status(), mode=0o744),
        Artifact('adb', content=scripts.waiting_for_status(), mode=0o744),
    )

    future, emulator, waiter, logcat = self._invoke_emulator(
        './emulator', './adb')

    # wait-for-device fails
    waiter.send(1, close=True)

    with self.assertRaisesRegex(RuntimeError, 'Waiting for emulator failed'):
      future.result(timeout=1)

  @in_tempdir
  def test_emulator_when_startup_succeeds_should_produce_expected_outputs(self):
    create_artifacts(
        Artifact('emulator', content=scripts.waiting_for_status(), mode=0o744),
        Artifact('adb', content=scripts.waiting_for_status(), mode=0o744),
    )

    future, emulator, waiter, logcat = self._invoke_emulator(
        './emulator', './adb')

    # wait-for-device succeeds
    waiter.send(0, close=True)

    future.result(timeout=1)

    path = pathlib.Path('_artifacts') / 'test_emulator'

    stdout = path / 'stdout.log'
    stderr = path / 'stderr.log'
    logcat = path / 'logcat.log'

    for p in (stdout, stderr, logcat):
      self.assertTrue(p.exists())
      self.assertTrue(p.is_file())

    # both emulator and wait-for-device write to stdout.log
    self._assert_file_contains(
        stdout, './emulator -avd test {}\n./adb wait-for-device\n'.format(
            ' '.join(EMULATOR_FLAGS)))

    # emulator writes to stderr.log
    self._assert_file_contains(stderr, 'stderr\n' * 2)

    # logccat writes to logcat.log
    self._assert_file_contains(logcat, './adb logcat\n')

  def _assert_file_contains(self, path, expected_contents):
    with path.open() as f:
      c = f.read()
      self.assertEqual(c, expected_contents)

  def _invoke_emulator(self, emulator_binary, adb_binary):
    emulator_stdin, emulator_channel = os.pipe()
    wait_stdin, wait_channel = os.pipe()
    logcat_stdin, logcat_channel = os.pipe()

    def handler():

      with _emulator_handler(
          True,
          '_artifacts',
          emulator_binary=emulator_binary,
          adb_binary=adb_binary,
          emulator_stdin=emulator_stdin,
          wait_for_device_stdin=wait_stdin,
          logcat_stdin=logcat_stdin,
      ):
        time.sleep(0.1)

    future = self.executor.submit(handler)
    return future, ProcessChannel(emulator_channel), ProcessChannel(
        wait_channel), ProcessChannel(logcat_channel)
