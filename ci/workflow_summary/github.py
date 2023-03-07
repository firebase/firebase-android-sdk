# Copyright 2023 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""A utility for GitHub REST API."""

import requests
import logging

RETRIES = 3
BACKOFF = 5
RETRY_STATUS = (403, 500, 502, 504)
TIMEOUT = 5
TIMEOUT_LONG = 20

OWNER = ''
REPO = ''
BASE_URL = 'https://api.github.com'
GITHUB_API_URL = '%s/repos/%s/%s' % (BASE_URL, OWNER, REPO)


def set_api_url(owner, repo): 
  if owner and repo:
    global OWNER, REPO, GITHUB_API_URL
    OWNER = owner
    REPO = repo
    GITHUB_API_URL = '%s/repos/%s/%s' % (BASE_URL, OWNER, REPO)
    logging.info('GITHUB_API_URL been set: %s' % GITHUB_API_URL)
    return True
  else:
    return False


def list_workflows(token, workflow_id, params):
  """https://docs.github.com/en/rest/actions/workflow-runs#list-workflow-runs-for-a-workflow"""
  url = f'{GITHUB_API_URL}/actions/workflows/{workflow_id}/runs'
  headers = {'Accept': 'application/vnd.github+json', 'Authorization': f'token {token}'}
  with requests.get(url, headers=headers, params=params,
                    stream=True, timeout=TIMEOUT_LONG) as response:
    logging.info('list_workflows: %s, params: %s, response: %s', url, params, response)
    return response.json()

def list_jobs(token, run_id, params):
  """https://docs.github.com/en/rest/actions/workflow-jobs#list-jobs-for-a-workflow-run"""
  url = f'{GITHUB_API_URL}/actions/runs/{run_id}/jobs'
  headers = {'Accept': 'application/vnd.github+json', 'Authorization': f'token {token}'}
  with requests.get(url, headers=headers, params=params,
                    stream=True, timeout=TIMEOUT_LONG) as response:
    logging.info('list_jobs: %s, params: %s, response: %s', url, params, response)
    return response.json()

def job_logs(token, job_id):
  """https://docs.github.com/rest/reference/actions#download-job-logs-for-a-workflow-run"""
  url = f'{GITHUB_API_URL}/actions/jobs/{job_id}/logs'
  headers = {'Accept': 'application/vnd.github+json', 'Authorization': f'token {token}'}
  with requests.get(url, headers=headers, allow_redirects=False,
                    stream=True, timeout=TIMEOUT_LONG) as response:
    logging.info('job_logs: %s response: %s', url, response)
    if response.status_code == 302:
      with requests.get(response.headers['Location'], headers=headers, allow_redirects=False,
                        stream=True, timeout=TIMEOUT_LONG) as get_log_response:
        return get_log_response.content.decode('utf-8')
    else:
      print('no log avaliable')
      return ''
