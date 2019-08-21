# Copyright 2018 Google LLC
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
import os

from github import Github

from . import gradle
from . import ci_command
from . import stats


@click.argument('task', required=True, nargs=-1)
@click.option(
    '--gradle-opts',
    default='',
    help='GRADLE_OPTS passed to the gradle invocation.')
@ci_command('gradle')
def gradle_command(task, gradle_opts):
  """Runs the specified gradle commands."""
  gradle.run(*task, gradle_opts=gradle_opts)


@click.option(
    '--app-build-variant',
    type=click.Choice(['debug', 'release']),
    default='release',
    help=
    'App build variant to use while running the smoke Tests. One of release|debug'
)
@click.option(
    '--test-apps-dir',
    '-d',
    multiple=True,
    type=click.Path(exists=True, file_okay=False, resolve_path=True),
    default=['test-apps'],
    help=
    'Directory that contains gradle build with apps to test against. Multiple values are allowed.'
)
@ci_command()
def smoke_tests(app_build_variant, test_apps_dir):
  """Builds all SDKs in release mode and then tests test-apps against them."""
  gradle.run('publishAllToBuildDir')

  cwd = os.getcwd()
  for location in test_apps_dir:
    gradle.run(
        'connectedCheck',
        '-PtestBuildType=%s' % (app_build_variant),
        gradle_opts='-Dmaven.repo.local={}'.format(
            os.path.join(cwd, 'build', 'm2repository')),
        workdir=location,
    )


@click.option('--issue_number', 'issue_number', required=True)
@click.option('--repo_name', 'repo_name', required=True)
@click.option('--auth_token', 'auth_token', required=True)
@ci_command()
def api_information(auth_token, repo_name, issue_number):
  """Comments the api information on the pr"""

  gradle.run('apiInformation')
  dir_suffix = 'build/apiinfo'
  comment_string = ""
  for filename in os.listdir(dir_suffix):
    subproject = filename
    formatted_output_lines = []
    with open(os.path.join(dir_suffix, filename), 'r') as f:
      outputlines = f.readlines()
      for line in outputlines:
        if 'error' in line:
          formatted_output_lines.append(line[line.find('error:'):])
        elif 'warning' in line:
          formatted_output_lines.append(line[line.find('warning:'):])
          
    if formatted_output_lines:
      comment_string += 'The public api surface has changed for the subproject {}:\n'.format(subproject)
      comment_string += ''.join(formatted_output_lines)
      comment_string += '\n\n'
  if comment_string:
    comment_string += ('Please update the api.txt files for the subprojects being affected by this change '
      'by running ./gradlew ${subproject}:generateApiTxtFile. Also perform a major/minor bump accordingly.\n')
    # Comment to github.
    github_client = Github(auth_token)
    repo = github_client.get_repo(repo_name)
    pr = repo.get_pull(int(issue_number))
    pr.create_issue_comment(comment_string)
    exit(1)
