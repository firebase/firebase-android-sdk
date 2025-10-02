# Continuous Integration

This directory contains tooling used to run Continuous Integration tasks.

## Prerequisites

- Requires python3.9+ and setuptools to be installed.

## Setup

- Optionally create a virtualenv to avoid installing system-wide.
  ```bash
  python3 -m venv ~/.venvs/fireci
  source ~/.venvs/fireci/bin/activate
  ```
- At the root of the firebase sdk repo, run

  ```
  pip3 install -e ./ci/fireci/
  ```

- For usage help, see:
  ```
  fireci --help
  ```

## Uninstall

If you run into any issues and need to re-install, or uninstall the package, you can do so by
uninstalling the `fireci` package.

```shell
pip3 uninstall fireci -y
```

## Debug

By default, if you're not running `fireci` within the context of CI, the minimum log level is set to
`INFO`.

To manually set the level to `DEBUG`, you can use the `--debug` flag.

```shell
fireci --debug clean
```

> ![NOTE] The `--debug` flag must come _before_ the command.
