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

import logging
import os
import subprocess

_logger = logging.getLogger('fireci.prow_utils')


def prow_job_log_link():
  """Returns the link to the log of the current prow job."""
  job_name = os.getenv('JOB_NAME')
  job_type = os.getenv('JOB_TYPE')
  build_id = os.getenv('BUILD_ID')
  repo_owner = os.getenv('REPO_OWNER')
  repo_name = os.getenv('REPO_NAME')
  pull_number = os.getenv('PULL_NUMBER')

  domain = "android-ci.firebaseopensource.com"
  bucket = "android-ci"

  dir_pre_submit = f'pr-logs/pull/{repo_owner}_{repo_name}/{pull_number}'
  dir_post_submit = "logs"
  directory = dir_pre_submit if job_type == 'presubmit' else dir_post_submit
  path = f'{job_name}/{build_id}'

  return f'https://{domain}/view/gcs/{bucket}/{directory}/{path}'


def gcloud_identity_token():
  """Returns an identity token with the current gcloud service account."""
  result = subprocess.run(['gcloud', 'auth', 'print-identity-token'], stdout=subprocess.PIPE, check=True)
  return result.stdout.decode('utf-8').strip()
