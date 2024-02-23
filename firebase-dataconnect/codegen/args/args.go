package args

import (
	"errors"
	"flag"
	"os"
	"path"
)

type ParsedArguments struct {
	DestDir         string
	SchemaFile      string
	OperationsFiles []string
	ConnectorName   string
}

func Parse() (*ParsedArguments, error) {
	flagSet := flag.NewFlagSet(os.Args[0], flag.ContinueOnError)

	destDir := flagSet.String(
		"dest_dir",
		"",
		"The directory into which to write the output files. "+
			"If not specified or the empty string, then the current directory is used.")

	connectorName := flagSet.String(
		"connector",
		"",
		"The name of the connector to use. If not specified, a default value will be used.")

	err := flagSet.Parse(os.Args[1:])
	if err != nil {
		return nil, err
	}

	if flagSet.NArg() == 0 {
		return nil, errors.New("no graphql schema file specified")
	} else if flagSet.NArg() == 1 {
		return nil, errors.New("no graphql operations files specified")
	}

	schemaFile := flagSet.Args()[0]
	operationsFiles := flagSet.Args()[1:]

	parsedArguments := &ParsedArguments{
		DestDir:         *destDir,
		SchemaFile:      schemaFile,
		OperationsFiles: operationsFiles,
		ConnectorName:   connectorNameFrom(connectorName, operationsFiles[0]),
	}

	return parsedArguments, nil
}

func connectorNameFrom(flagValue *string, operationsFile string) string {
	if flagValue != nil && len(*flagValue) > 0 {
		return *flagValue
	}

	cleanFile := path.Clean(operationsFile)
	fileName := path.Base(cleanFile)
	fileExt := path.Ext(fileName)

	return fileName[0 : len(fileName)-len(fileExt)]
}
