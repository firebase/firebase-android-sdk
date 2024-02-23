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

	schema, err := graphql.LoadSchemaFile(parsedArgs.SchemaFile)
	if err != nil {
		log.Fatal(err)
	}

	queryDocuments := make([]*ast.QueryDocument, 0, 0)
	for _, operationsFile := range parsedArgs.OperationsFiles {
		queryDocument, err := graphql.LoadOperationsFile(operationsFile, schema)
		if err != nil {
			log.Fatal(err)
		}
		queryDocuments = append(queryDocuments, queryDocument)
	}

	for _, queryDocument := range queryDocuments {
		err = generateOperationKotlinFiles(queryDocument.Operations, schema, parsedArgs)
		if err != nil {
			log.Fatal(err)
		}
	}
}

func generateOperationKotlinFiles(
	operations ast.OperationList,
	schema *ast.Schema,
	parsedArgs *args.ParsedArguments) error {

	operationTemplate, err := templates.LoadOperationTemplate()
	if err != nil {
		log.Fatal(err)
	}

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
	kotlinPackage := "com.google.firebase.dataconnect.connectors." + parsedArgs.ConnectorName

	outputFile := parsedArgs.DestDir + "/" + parsedArgs.ConnectorName + "/" + operation.Name + ".kt"

	renderConfig := templates.RenderOperationTemplateConfig{
		KotlinPackage: kotlinPackage,
		Operation:     operation,
		Schema:        schema,
	}

	return templates.RenderOperationTemplate(operationTemplate, outputFile, renderConfig)
}
