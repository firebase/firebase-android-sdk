import argparse
import dataclasses
import pathlib
import sys


def main() -> None:
  parser = MyArgumentParser()
  args = parser.parse_args()
  print(args)


class MyArgumentParser(argparse.ArgumentParser):
  """Argument parser for local_subprojects.py."""

  def __init__(self, *args, **kwargs) -> None:
    super().__init__(*args, **kwargs)

    subparsers = self.add_subparsers(
      dest="subcommand",
      required=True,
      parser_class=argparse.ArgumentParser,
    )

    default_gradle_project_dir = pathlib.Path.cwd()
    default_local_subprojects_dir = pathlib.Path.cwd() / "ci" / "local_subprojects"

    # Subcommand: calculate
    calculate_parser = subparsers.add_parser(
      "calculate",
      help="Calculate local subprojects for gradle submodules.",
      description="Calculate local subprojects for gradle submodules.",
    )
    calculate_parser.add_argument(
      "submodules",
      nargs="*",
      help="The gradle submodules whose local subprojects to calculate; "
      "if zero are specified then all subprojects listed in subprojects.cfg "
      "have their local subprojects calculated.",
    )
    calculate_parser.add_argument(
      "-p",
      "--project-dir",
      type=pathlib.Path,
      default=default_gradle_project_dir,
      help="The directory of the root gradle project, the directory containing "
      "subprojects.cfg. If not specified then the current directory is used. "
      "(%(default)s)",
    )
    calculate_parser.add_argument(
      "-o",
      "--output-dir",
      type=pathlib.Path,
      default=default_local_subprojects_dir,
      help="The directory into which the calculated local subprojects file is to be written; "
      "if not specified then a 'local_subprojects' directory relative to the "
      "current directory is used (%(default)s).",
    )

    # Subcommand: apply
    apply_parser = subparsers.add_parser(
      "apply",
      help="Apply local subprojects for a gradle submodule.",
      description="Apply local subprojects for a gradle submodule.",
    )
    apply_parser.add_argument(
      "submodule",
      help="The gradle submodule whose local subprojects to apply.",
    )
    apply_parser.add_argument(
      "-p",
      "--project-dir",
      type=pathlib.Path,
      default=default_gradle_project_dir,
      help="The directory of the root gradle project, the directory to which "
      "subprojects.local.cfg is to be written. If not specified then the "
      "current directory is used. (%(default)s)",
    )
    apply_parser.add_argument(
      "-s",
      "--local-subprojects-dir",
      type=pathlib.Path,
      default=default_local_subprojects_dir,
      help="The directory into which the calculated local subprojects file is read; "
      "if not specified then a 'local_subprojects' directory relative to the "
      "current directory is used (%(default)s).",
    )


if __name__ == "__main__":
  try:
    main()
  except KeyboardInterrupt:
    print("ERROR: application terminated by keyboard interrupt", file=sys.stderr)
    sys.exit(1)
