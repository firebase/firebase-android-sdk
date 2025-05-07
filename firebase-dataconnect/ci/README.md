# Firebase Data Connect Android SDK Continuous Integration Scripts

These scripts are used by GitHub Actions.

There are GitHub Actions workflows that verify code formatting, lint checks, type annotations,
and running unit tests of code in this directory. Although they are not "required" checks, it
is requested to wait for these checks to pass. See `dataconnect.yaml`.

The minimum required Python version (at the time of writing, April 2025) is 3.13.
See `pyproject.toml` for the most up-to-date requirement.

Before running the scripts, install the required dependencies by running:

```
pip install -r requirements.txt
```

Then, run all of these presubmit checks by running the following command:

```
ruff check --fix && ruff format && pyright && pytest && echo 'SUCCESS!!!!!!!!!!!!!!!'
```
