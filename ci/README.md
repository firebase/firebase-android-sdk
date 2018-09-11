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
  ./ci/fireci/setup.py develop
  ```

- For usage help, see:
  ```
  fireci --help
  ```

