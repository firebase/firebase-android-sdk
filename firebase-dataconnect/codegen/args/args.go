package args

import (
	"errors"
	"flag"
	"os"
	"path"
)

type ParsedArguments struct {
	DestDir        string
	PreludeDir     string
	SchemaFile     string
	OperationsFile string
	ConnectorName  string
}

func Parse() (*ParsedArguments, error) {
	flagSet := flag.NewFlagSet(os.Args[0], flag.ContinueOnError)

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
		return nil, errors.New("no graphql operations file specified")
	} else if flagSet.NArg() > 2 {
		return nil, errors.New("unexpected argument: " + flagSet.Args()[3])
	}

	schemaFile := flagSet.Args()[0]
	operationsFile := flagSet.Args()[1]

	parsedArguments := &ParsedArguments{
		DestDir:        *destDir,
		PreludeDir:     *preludeDir,
		SchemaFile:     schemaFile,
		OperationsFile: operationsFile,
		ConnectorName:  connectorNameFrom(connectorName, operationsFile),
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
