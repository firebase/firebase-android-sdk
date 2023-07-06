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
import json
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

def main(): 
  logging.getLogger().setLevel(logging.INFO)

  args = parse_cmdline_args()

  gh = github.GitHub(REPO_OWNER, REPO_NAME)

  monthly_summary = get_pervious_report()
  monthly_summary = get_latest_monthly_summary(monthly_summary, gh, args.token)
  print(monthly_summary)
  summary_report = markdown_report(monthly_summary, args.run_id)
  print(summary_report)
  # update_report()


def get_latest_monthly_summary(monthly_summary, gh, token):
  first_day_in_month = datetime.date.today().replace(day=1)
  for _ in range(6):
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
    monthly_summary[last_day_in_month]['failure_jobs'] = {
      job_name: {
        'failure_rate': job['failure_rate'],
        'total_count': job['total_count'],
        'success_count': job['success_count'],
        'failure_count': job['failure_count'],
      } 
      for job_name, job in job_summary.items() if job['failure_rate'] > 0
    }

  return monthly_summary


def markdown_report(monthly_summary, run_id):
  monthly_summary = dict(sorted(monthly_summary.items(), reverse=True))

  markdown_report = "### Monthly Flakiness History \n\n"
  markdown_report += "**[View monthly flakiness logs & download artifacts](https://github.com/firebase/firebase-android-sdk/actions/runs/%s)**\n\n" % run_id

  # List to hold all dates
  dates = [date.strftime('%b %Y') for date in sorted(monthly_summary.keys(), reverse=True)]
  markdown_report += f"| Workflow | {' | '.join(dates)} |\n"
  markdown_report += "| --- |" + " --- |" * len(dates) + "\n"
  # For the workflow, generate the failure rate for each month
  workflow_data = []
  workflow_data = [f"{summary['failure_rate']:.2%} ({summary['failure_count']}/{summary['total_count']})" for summary in monthly_summary.values()]
  markdown_report += f"| Workflow | {' | '.join(workflow_data)} |\n\n"

  # List to hold all dates
  markdown_report += f"| Job Name | {' | '.join(dates)} |\n"
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
  

def get_pervious_report():
  return {} 

  
def parse_cmdline_args():
  parser = argparse.ArgumentParser()
  parser.add_argument('-t', '--token', required=True, help='GitHub access token')
  parser.add_argument('-i', '--run_id', required=True, help='Workflow run id')

  args = parser.parse_args()
  return args


if __name__ == '__main__':
  main()