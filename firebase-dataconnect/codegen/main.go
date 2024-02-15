package main

import (
	"fmt"
	"log"
	"os"
)

func main() {
	log.SetPrefix("codegen: ")
	log.SetFlags(0)

	args, err := ParseCommandLineArguments()
	if err != nil {
		fmt.Println("ERROR: invalid command-line arguments:", err)
		os.Exit(2)
	}

	graphQLSchema, err := LoadGraphQLSchemaFile(args.SchemaFile, args.PreludeDir)
	if err != nil {
		log.Fatal(err)
	}

	graphQLOperations, err := LoadGraphQLOperationsFile(args.OperationsFile, graphQLSchema)
	if err != nil {
		log.Fatal(err)
	}

	operationTemplate, err := LoadGoTemplateFromFile(args.TemplateFile)
	if err != nil {
		log.Fatal(err)
	}

	for _, operation := range graphQLOperations.Operations {
		operationName := operation.Name
		outputFile := args.DestDir + "/" + args.ConnectorName + "/" + operationName + ".kt"
		renderConfig := RenderOperationTemplateConfig{
			OperationName: operationName,
			KotlinPackage: "com.google.firebase.dataconnect.connectors." + args.ConnectorName,
		}
		err = RenderOperationTemplate(operationTemplate, outputFile, renderConfig)
		if err != nil {
			log.Fatal(err)
		}
	}
}
