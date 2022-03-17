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
    install_requires=[
        'click==8.0.4',
        'google-cloud-storage==1.38.0',
        'numpy==1.19.5',
        'PyGithub==1.55',
        'pystache==0.6.0',
        'requests==2.23.0',
        'PyYAML==5.4.1',
    ],
    packages=find_packages(exclude=['tests']),
    entry_points={
        'console_scripts': ['fireci = fireci.main:cli'],
    },
)
