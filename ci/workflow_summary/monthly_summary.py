# Copyright 2023 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the 'License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an 'AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import github
import os
import re
import json
import zipfile
import datetime
import argparse
import logging
import workflow_information


'''A utility collecting ci_test.yml workflow failure logs.

Usage:

  python workflow_information.py --token ${github_toke} --workflow_name ${workflow_name}

'''

REPO_OWNER = 'firebase'
REPO_NAME = 'firebase-android-sdk'
EXCLUDE_JOB_LIST = ['Determine changed modules','Unit Tests (matrix)','Publish Tests Results','Unit Test Results','Instrumentation Tests','Unit Tests']

REPORT_LABEL = 'flakiness-history'
REPORT_TITLE = 'Monthly Flakiness History'

def main(): 
  logging.getLogger().setLevel(logging.INFO)

  args = parse_cmdline_args()
  token = args.token
  output_folder = os.path.normpath(args.folder)
  if not os.path.exists(output_folder):
    os.makedirs(output_folder)
  gh = github.GitHub(REPO_OWNER, REPO_NAME)

  issue_number = get_issue_number(gh)
  monthly_summary = get_pervious_report(gh, token, issue_number)
  monthly_summary = get_latest_monthly_summary(gh, token, monthly_summary, output_folder)
  print(monthly_summary)
  summary_report = markdown_report(monthly_summary, args.run_id)
  print(summary_report)
  update_report(gh, token, issue_number, summary_report)


def get_latest_monthly_summary(gh, token, monthly_summary, output_folder):
  first_day_in_month = datetime.date.today().replace(day=1)
  month = 1 if monthly_summary else 6
  for i in range(month):
    last_day_in_month = first_day_in_month - datetime.timedelta(days=1)
    first_day_in_month = last_day_in_month.replace(day=1)
    first_day_in_month = first_day_in_month

    from_time = datetime.datetime.combine(first_day_in_month, datetime.time.min)
    to_time = datetime.datetime.combine(last_day_in_month, datetime.time.max)
    created = from_time.strftime('%Y-%m-%dT%H:%M:%SZ') + '..' + to_time.strftime('%Y-%m-%dT%H:%M:%SZ')

    workflow_summary = workflow_information.get_workflow_summary(gh=gh, token=token, created=created, workflow_name='ci_tests.yml', event='push', branch='master')
    failure_rate = float(workflow_summary['failure_count']/workflow_summary['total_count'])
    monthly_summary[last_day_in_month] = {
      'failure_rate': failure_rate,
      'total_count': workflow_summary['total_count'],
      'success_count': workflow_summary['success_count'],
      'failure_count': workflow_summary['failure_count'],
      'failure_jobs':{}
    }

    job_summary = workflow_information.get_job_summary(workflow_summary)
    if i == 0:
      job_summary_file_path = os.path.join(output_folder, 'job_summary.json')
      with open(job_summary_file_path, 'w') as f:
        json.dump(job_summary, f)
      logging.info(f'Job summary has been write to {job_summary_file_path}\n')

    monthly_summary[last_day_in_month]['failure_jobs'] = {
      job_name: {
        'failure_rate': job['failure_rate'],
        'total_count': job['total_count'],
        'success_count': job['success_count'],
        'failure_count': job['failure_count'],
      } 
      for job_name, job in job_summary.items() if job['failure_rate'] > 0
    }

  monthly_summary_file_path = os.path.join(output_folder, 'monthly_summary.json')
  with open(monthly_summary_file_path, 'w') as f:
    json.dump({date_to_string(key): value for key, value in monthly_summary.items()}, f)
  logging.info(f'Job summary has been write to {monthly_summary_file_path}\n')

  return monthly_summary


