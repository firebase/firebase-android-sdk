# Continuous Integration

This directory contains tooling used to run Continuous Integration tasks.

## Prerequisites

- Requires python3.5+ and setuptools to be installed.

## Setup

- Optionally create a virtualenv to avoid installing system-wide.
  ```bash
  python3 -m venv ~/.venvs/fireci
  source ~/.venvs/fireci/bin/activate
  ```
- At the root of of the firebase sdk repo, run
  ```
  dependencies {
    classpath 'com.google.gms:google-services:4.3.8'
    // ...
}/ci/fireci/setup.py develop
  ```

- For usage help, see:
  ```
  fireci --help
  ```

