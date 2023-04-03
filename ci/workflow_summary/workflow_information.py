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
import os 


'''A utility collecting ci_test.yml workflow failure logs.

Usage:

  python workflow_information.py --token ${github_toke} --workflow_name ${workflow_name}

'''

def main():
  logging.getLogger().setLevel(logging.INFO)

  args = parse_cmdline_args()
  logging.info(args)

  gh = github.GitHub(args.repo_owner, args.repo_name)

  # location for all artifacts
  if args.folder:
    file_folder = os.path.normpath(args.folder)
  else:
    file_folder = os.path.normpath(datetime.datetime.utcnow().strftime('%Y-%m-%d+%H:%M:%S'))
  if not os.path.exists(file_folder):
    os.makedirs(file_folder)

  workflow_summary = get_workflow_summary(gh, args)
  workflow_summary_file_path = os.path.join(file_folder, 'workflow_summary.json')
  with open(workflow_summary_file_path, 'w') as f:
    json.dump(workflow_summary, f)
  logging.info(f'Workflow summary has been write to {workflow_summary_file_path}\n')

  job_summary = get_job_summary(workflow_summary)
  job_summary_file_path = os.path.join(file_folder, 'job_summary.json')
  with open(job_summary_file_path, 'w') as f:
    json.dump(job_summary, f)
  logging.info(f'Job summary has been write to {job_summary_file_path}\n')

  workflow_summary_report = f"{datetime.datetime.utcnow()}\n{args}\n\n"
  workflow_summary_report += generate_summary_report(workflow_summary, job_summary)
  report_file_path = os.path.join(file_folder, 'workflow_summary_report.txt')
  with open(report_file_path, 'w') as f:
    f.write(workflow_summary_report)
  logging.info(f'Workflow summary report has been write to {report_file_path}\n')


def get_workflow_summary(gh, args):  
  token = args.token
  workflow_name = args.workflow_name
  # https://docs.github.com/en/search-github/getting-started-with-searching-on-github/understanding-the-search-syntax#query-for-dates
  days = args.days
  current_datetime = datetime.datetime.utcnow()
  since_datetime = current_datetime - datetime.timedelta(days=days)
  created = '>' + since_datetime.strftime('%Y-%m-%dT%H:%M:%SZ')

  workflow_summary = {'workflow_name': workflow_name, 
                    'total_count': 0, 
                    'success_count': 0, 
                    'failure_count': 0, 
                    'created': created,
                    'workflow_runs': []}

  logging.info('START collecting workflow run data\n')
  workflow_page = 0
  per_page = 100 # max 100
  list_workflows_params = {'status': 'completed', 'created': created, 'page': workflow_page, 'per_page': per_page}
  if args.event:
    list_workflows_params['event'] = args.event
  if args.actor:
    list_workflows_params['actor'] = args.actor
  if args.branch:
    list_workflows_params['branch'] = args.branch

  while True:
    workflow_page += 1
    list_workflows_params['page'] = workflow_page
    workflows = gh.list_workflows(token, workflow_name, list_workflows_params)

    if 'workflow_runs' not in workflows or not workflows['workflow_runs']:
      break

    for workflow in workflows['workflow_runs']:
      if workflow['conclusion'] in ['success', 'failure']:
        workflow_summary['workflow_runs'].append({'workflow_id': workflow['id'], 'conclusion': workflow['conclusion'],
                                                  'head_branch': workflow['head_branch'], 'actor': workflow['actor']['login'], 
                                                  'created_at': workflow['created_at'], 'updated_at': workflow['updated_at'], 
                                                  'run_started_at': workflow['run_started_at'], 'run_attempt': workflow['run_attempt'], 
                                                  'html_url': workflow['html_url'], 'jobs_url': workflow['jobs_url'], 
                                                  'jobs': {'total_count': 0, 'success_count': 0, 'failure_count': 0,  'job_runs': []}})
        workflow_summary['total_count']  += 1
        if workflow['conclusion'] == 'success':
          workflow_summary['success_count'] += 1 
        else: 
          workflow_summary['failure_count'] += 1

  logging.info('END collecting workflow run data\n')

  logging.info('START collecting job data by workflow run\n')
  for workflow_run in workflow_summary['workflow_runs']:
    get_workflow_jobs(gh, args, workflow_run)
  logging.info('END collecting job data by workflow run\n')

  return workflow_summary

def get_workflow_jobs(gh, args, workflow_run):
  workflow_jobs = workflow_run['jobs']
  job_page = 0
  while True:
    job_page += 1
    list_jobs_params = {'filter': args.jobs, 'per_page': 100, 'page': job_page} # per_page: max 100
    jobs = gh.list_jobs(args.token, workflow_run['workflow_id'], list_jobs_params)

    if 'jobs' not in jobs or jobs['total_count'] < job_page * 100:
      break

    for job in jobs['jobs']:
      workflow_jobs['job_runs'].append({'job_id': job['id'], 'job_name': job['name'], 'conclusion': job['conclusion'], 
                                        'created_at': job['created_at'], 'started_at': job['started_at'], 'completed_at': job['completed_at'],
                                        'run_attempt': job['run_attempt'], 'html_url': job['html_url']})
      if job['conclusion'] in ['success', 'failure']:
        workflow_jobs['total_count'] += 1
      if job['conclusion'] == 'success':
        workflow_jobs['success_count'] += 1 
      else: 
        workflow_jobs['failure_count'] += 1


