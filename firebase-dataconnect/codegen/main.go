package main

import (
	"fmt"
	"log"
	"os"
)

func main() {
	log.SetPrefix("codegen: ")
	log.SetFlags(0)

	parsedCommandLineArguments, err := ParseCommandLineArguments()
	if err != nil {
		fmt.Println("ERROR: invalid command-line arguments:", err)
		os.Exit(2)
	}

	fmt.Println("Loading GraphQL schema file:", parsedCommandLineArguments.SchemaFile)
	graphQLSchema, err := LoadGraphQLSchemaFile(parsedCommandLineArguments.SchemaFile, parsedCommandLineArguments.PreludeDir)
	if err != nil {
		log.Fatal("Loading GraphQL schema file failed: ", parsedCommandLineArguments.SchemaFile, " (", err, ")")
	}

	fmt.Println("DestDir", parsedCommandLineArguments.DestDir)
	fmt.Println("SchemaFile", graphQLSchema)
	fmt.Println("OperationsFile", parsedCommandLineArguments.OperationsFile)
}
