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
import requests
import subprocess
import xml.etree.ElementTree as ElementTree

from fireci import ci_command
from fireci import gradle

_logger = logging.getLogger('fireci.coverage')


def prow_job_log_link():
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
  result = subprocess.run(['gcloud', 'auth', 'print-identity-token'], stdout=subprocess.PIPE, check=True)
  return result.stdout.decode('utf-8').strip()


@click.option(
  '--gradle-task',
  default='checkCoverage',
  help='The gradle task, which collects coverage for affected or all products.'
)
@click.option(
  '--log',
  default=prow_job_log_link,
  help='The link to the log of the prow job, which runs this coverage check.'
)
@click.option(
  '--metrics-service-url',
  envvar='METRICS_SERVICE_URL',
  help='The URL to the metrics service, which persists data and calculates diff.'
)
@click.option(
  '--access-token',
  default=gcloud_identity_token,
  help='The access token, used to authorize http requests to the metrics service.'
)
@ci_command()
def coverage(gradle_task, log, metrics_service_url, access_token):
  """Produces and uploads code coverage reports."""

  gradle.run(gradle_task, '--continue')

  test_results = parse_xml_reports()
  test_report = {'metric': 'Coverage', 'results': test_results, 'log': log}

  post_report(metrics_service_url, access_token, test_report)


def parse_xml_reports():
  test_results = []

  xml_reports = glob.glob('./**/reports/jacoco/*.xml', recursive=True)
  _logger.info(f'Found {len(xml_reports)} coverage reports: {xml_reports}')

  for xml_report in xml_reports:
    sdk = re.search(r'([^/]*)\.xml', xml_report).group(1)
    report = ElementTree.parse(xml_report).getroot()
    sdk_coverage = calculate_coverage(report)
    test_results.append({'sdk': sdk, 'type': '', 'value': sdk_coverage})

    for source_file in report.findall('.//sourcefile'):
      file_name = source_file.attrib['name']
      file_coverage = calculate_coverage(source_file)
      test_results.append({'sdk': sdk, 'type': file_name, 'value': file_coverage})

  return test_results


def calculate_coverage(element):
  counter = element.find('counter[@type="LINE"]')
  if counter is not None:
    covered = int(counter.attrib['covered'])
    missed = int(counter.attrib['missed'])
    return covered / (covered + missed)
  return 0


def post_report(metrics_service_url, access_token, test_report):
  endpoint = construct_request_endpoint()
  headers = {'Authorization': f'Bearer {access_token}', 'Content-Type': 'application/json'}
  data = json.dumps(test_report)

  _logger.info('Posting to the metrics service ...')
  _logger.info(f'Request endpoint: {endpoint}')
  _logger.info(f'Request data: {data}')

  request_url = f'{metrics_service_url}{endpoint}'
  result = requests.post(request_url, data=data, headers=headers)

  _logger.info(f'Response: {result.text}')


def construct_request_endpoint():
  repo_owner = os.getenv('REPO_OWNER')
  repo_name = os.getenv('REPO_NAME')
  branch = os.getenv('PULL_BASE_REF')
  base_commit = os.getenv('PULL_BASE_SHA')
  head_commit = os.getenv('PULL_PULL_SHA')
  pull_request = os.getenv('PULL_NUMBER')

  commit = head_commit if head_commit else base_commit

  endpoint = f'/repos/{repo_owner}/{repo_name}/commits/{commit}/reports?branch={branch}'
  if pull_request:
    endpoint += f'&pull_request={pull_request}&base_commit={base_commit}'

  return endpoint