def get_job_summary(workflow_summary):
  logging.info('START gathering job information by job name\n')
  job_summary = {}
  for workflow_run in workflow_summary['workflow_runs']:
    for job_run in workflow_run['jobs']['job_runs']:
      job_name = job_run['job_name']
      if job_name not in job_summary:
        job_summary[job_name] = {'total_count': 0, 
                                 'success_count': 0, 
                                 'failure_count': 0, 
                                 'failure_jobs': []}

      job = job_summary[job_name]  
      job['total_count'] += 1
      if job_run['conclusion'] == 'success':
        job['success_count'] += 1
      else:
        job['failure_count'] += 1
        job['failure_jobs'].append(job_run)

  for job_name in job_summary:
    total_count = job_summary[job_name]['total_count'] 
    failure_count = job_summary[job_name]['failure_count']    
    job_summary[job_name]['failure_rate'] = float(failure_count/total_count)

  job_summary=dict(sorted(job_summary.items(), key=lambda item: item[1]['failure_rate'], reverse=True))

  logging.info('END gathering job information by job name\n')
  return job_summary


def generate_summary_report(workflow_summary, job_summary):
  report_content = ''

  workflow_name = workflow_summary['workflow_name']
  total_count = workflow_summary['total_count'] 
  success_count = workflow_summary['success_count'] 
  failure_count = workflow_summary['failure_count'] 
  failure_rate = float(failure_count/total_count)
  report_content += f"Workflow '{workflow_name}' Report: \n Workflow Failure Rate:{failure_rate:.2%} \n Workflow Total Count: {total_count} (success: {success_count}, failure: {failure_count})\n\n"

  report_content += workflow_runtime_report(workflow_summary)

  report_content += 'Job Failure Report:\n'
  for job_name in job_summary:
    job = job_summary[job_name]
    if job['failure_rate'] > 0:
      report_content += f"{job_name}:\n Failure Rate:{job['failure_rate']:.2%}\n Total Count: {job['total_count']} (success: {job['success_count']}, failure: {job['failure_count']})\n"
  
  logging.info(report_content)
  return report_content
      

def workflow_runtime_report(workflow_summary):     
  for workflow in workflow_summary['workflow_runs']:
    created_at = datetime.datetime.strptime(workflow['created_at'], '%Y-%m-%dT%H:%M:%SZ')
    updated_at = datetime.datetime.strptime(workflow['updated_at'], '%Y-%m-%dT%H:%M:%SZ')
    workflow['runtime'] = (updated_at - created_at).total_seconds()
    
  success_without_rerun = [w['runtime'] for w in workflow_summary['workflow_runs'] if w['run_attempt'] == 1 and w['conclusion'] == 'success']
  failure_without_rerun = [w['runtime'] for w in workflow_summary['workflow_runs'] if w['run_attempt'] == 1 and w['conclusion'] == 'failure']
  without_rerun = success_without_rerun + failure_without_rerun
  with_rerun = [w['runtime'] for w in workflow_summary['workflow_runs'] if w['run_attempt'] > 1]

  runtime_report = 'Workflow Runtime Report:\n'
  if without_rerun:
    runtime_report += f"{len(without_rerun)} workflow runs finished without rerun, the average running time: {datetime.timedelta(seconds=sum(without_rerun)/len(without_rerun))}\n"
    runtime_report += 'Including:\n'
    if success_without_rerun:
      runtime_report += f" {len(success_without_rerun)} passed workflow runs, with average running time: {datetime.timedelta(seconds=sum(success_without_rerun)/len(success_without_rerun))}\n"
    if failure_without_rerun:
      runtime_report += f" {len(failure_without_rerun)} failed workflow runs, with average running time: {datetime.timedelta(seconds=sum(failure_without_rerun)/len(failure_without_rerun))}\n\n"

  if with_rerun:
    runtime_report += f"{len(with_rerun)} runs finished with rerun, the average running time: {datetime.timedelta(seconds=sum(with_rerun)/len(with_rerun))}\n"
    runtime_report += f"The running time for each workflow reruns are:\n {[str(datetime.timedelta(seconds=x)) for x in with_rerun]}\n\n"

  return runtime_report


def parse_cmdline_args():
  parser = argparse.ArgumentParser(description='Collect certain Github workflow information and calculate failure rate.')
  parser.add_argument('-o', '--repo_owner', default='firebase', help='GitHub repo owner')
  parser.add_argument('-n', '--repo_name', default='firebase-android-sdk', help='GitHub repo name')
  parser.add_argument('-t', '--token', required=True, help='GitHub access token')

  parser.add_argument('-w', '--workflow_name', default='ci_tests.yml', help='Workflow filename to run')
  # By default, the artifacts and log files generated by workflows are retained for 90 days before they are automatically deleted.
  # https://docs.github.com/en/organizations/managing-organization-settings/configuring-the-retention-period-for-github-actions-artifacts-and-logs-in-your-organization
  parser.add_argument('-d', '--days', type=int, default=90, help='Filter workflows that running in past -d days')
  parser.add_argument('-b', '--branch', help='Filter branch name that workflows run against, default is all branches')
  parser.add_argument('-a', '--actor', help='Filter someone\'s workflow runs, default is actors')
  parser.add_argument('-e', '--event', choices=['push', 'pull_request', 'issue'], help='Filter workflows trigger event, default is all events')
  parser.add_argument('-j', '--jobs', default='all', choices=['latest', 'all'], help='Filter workflows jobs, default is the last job and does not include all jobs (does not include rerun jobs)')

  parser.add_argument('-f', '--folder', help='Workflow and job information will be store here, default is current datatime')

  args = parser.parse_args()
  return args


if __name__ == '__main__':
  main()