def markdown_report(monthly_summary, run_id):
  monthly_summary = dict(sorted(monthly_summary.items(), reverse=True))

  markdown_report = f"## {REPORT_TITLE} \n\n"
  markdown_report += f"**[Click to View and Download the Artifacts for Last Month's Flakiness Logs](https://github.com/firebase/firebase-android-sdk/actions/runs/{run_id})**\n\n"
  markdown_report += "*** \n\n"

  # List to hold all dates
  dates = [date.strftime('%b %Y') for date in sorted(monthly_summary.keys(), reverse=True)]
  markdown_report += "#### Workflow Flakiness History \n\n"
  markdown_report += f"| Workflow | {' | '.join(dates)} |\n"
  markdown_report += "| --- |" + " --- |" * len(dates) + "\n"
  # For the workflow, generate the failure rate for each month
  workflow_data = []
  workflow_data = [f"{summary['failure_rate']:.2%} ({summary['failure_count']}/{summary['total_count']})" for summary in monthly_summary.values()]
  markdown_report += f"| ci_tests.yml | {' | '.join(workflow_data)} |\n\n"
  markdown_report += "*** \n\n"

  # List to hold all dates
  markdown_report += "#### Job Flakiness History \n\n"
  markdown_report += "This table presents two categories of job failures: \n"
  markdown_report += "1) jobs that failed in the last month \n"
  markdown_report += "2) jobs that had a high failure rate (exceeding 10% on average) over the past six months \n\n"
  markdown_report += f"| Job | {' | '.join(dates)} |\n"
  markdown_report += "| --- |" + " --- |" * len(dates) + "\n"
  # Sorted Jobs for the latest month
  latest_month = next(iter(monthly_summary.values()))
  sorted_jobs = sorted(latest_month['failure_jobs'], key=lambda job: latest_month['failure_jobs'][job]['failure_rate'], reverse=True)
  # High Failure Jobs in 6 months
  all_jobs = {job for summary in list(monthly_summary.values())[:6] for job in summary['failure_jobs']}
  all_jobs.difference_update(set(sorted_jobs))
  avg_failure_rates = {job: sum([summary['failure_jobs'][job]['failure_rate'] for summary in list(monthly_summary.values())[:6] if job in summary['failure_jobs']])/6 for job in all_jobs}
  high_failure_jobs = {job: rate for job, rate in avg_failure_rates.items() if rate >= 0.1}
  # Combine Last Month Failure Jobs and High Failure Rate Jobs
  all_jobs = sorted_jobs + sorted(high_failure_jobs, key=high_failure_jobs.get, reverse=True)
  # Loop through All Jobs
  for job_name in all_jobs:
    if job_name in EXCLUDE_JOB_LIST:
      continue
    job_data = []
    for _, one_month_summary in sorted(monthly_summary.items(), reverse=True):
      one_month_job_summary = one_month_summary['failure_jobs'].get(job_name)
      if one_month_job_summary:
        job_data.append(f"{one_month_job_summary['failure_rate']:.2%} ({one_month_job_summary['failure_count']}/{one_month_job_summary['total_count']})")
      else:
        job_data.append('N/A')
    markdown_report += f"| {job_name} | {' | '.join(job_data)} |\n"

  return markdown_report
  

def get_issue_number(gh):
  issues = gh.search_issues_by_label(REPO_OWNER, REPO_NAME, REPORT_LABEL)
  for issue in issues:
    if issue['title'] == REPORT_TITLE:
      return issue['number']


def get_pervious_report(gh, token, issue_number):
  pervious_monthly_summary = {}
  if issue_number:
    issue_body = gh.get_issue_body(token, issue_number)
    logging.info(issue_body)
    # The regex pattern to match "run_id" in the URL
    pattern = r"https://github.com/firebase/firebase-android-sdk/actions/runs/(\d+)"
    # Use re.search() to search for the pattern
    match = re.search(pattern, issue_body)
    if match:
      run_id = match.group(1)
      artifacts = gh.list_artifacts(token, run_id)
      for artifact in artifacts:
        if artifact['name'] == 'output_logs':
          gh.download_artifact(token, artifact['id'], 'artifact.zip')
          # extract all the files
          with zipfile.ZipFile('artifact.zip', 'r') as zip_ref:
            zip_ref.extractall('artifact')
            pervious_summary_file = os.path.join('artifact', 'monthly_summary.json')
            if os.path.exists(pervious_summary_file):
              with open(pervious_summary_file, 'r') as f:
                loaded_data = json.load(f)
                logging.info(loaded_data)
                pervious_monthly_summary = {string_to_date(key): value for key, value in loaded_data.items()}

  return pervious_monthly_summary


def update_report(gh, token, issue_number, summary_report):
  if not issue_number:
    gh.create_issue(token, REPORT_TITLE, REPORT_LABEL, summary_report)
  else:
    gh.update_issue_comment(token, issue_number, summary_report)


# Function to convert date to string
def date_to_string(date):
    return date.strftime('%Y-%m-%d')


# Function to convert string to date
def string_to_date(date_string):
    return datetime.datetime.strptime(date_string, '%Y-%m-%d').date()


def parse_cmdline_args():
  parser = argparse.ArgumentParser()
  parser.add_argument('-t', '--token', required=True, help='GitHub access token')
  parser.add_argument('-i', '--run_id', required=True, help='Workflow run id')
  parser.add_argument('-f', '--folder', required=True, help='Folder generated by workflow_information.py. Test logs also locate here.')

  args = parser.parse_args()
  return args


if __name__ == '__main__':
  main()