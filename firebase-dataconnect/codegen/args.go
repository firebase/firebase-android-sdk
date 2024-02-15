package main

import (
	"errors"
	"flag"
	"os"
)

type ParsedCommandLineArguments struct {
	DestDir           string
	GraphQLInputFiles []string
}

func ParseCommandLineArguments() (ParsedCommandLineArguments, error) {
	flagSet := flag.NewFlagSet(os.Args[0], flag.ContinueOnError)

	destDir := flagSet.String(
		"dest_dir",
		"",
		"The directory into which to write the output files. "+
			"If not specified or the empty string, then the current directory is used.")

	err := flagSet.Parse(os.Args[1:])
	if err != nil {
		return ParsedCommandLineArguments{}, err
	}

	if flagSet.NArg() == 0 {
		return ParsedCommandLineArguments{}, errors.New("no input graphql files specified")
	}

	return ParsedCommandLineArguments{*destDir, flagSet.Args()}, nil
}
