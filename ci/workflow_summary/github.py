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
import json
import shutil

RETRIES = 3
BACKOFF = 5
RETRY_STATUS = (403, 500, 502, 504)
TIMEOUT = 5
TIMEOUT_LONG = 20

class GitHub:

  def __init__(self, owner, repo):
    self.github_api_url = f'https://api.github.com/repos/{owner}/{repo}'

  def list_workflows(self, token, workflow_id, params):
    """https://docs.github.com/en/rest/actions/workflow-runs#list-workflow-runs-for-a-workflow"""
    url = f'{self.github_api_url}/actions/workflows/{workflow_id}/runs'
    headers = {'Accept': 'application/vnd.github+json', 'Authorization': f'token {token}'}
    with requests.get(url, headers=headers, params=params,
                      stream=True, timeout=TIMEOUT_LONG) as response:
      logging.info('list_workflows: %s, params: %s, response: %s', url, params, response)
      return response.json()

  def list_jobs(self, token, run_id, params):
    """https://docs.github.com/en/rest/actions/workflow-jobs#list-jobs-for-a-workflow-run"""
    url = f'{self.github_api_url}/actions/runs/{run_id}/jobs'
    headers = {'Accept': 'application/vnd.github+json', 'Authorization': f'token {token}'}
    with requests.get(url, headers=headers, params=params,
                      stream=True, timeout=TIMEOUT_LONG) as response:
      logging.info('list_jobs: %s, params: %s, response: %s', url, params, response)
      return response.json()

  def job_logs(self, token, job_id):
    """https://docs.github.com/rest/reference/actions#download-job-logs-for-a-workflow-run"""
    url = f'{self.github_api_url}/actions/jobs/{job_id}/logs'
    headers = {'Accept': 'application/vnd.github+json', 'Authorization': f'token {token}'}
    with requests.get(url, headers=headers, allow_redirects=False,
                      stream=True, timeout=TIMEOUT_LONG) as response:
      logging.info('job_logs: %s response: %s', url, response)
      if response.status_code == 302:
        with requests.get(response.headers['Location'], headers=headers, allow_redirects=False,
                          stream=True, timeout=TIMEOUT_LONG) as get_log_response:
          return get_log_response.content.decode('utf-8')
      else:
        logging.info('no log avaliable')
        return ''
      
  def create_issue(self, token, title, label, body):
    """Create an issue: https://docs.github.com/en/rest/reference/issues#create-an-issue"""
    url = f'{self.github_api_url}/issues'
    headers = {'Accept': 'application/vnd.github.v3+json', 'Authorization': f'token {token}'}
    data = {'title': title, 'labels': [label], 'body': body}
    with requests.post(url, headers=headers, data=json.dumps(data), timeout=TIMEOUT) as response:
      logging.info("create_issue: %s response: %s", url, response)
      return response.json()

  def get_issue_body(self, token, issue_number):
    """https://docs.github.com/en/rest/reference/issues#get-an-issue-comment"""
    url = f'{self.github_api_url}/issues/{issue_number}'
    headers = {'Accept': 'application/vnd.github.v3+json', 'Authorization': f'token {token}'}
    with requests.get(url, headers=headers, timeout=TIMEOUT) as response:
      logging.info("get_issue_body: %s response: %s", url, response)
      return response.json()["body"]
      
  def update_issue_comment(self, token, issue_number, comment):
    """Update an issue: https://docs.github.com/en/rest/reference/issues#update-an-issue"""
    url = f'{self.github_api_url}/issues/{issue_number}'
    headers = {'Accept': 'application/vnd.github.v3+json', 'Authorization': f'token {token}'}
    with requests.patch(url, headers=headers, data=json.dumps({'body': comment}), timeout=TIMEOUT) as response:
      logging.info("update_issue: %s response: %s", url, response)

  def search_issues_by_label(self, owner, repo, label):
    """https://docs.github.com/en/rest/reference/search#search-issues-and-pull-requests"""
    url = f'https://api.github.com/search/issues?q=repo:{owner}/{repo}+label:"{label}"+is:issue'
    headers = {'Accept': 'application/vnd.github.v3+json'}
    with requests.get(url, headers=headers, timeout=TIMEOUT) as response:
      logging.info("search_issues_by_label: %s response: %s", url, response)
      return response.json()["items"]
    
  def list_artifacts(self, token, run_id):
    """https://docs.github.com/en/rest/reference/actions#list-workflow-run-artifacts"""
    url = f'{self.github_api_url}/actions/runs/{run_id}/artifacts'
    headers = {'Accept': 'application/vnd.github.v3+json', 'Authorization': f'token {token}'}
    with requests.get(url, headers=headers, timeout=TIMEOUT) as response:
      logging.info("list_artifacts: %s response: %s", url, response)
      return response.json()["artifacts"]


  def download_artifact(self, token, artifact_id, output_path=None):
    """https://docs.github.com/en/rest/reference/actions#download-an-artifact"""
    url = f'{self.github_api_url}/actions/artifacts/{artifact_id}/zip'
    headers = {'Accept': 'application/vnd.github.v3+json', 'Authorization': f'token {token}'}
    with requests.get(url, headers=headers, stream=True, timeout=TIMEOUT_LONG) as response:
      logging.info("download_artifact: %s response: %s", url, response)
      if output_path:
        with open(output_path, 'wb') as file:
            shutil.copyfileobj(response.raw, file)
      elif response.status_code == 200:
        return response.content
    return None
