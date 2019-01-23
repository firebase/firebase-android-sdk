#!/usr/bin/env python3
#
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

import os
from setuptools import find_packages, setup

os.chdir(os.path.abspath(os.path.dirname(__file__)))

requires = []

setup(
    name='fireci',
    version='0.1',
    # this is a temporary measure until opencensus 0.2 release is out.
    dependency_links=[
        'https://github.com/census-instrumentation/opencensus-python/tarball/master#egg=opencensus'
    ],
    install_requires=[
        'click==7.0',
        'opencensus',
        'google-cloud-monitoring==0.31.1',
    ],
    packages=find_packages(exclude=['tests']),
    entry_points={
        'console_scripts': ['fireci = fireci.main:cli'],
    },
)
