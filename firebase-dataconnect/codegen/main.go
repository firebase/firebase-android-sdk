package main

import (
	"firebase-dataconnect/codegen/args"
	"firebase-dataconnect/codegen/graphql"
	"firebase-dataconnect/codegen/templates"
	"fmt"
	"github.com/vektah/gqlparser/v2/ast"
	"log"
	"os"
	"text/template"
)

func main() {
	log.SetPrefix("codegen: ")
	log.SetFlags(0)

	parsedArgs, err := args.Parse()
	if err != nil {
		fmt.Println("ERROR: invalid command-line arguments:", err)
		os.Exit(2)
	}

	graphQLSchema, err := graphql.LoadSchemaFile(parsedArgs.SchemaFile, parsedArgs.PreludeDir)
	if err != nil {
		log.Fatal(err)
	}

	graphQLOperations, err := graphql.LoadOperationsFile(parsedArgs.OperationsFile)
	if err != nil {
		log.Fatal(err)
	}

	operationTemplate, err := templates.LoadGoTemplateFromFile(parsedArgs.TemplateFile)
	if err != nil {
		log.Fatal(err)
	}

	err = generateOperationKotlinFiles(graphQLOperations.Operations, graphQLSchema, operationTemplate, parsedArgs)
	if err != nil {
		log.Fatal(err)
	}
}

func generateOperationKotlinFiles(
	operations ast.OperationList,
	schema *ast.Schema,
	operationTemplate *template.Template,
	parsedArgs *args.ParsedArguments) error {
	for _, operation := range operations {
		err := generateOperationKotlinFile(operation, schema, operationTemplate, parsedArgs)
		if err != nil {
			return err
		}
	}
	return nil
}

func generateOperationKotlinFile(
	operation *ast.OperationDefinition,
	schema *ast.Schema,
	operationTemplate *template.Template,
	parsedArgs *args.ParsedArguments) error {
	operationName := operation.Name
	kotlinPackage := "com.google.firebase.dataconnect.connectors." + parsedArgs.ConnectorName

	outputFile := parsedArgs.DestDir + "/" + parsedArgs.ConnectorName + "/" + operationName + ".kt"

	renderConfig := templates.RenderOperationTemplateConfig{
		OperationName: operationName,
		KotlinPackage: kotlinPackage,
		Operation:     operation,
		Schema:        schema,
	}

	return templates.RenderOperationTemplate(operationTemplate, outputFile, renderConfig)
}
