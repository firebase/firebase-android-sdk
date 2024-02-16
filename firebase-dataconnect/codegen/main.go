package main

import (
	"firebase-dataconnect/codegen/args"
	"firebase-dataconnect/codegen/graphql"
	"firebase-dataconnect/codegen/templates"
	"fmt"
	"log"
	"os"
)

func main() {
	log.SetPrefix("codegen: ")
	log.SetFlags(0)

	parsedArgs, err := args.ParseCommandLineArguments()
	if err != nil {
		fmt.Println("ERROR: invalid command-line arguments:", err)
		os.Exit(2)
	}

	graphQLSchema, err := graphql.LoadGraphQLSchemaFile(parsedArgs.SchemaFile, parsedArgs.PreludeDir)
	if err != nil {
		log.Fatal(err)
	}

	graphQLOperations, err := graphql.LoadGraphQLOperationsFile(parsedArgs.OperationsFile, graphQLSchema)
	if err != nil {
		log.Fatal(err)
	}

	operationTemplate, err := templates.LoadGoTemplateFromFile(parsedArgs.TemplateFile)
	if err != nil {
		log.Fatal(err)
	}

	for _, operation := range graphQLOperations.Operations {
		operationName := operation.Name
		outputFile := parsedArgs.DestDir + "/" + parsedArgs.ConnectorName + "/" + operationName + ".kt"
		renderConfig := templates.RenderOperationTemplateConfig{
			OperationName: operationName,
			KotlinPackage: "com.google.firebase.dataconnect.connectors." + parsedArgs.ConnectorName,
			Operation:     operation,
		}
		err = templates.RenderOperationTemplate(operationTemplate, outputFile, renderConfig)
		if err != nil {
			log.Fatal(err)
		}
	}
}
