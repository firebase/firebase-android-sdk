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

import datetime
import logging
import os
import signal
import subprocess
import time

from . import stats

_logger = logging.getLogger('fireci.emulator')

EMULATOR_BINARY = 'emulator'
ADB_BINARY = 'adb'
EMULATOR_NAME = 'test'

EMULATOR_FLAGS = ['-no-audio', '-no-window', '-skin', '768x1280']


class EmulatorHandler:
  """
     Context manager that launches an android emulator for the duration of its execution.

     As part of its run it:
     * Launches the emulator
     * Waits for it to boot
     * Starts logcat to store on-device logs
     * Produces stdout.log, stderr.log, logcat.log in the artifacts directory
  """

  def __init__(
      self,
      artifacts_dir,
      *,
      name=EMULATOR_NAME,
      emulator_binary=EMULATOR_BINARY,
      adb_binary=ADB_BINARY,
      # for testing only
      emulator_stdin=None,
      wait_for_device_stdin=None,
      logcat_stdin=None):
    self._artifacts_dir = artifacts_dir

    log_dir = '{}_emulator'.format(name)
    self._stdout = self._open(log_dir, 'stdout.log')
    self._stderr = self._open(log_dir, 'stderr.log')
    self._adb_log = self._open(log_dir, 'logcat.log')
    self._name = name

    self._emulator_binary = emulator_binary
    self._adb_binary = adb_binary

    self._emulator_stdin = emulator_stdin
    self._wait_for_device_stdin = wait_for_device_stdin
    self._logcat_stdin = logcat_stdin

  @stats.measure_call("emulator_startup")
  def __enter__(self):
    _logger.info('Starting avd "{}..."'.format(self._name))
    self._process = subprocess.Popen(
        [self._emulator_binary, '-avd', self._name] + EMULATOR_FLAGS,
        env=os.environ,
        stdin=self._emulator_stdin,
        stdout=self._stdout,
        stderr=self._stderr)
    try:
      self._wait_for_boot(datetime.timedelta(minutes=10))
    except:
      self._kill(self._process)
      self._close_files()
      raise

    self._logcat = subprocess.Popen(
        [self._adb_binary, 'logcat'],
        stdin=self._logcat_stdin,
        stdout=self._adb_log,
    )

  @stats.measure_call("emulator_shutdown")
  def __exit__(self, exception_type, exception_value, traceback):
    _logger.info('Shutting down avd "{}"...'.format(self._name))
    self._kill(self._process)
    _logger.info('Avd "{}" shut down.'.format(self._name))
    self._kill(self._logcat)
    self._close_files()

  def _open(self, dirname, filename):
    """Opens a file in a given directory, creates directory if required."""
    dirname = os.path.join(self._artifacts_dir, dirname)
    if (not os.path.exists(dirname)):
      os.makedirs(dirname)
    return open(os.path.join(dirname, filename), 'w')

  def _wait_for_boot(self, timeout: datetime.timedelta):
    _logger.info('Waiting for avd to boot...')
    wait = subprocess.Popen(
        [self._adb_binary, 'wait-for-device'],
        stdin=self._wait_for_device_stdin,
        stdout=self._stdout,
        stderr=self._stderr,
    )

    start = datetime.datetime.now()
    while self._process.poll() is None:
      wait_exitcode = wait.poll()
      if wait_exitcode is not None:
        if wait_exitcode == 0:
          _logger.info('Emulator booted successfully.')
          return
        raise RuntimeError("Waiting for emulator failed.")

      time.sleep(0.1)
      now = datetime.datetime.now()
      if now - start >= timeout:
        self._kill(wait, sig=signal.SIGKILL)
        raise RuntimeError("Emulator startup timed out.")

    self._kill(wait)
    raise RuntimeError(
        "Emulator failed to launch. See emulator logs for details.")

  def _kill(self, process, sig=signal.SIGTERM):
    process.send_signal(sig)
    process.wait()

  def _close_files(self):
    for f in (self._stdout, self._stderr, self._adb_log):
      if f is not None:
        f.close()
