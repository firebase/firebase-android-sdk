import argparse
import dataclasses
import sys

def main() -> None:
  pass


if __name__ == "__main__":
  try:
    main()
  except KeyboardInterrupt:
    print("ERROR: application terminated by keyboard interrupt", file=sys.stderr)
    sys.exit(1)
