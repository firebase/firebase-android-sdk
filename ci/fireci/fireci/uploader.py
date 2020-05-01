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

import json
import logging
import os
import requests
import subprocess
import urllib.parse

_logger = logging.getLogger('fireci.uploader')


def post_report(test_report, metrics_service_url, access_token, note=''):
  """Post a report to the metrics service backend."""

  endpoint = _construct_request_endpoint(note)
  headers = {'Authorization': f'Bearer {access_token}', 'Content-Type': 'application/json'}
  data = json.dumps(test_report)

  _logger.info('Posting to the metrics service ...')
  _logger.info(f'Request endpoint: {endpoint}')
  _logger.info(f'Request data: {data}')

  request_url = f'{metrics_service_url}{endpoint}'
  result = requests.post(request_url, data=data, headers=headers)

  _logger.info(f'Response: {result.text}')


def _construct_request_endpoint(note):
  repo_owner = os.getenv('REPO_OWNER')
  repo_name = os.getenv('REPO_NAME')
  branch = os.getenv('PULL_BASE_REF')
  pull_request = os.getenv('PULL_NUMBER')

  commit = _get_commit_hash('HEAD@{0}')

  endpoint = f'/repos/{repo_owner}/{repo_name}/commits/{commit}/reports'
  if pull_request:
    base_commit = _get_commit_hash('HEAD@{1}')
    endpoint += f'?pull_request={pull_request}&base_commit={base_commit}'

    commit_note = _get_prow_commit_note('HEAD@{0}')
    note += f'\n{commit_note}\n'
    endpoint += f'&note={urllib.parse.quote(note)}'
  else:
    endpoint += f'?branch={branch}'

  return endpoint


def _get_commit_hash(revision):
  result = subprocess.run(['git', 'rev-parse', revision], stdout=subprocess.PIPE, check=True)
  return result.stdout.decode('utf-8').strip()


def _get_prow_commit_note(revision):
  template = 'Head commit (%h) is created by Prow via merging commits: %p.'
  result = subprocess.run(
    ['git', 'show', revision, f'--format={template}', '-s'],
    stdout=subprocess.PIPE,
    check=True,
  )
  return result.stdout.decode('utf-8').strip()
