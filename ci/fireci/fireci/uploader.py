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


_logger = logging.getLogger('fireci.uploader')


def post_report(test_report, metrics_service_url, access_token, metric_type, asynchronous=False):
  """Post a report to the metrics service backend."""

  endpoint = ''
  if os.getenv('GITHUB_ACTIONS'):
    endpoint = _construct_request_endpoint_for_github_actions(metric_type)
  elif os.getenv('PROW_JOB_ID'):
    endpoint = _construct_request_endpoint_for_prow(metric_type)

  if asynchronous:
    endpoint += '&async=true'

  headers = {'Authorization': f'Bearer {access_token}', 'Content-Type': 'application/json'}
  data = json.dumps(test_report)

  _logger.info('Posting to the metrics service ...')
  _logger.info(f'Request endpoint: {endpoint}')
  _logger.info(f'Request data: {data}')

  request_url = f'{metrics_service_url}{endpoint}'
  result = requests.post(request_url, data=data, headers=headers)

  _logger.info(f'Response: {result.text}')


def _construct_request_endpoint_for_github_actions(metric_type):
  repo = os.getenv('GITHUB_REPOSITORY')
  commit = os.getenv('GITHUB_SHA')
  event_name = os.getenv('GITHUB_EVENT_NAME')

  endpoint = f'/repos/{repo}/commits/{commit}/{metric_type}'
  if event_name == 'pull_request':
    pull_request = os.getenv('GITHUB_PULL_REQUEST_NUMBER')
    base_commit = _get_commit_hash('HEAD^1')
    head_commit = _get_commit_hash('HEAD^2')
    endpoint += f'?pull_request={pull_request}&base_commit={base_commit}&head_commit={head_commit}'
  else:
    branch = os.getenv('GITHUB_REF_NAME')
    endpoint += f'?branch={branch}'

  return endpoint


def _construct_request_endpoint_for_prow(metric_type):
  repo_owner = os.getenv('REPO_OWNER')
  repo_name = os.getenv('REPO_NAME')
  commit = _get_commit_hash('HEAD@{0}')
  pull_request = os.getenv('PULL_NUMBER')

  endpoint = f'/repos/{repo_owner}/{repo_name}/commits/{commit}/{metric_type}'
  if pull_request:
    base_commit = os.getenv('PULL_BASE_SHA')
    head_commit = os.getenv('PULL_PULL_SHA')
    endpoint += f'&pull_request={pull_request}&base_commit={base_commit}&head_commit={head_commit}'
  else:
    branch = os.getenv('PULL_BASE_REF')
    endpoint += f'&branch={branch}'

  return endpoint


def _get_commit_hash(revision):
  result = subprocess.run(['git', 'rev-parse', revision], stdout=subprocess.PIPE, check=True)
  return result.stdout.decode('utf-8').strip()
