package templates

import (
	"bytes"
	_ "embed"
	"errors"
	"fmt"
	"github.com/vektah/gqlparser/v2/ast"
	"log"
	"os"
	"path"
	"text/template"
)

//go:embed operation.gotmpl
var operationTemplate string

func LoadOperationTemplate() (*template.Template, error) {
	templateName := "operation.gotmpl"
	log.Println("Loading Go template:", templateName)

	funcMap := template.FuncMap{
		"fail":                      fail,
		"kotlinTypeFromGraphQLType": kotlinTypeFromGraphQLType,
		"isScalarType":              isScalarType,
		"flattenedVariablesFor":     flattenedVariablesFor,
		"createConvenienceFunctionVariablesArgumentsRecursiveArgFromConfig":     createConvenienceFunctionVariablesArgumentsRecursiveArgFromConfig,
		"createConvenienceFunctionVariablesArgumentsRecursiveArgFromArgAndType": createConvenienceFunctionVariablesArgumentsRecursiveArgFromArgAndType,
	}

	return template.New(templateName).Funcs(funcMap).Parse(operationTemplate)
}

type RenderOperationTemplateConfig struct {
	KotlinPackage string
	Operation     *ast.OperationDefinition
	Schema        *ast.Schema
}

func RenderOperationTemplate(
	tmpl *template.Template,
	outputFile string,
	config RenderOperationTemplateConfig) error {

	log.Println("Generating:", outputFile)

	var outputBuffer bytes.Buffer
	err := tmpl.Execute(&outputBuffer, config)
	if err != nil {
		return err
	}

	outputDir := path.Dir(outputFile)
	_, err = os.Stat(outputDir)
	if os.IsNotExist(err) {
		err = os.MkdirAll(outputDir, 0755)
		if err != nil {
			return err
		}
	}

	err = os.WriteFile(outputFile, outputBuffer.Bytes(), 0644)
	if err != nil {
		return err
	}

	return nil
}

func kotlinTypeFromGraphQLType(node *ast.Type) string {
	var suffix string
	if node.NonNull {
		suffix = ""
	} else {
		suffix = "?"
	}

	return kotlinTypeNameFromGraphQLTypeName(node.NamedType) + suffix
}

func kotlinTypeNameFromGraphQLTypeName(graphQLTypeName string) string {
	if graphQLTypeName == "Int" {
		return "Int"
	} else if graphQLTypeName == "Float" {
		return "Float"
	} else if graphQLTypeName == "String" {
		return "String"
	} else if graphQLTypeName == "Boolean" {
		return "Boolean"
	} else if graphQLTypeName == "ID" {
		return "String"
	} else {
		return graphQLTypeName
	}
}

func isScalarType(node *ast.Type) bool {
	return isScalarTypeName(node.NamedType)
}

func isScalarTypeName(typeName string) bool {
	if typeName == "Int" {
		return true
	} else if typeName == "Float" {
		return true
	} else if typeName == "String" {
		return true
	} else if typeName == "Boolean" {
		return true
	} else if typeName == "ID" {
		return true
	} else {
		return false
	}
}

func flattenedVariablesFor(operation *ast.OperationDefinition, schema *ast.Schema) []*ast.VariableDefinition {
	flattenedVariables := make([]*ast.VariableDefinition, 0, 0)

	for _, variableDefinition := range operation.VariableDefinitions {
		if isScalarType(variableDefinition.Type) {
			flattenedVariables = append(flattenedVariables, variableDefinition)
		} else {
			flattenedVariables = append(flattenedVariables, flattenedVariablesForType(variableDefinition.Type, schema)...)
		}
	}

	return flattenedVariables
}

func flattenedVariablesForType(typeNode *ast.Type, schema *ast.Schema) []*ast.VariableDefinition {
	flattenedVariables := make([]*ast.VariableDefinition, 0, 0)

	typeInfo := schema.Types[typeNode.NamedType]
	for _, field := range typeInfo.Fields {
		if isScalarType(field.Type) {
			flattenedVariables = append(flattenedVariables, &ast.VariableDefinition{
				Variable: field.Name,
				Type:     field.Type,
			})
		} else {
			flattenedVariables = append(flattenedVariables, flattenedVariablesForType(field.Type, schema)...)
		}
	}

	return flattenedVariables
}

type convenienceFunctionVariablesArgumentsRecursiveArg struct {
	OperationName string
	Schema        *ast.Schema
	Fields        []*ast.FieldDefinition
}

func createConvenienceFunctionVariablesArgumentsRecursiveArgFromConfig(config RenderOperationTemplateConfig) convenienceFunctionVariablesArgumentsRecursiveArg {
	return convenienceFunctionVariablesArgumentsRecursiveArg{
		OperationName: config.Operation.Name,
		Schema:        config.Schema,
		Fields:        fieldDefinitionsFromVariableDefinitions(config.Operation.VariableDefinitions),
	}
}

func createConvenienceFunctionVariablesArgumentsRecursiveArgFromArgAndType(arg convenienceFunctionVariablesArgumentsRecursiveArg, typeNode *ast.Type) convenienceFunctionVariablesArgumentsRecursiveArg {
	typeInfo := arg.Schema.Types[typeNode.NamedType]
	arg.Fields = typeInfo.Fields
	return arg
}

func fieldDefinitionsFromVariableDefinitions(variableDefinitions []*ast.VariableDefinition) []*ast.FieldDefinition {
	fieldDefinitions := make([]*ast.FieldDefinition, 0, len(variableDefinitions))
	for _, variableDefinition := range variableDefinitions {
		fieldDefinitions = append(fieldDefinitions, &ast.FieldDefinition{
			Name:       variableDefinition.Variable,
			Type:       variableDefinition.Type,
			Directives: variableDefinition.Directives,
			Position:   variableDefinition.Position,
		})
	}
	return fieldDefinitions
}

func fail(a ...any) (any, error) {
	return 42, errors.New(fmt.Sprint(a...))
}
