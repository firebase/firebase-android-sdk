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

//go:embed operation2.gotmpl
var operationTemplate string

func LoadOperationTemplate() (*template.Template, error) {
	templateName := "operation2.gotmpl"
	log.Println("Loading Go template:", templateName)

	return template.New(templateName).Funcs(makeOperationFuncMap()).Parse(operationTemplate)
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

	templateData := operationTemplateDataFromRenderOperationTemplateConfig(config)

	var outputBuffer bytes.Buffer
	err := tmpl.Execute(&outputBuffer, templateData)
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

type operationTemplateData struct {
	KotlinPackage string
	OperationName string
	Variables     *kotlinClass
}

type kotlinClass struct {
	Name                  string
	ConstructorParameters []kotlinFunctionParameter
	HasBody               bool
	NestedClasses         []kotlinClass
}

type kotlinFunctionParameter struct {
	Name       string
	KotlinType string
	IsLast     bool
}

func operationTemplateDataFromRenderOperationTemplateConfig(config RenderOperationTemplateConfig) operationTemplateData {
	return operationTemplateData{
		KotlinPackage: config.KotlinPackage,
		OperationName: config.Operation.Name,
		Variables:     kotlinClassForVariableDefinitions(config.Operation.VariableDefinitions),
	}
}

func kotlinClassForVariableDefinitions(variableDefinitions []*ast.VariableDefinition) *kotlinClass {
	if variableDefinitions == nil || len(variableDefinitions) == 0 {
		return nil
	}

	return &kotlinClass{
		Name:                  "Variables",
		ConstructorParameters: variablesClassConstructorParametersFromVariableDefinitions(variableDefinitions),
		HasBody:               false,
	}
}

func variablesClassConstructorParametersFromVariableDefinitions(variableDefinitions []*ast.VariableDefinition) []kotlinFunctionParameter {
	kotlinFunctionParameters := make([]kotlinFunctionParameter, 0, 0)
	for i, variableDefinition := range variableDefinitions {
		kotlinFunctionParameters = append(kotlinFunctionParameters, kotlinFunctionParameter{
			Name:       variableDefinition.Variable,
			KotlinType: kotlinTypeFromTypeNode(variableDefinition.Type),
			IsLast:     i+1 == len(variableDefinitions),
		})
	}
	return kotlinFunctionParameters
}

func kotlinTypeFromTypeNode(node *ast.Type) string {
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

func fail(a ...any) (any, error) {
	return 42, errors.New(fmt.Sprint(a...))
}
