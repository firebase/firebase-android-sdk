package main

import (
	"fmt"
	"log"
	"os"
)

func main() {
	log.SetPrefix("codegen: ")
	log.SetFlags(0)

	config, err := ParseCommandLineArguments()
	if err != nil {
		fmt.Println("ERROR: invalid command-line arguments:", err)
		os.Exit(2)
	}

	graphQLSchema, err := LoadGraphQLSchemaFile(config.SchemaFile, config.PreludeDir)
	if err != nil {
		log.Fatal(err)
	}

	graphQLOperations, err := LoadGraphQLOperationsFile(config.OperationsFile, graphQLSchema)
	if err != nil {
		log.Fatal(err)
	}

	for _, operation := range graphQLOperations.Operations {
		fmt.Println("op:", operation.Name)
	}

	for key, value := range graphQLSchema.Types {
		if !value.BuiltIn {
			fmt.Println("type:", key, value.Name)
		}
	}

	fmt.Println(config.ConnectorName)
}
