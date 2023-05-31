# Copyright 2023 Google LLC
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

from textwrap import dedent
from fireci import gradle
from fireci import ci_command

@click.option(
  '--changelogs',
  '-c',
  required=True,
  help="A space separated list of changelog files to generate release notes for."
)
@click.option(
  '--output',
  '-o',
  required=True,
  help="The file to write the comment to."
)
@ci_command()
def changelog_comment(changelogs, output):
  """Generates and formats release notes to a pretty comment for GitHub."""

  gradle.run('makeReleaseNotes')

  release_notes = [*map(convert_release_note, changelogs.rsplit(" "))]
  comment_string = dedent("""
  ## Release note changes
  The following release notes were modified. Please ensure they look correct.
  <details>
  <summary>Release Notes</summary>
  {0}
  </details>
  """).format(''.join(release_notes))

  with open(output, 'w') as f:
    f.write(comment_string.strip())

  exit(0)

def convert_release_note(changelogFile):
  path = changelogFile.rsplit("/", 1)[0]
  with open(os.path.join(path, "build/tmp/makeReleaseNotes/release_notes.md"), 'r') as f:
    return dedent("""
    <details>
    <summary>{0}</summary>
    
    ```markdown
    {1}
    ```
    </details>
    """).format(path.replace("/", ":"), f.read())
