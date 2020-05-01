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
import logging
import re
import xml.etree.ElementTree as ElementTree

from fireci import ci_command
from fireci import gradle
from fireci import prow_utils
from fireci import uploader

_logger = logging.getLogger('fireci.coverage')


@click.option(
  '--pull-request/--no-pull-request',
  default=False,
  help='When running for pull requests, only coverage of affected SDKs are collected.'
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
def coverage(pull_request, log, metrics_service_url, access_token):
  """Produces and uploads code coverage reports."""

  coverage_task = 'checkCoverageChanged' if pull_request else 'checkCoverage'
  gradle.run(coverage_task, '--continue', check=False)

  test_results = _parse_xml_reports()
  test_report = {'metric': 'Coverage', 'results': test_results, 'log': log}

  note = '''
HTML coverage reports can be produced locally with `./gradlew <product>:checkCoverage`.
Report files are located at `<product-build-dir>/reports/jacoco/`.
'''

  uploader.post_report(test_report, metrics_service_url, access_token, note)


def _parse_xml_reports():
  test_results = []

  xml_reports = glob.glob('./**/reports/jacoco/*.xml', recursive=True)
  _logger.info(f'Found {len(xml_reports)} coverage reports: {xml_reports}')

  for xml_report in xml_reports:
    sdk = re.search(r'([^/]*)\.xml', xml_report).group(1)
    report = ElementTree.parse(xml_report).getroot()
    sdk_coverage = _calculate_coverage(report)
    test_results.append({'sdk': sdk, 'type': '', 'value': sdk_coverage})

    for source_file in report.findall('.//sourcefile'):
      file_name = source_file.attrib['name']
      file_coverage = _calculate_coverage(source_file)
      test_results.append({'sdk': sdk, 'type': file_name, 'value': file_coverage})

  return test_results


def _calculate_coverage(element):
  counter = element.find('counter[@type="LINE"]')
  if counter is not None:
    covered = int(counter.attrib['covered'])
    missed = int(counter.attrib['missed'])
    return covered / (covered + missed)
  return 0
