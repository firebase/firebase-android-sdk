# Copyright 2021 Google LLC
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
import github
import os

from fireci import gradle
from fireci import ci_command


@click.option('--issue_number', 'issue_number', required=True)
@click.option('--repo_name', 'repo_name', required=True)
@click.option('--auth_token', 'auth_token', required=True)
@ci_command()
def api_information(auth_token, repo_name, issue_number):
  """Comments the api information on the pr"""

  gradle.run('apiInformation', '--continue', check=False)

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
    github_client = github.Github(auth_token)
    repo = github_client.get_repo(repo_name)
    pr = repo.get_pull(int(issue_number))
    pr.create_issue_comment(comment_string)
    exit(1)
