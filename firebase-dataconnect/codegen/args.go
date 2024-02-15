package main

import (
	"errors"
	"flag"
	"os"
)

type ParsedCommandLineArguments struct {
	DestDir        string
	PreludeDir     string
	SchemaFile     string
	OperationsFile string
}

func ParseCommandLineArguments() (ParsedCommandLineArguments, error) {
	flagSet := flag.NewFlagSet(os.Args[0], flag.ContinueOnError)
	parsedCommandLineArguments := ParsedCommandLineArguments{}

	destDir := flagSet.String(
		"dest_dir",
		"",
		"The directory into which to write the output files. "+
			"If not specified or the empty string, then the current directory is used.")

	preludeDir := flagSet.String(
		"prelude_dir",
		"",
		"The directory that contains the graphql schema files for builtin types and directives"+
			"each file with a .gql extension will be loaded from this directory; if not specified, "+
			"then no builtins will be loaded and schema validation will likely fail with an error "+
			"about undefined types (like String) or undefined directives (like @table)")

	err := flagSet.Parse(os.Args[1:])
	if err != nil {
		return parsedCommandLineArguments, err
	}

	if flagSet.NArg() == 0 {
		return parsedCommandLineArguments, errors.New("no graphql schema file specified")
	} else if flagSet.NArg() == 1 {
		return parsedCommandLineArguments, errors.New("no graphql operations file specified")
	} else if flagSet.NArg() > 2 {
		return parsedCommandLineArguments, errors.New("unexpected argument: " + flagSet.Args()[2])
	}

	parsedCommandLineArguments.DestDir = *destDir
	parsedCommandLineArguments.PreludeDir = *preludeDir
	parsedCommandLineArguments.SchemaFile = flagSet.Args()[0]
	parsedCommandLineArguments.OperationsFile = flagSet.Args()[1]

	return parsedCommandLineArguments, nil
}
