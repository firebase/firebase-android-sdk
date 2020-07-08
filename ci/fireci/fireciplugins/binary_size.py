# Copyright 2020 Google LLC
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
import glob
import json
import logging
import os
import re

from fireci import ci_command
from fireci import gradle
from fireci import prow_utils
from fireci import uploader

_logger = logging.getLogger('fireci.binary_size')


@click.option(
  '--pull-request/--no-pull-request',
  default=False,
  help='When running for pull requests, only size of affected SDKs are collected.'
)
@click.option(
  '--log',
  default=prow_utils.prow_job_log_link,
  help='The link to the log of the prow job, which runs this coverage check.'
)
@click.option(
  '--metrics-service-url',
  envvar='METRICS_SERVICE_URL',
  help='The URL to the metrics service, which persists data and calculates diff.'
)
@click.option(
  '--access-token',
  default=prow_utils.gcloud_identity_token,
  help='The access token, used to authorize http requests to the metrics service.'
)
@ci_command()
def binary_size(pull_request, log, metrics_service_url, access_token):
  """Produces and uploads binary size reports."""

  gradle.run('assembleAllForSmokeTests')

  affected_artifacts, all_artifacts = _parse_artifacts()
  artifacts = affected_artifacts if pull_request else all_artifacts
  sdks = ','.join(artifacts)

  gradle.run('assemble', '--continue', gradle.P('sdks', sdks), workdir='apk-size', check=False)

  test_results = _measure_aar_sizes(artifacts) + _measure_apk_sizes(artifacts)
  test_report = {'metric': 'BinarySize', 'results': test_results, 'log': log}

  uploader.post_report(test_report, metrics_service_url, access_token)


def _measure_aar_sizes(artifacts):
  test_results = []

  for artifact in artifacts:
    group_id, artifact_id, version = artifact.split(':')
    aar_files = glob.glob(f'./**/m2repository/**/{artifact_id}/**/*.aar', recursive=True)

    if aar_files:
      aar_size = os.path.getsize(aar_files[0])
      test_results.append({'sdk': artifact_id, 'type': 'aar', 'value': aar_size})

  return test_results


def _measure_apk_sizes(artifacts):
  test_results = []

  for artifact in artifacts:
    group_id, artifact_id, version = artifact.split(':')
    apk_files = glob.glob(f'./apk-size/**/{artifact_id}/**/*.apk', recursive=True)

    for apk_file in apk_files:
      build_type = re.search(fr'{artifact_id}/([^/]*)/', apk_file).group(1)
      apk_size = os.path.getsize(apk_file)
      test_results.append({'sdk': artifact_id, 'type': f'apk ({build_type})', 'value': apk_size})

  return test_results


def _parse_artifacts():
  with open('./build/m2repository/changed-artifacts.json') as json_file:
    artifacts = json.load(json_file)
    return artifacts['default'], artifacts['headGit']
