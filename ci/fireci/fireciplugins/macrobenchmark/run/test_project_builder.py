# Copyright 2022 Google LLC
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
import pystache
import shutil

from .log_decorator import LogDecorator
from .test_project import TestProject
from .utils import execute
from pathlib import Path
from typing import Any, Dict, List


logger = logging.getLogger('fireci.macrobenchmark')


class TestProjectBuilder:
  def __init__(
      self,
      test_config: Any,
      test_dir: Path,
      template_project_dir: Path,
      product_versions: Dict[str, str],
      changed_modules: List[str],
  ):
    self.test_config = test_config
    self.template_project_dir = template_project_dir
    self.product_versions = product_versions
    self.changed_modules = changed_modules

    self.name = 'test-changed' if changed_modules else 'test-all'
    self.logger = LogDecorator(logger, self.name)
    self.project_dir = test_dir.joinpath(self.name)

  def build(self) -> TestProject:
    self.logger.info(f'Creating test project "{self.name}" ...')

    self._copy_template_project()
    self._flesh_out_mustache_template_files()
    self._download_gradle_wrapper()

    self.logger.info(f'Test project "{self.name}" created at "{self.project_dir}"')
    return TestProject(self.name, self.project_dir, self.logger)

  def _copy_template_project(self):
    shutil.copytree(self.template_project_dir, self.project_dir)
    self.logger.debug(f'Copied project template files into "{self.project_dir}"')

  def _download_gradle_wrapper(self):
    args = ['wrapper', '--gradle-version', '7.5.1', '--project-dir', str(self.project_dir)]
    execute('./gradlew', *args, logger=self.logger)
    self.logger.debug(f'Created gradle wrapper in "{self.project_dir}"')

  def _flesh_out_mustache_template_files(self):
    mustache_context = {
      'm2repository': os.path.abspath('build/m2repository'),
      'plugins': self.test_config.get('plugins', []),
      'traces': self.test_config.get('traces', []),
      'dependencies': [],
    }

    if 'dependencies' in self.test_config:
      for dep in self.test_config['dependencies']:
        if '@' in dep:
          key, version = dep.split('@', 1)
          dependency = {'key': key, 'version': version}
        else:
          dependency = {'key': dep, 'version': self.product_versions[dep]}
        if not self.changed_modules or dep in self.changed_modules:
          mustache_context['dependencies'].append(dependency)

    renderer = pystache.Renderer()
    mustaches = self.project_dir.rglob('**/*.mustache')
    for mustache in mustaches:
      self.logger.debug(f'Processing template file: {mustache}')
      result = renderer.render_path(mustache, mustache_context)
      original_name = str(mustache)[:-9]  # TODO(yifany): .removesuffix('.mustache') w/ python 3.9+
      with open(original_name, 'w') as file:
        file.write(result)
