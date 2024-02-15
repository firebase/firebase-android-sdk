package main

import (
	"bytes"
	"github.com/vektah/gqlparser/v2/ast"
	"log"
	"os"
	"path"
	"text/template"
)

type RenderOperationTemplateConfig struct {
	OperationName string
	KotlinPackage string
	Operation     *ast.OperationDefinition
}

func RenderOperationTemplate(tmpl *template.Template, outputFile string, config RenderOperationTemplateConfig) error {
	log.Println("Generating:", outputFile)

	templateRenderData := renderOperationTemplateData{
		OperationName: config.OperationName,
		KotlinPackage: config.KotlinPackage,
		Variables:     make([]renderVariableDefinition, 0),
	}
	templateRenderData.updateVariables(config.Operation)

	var outputBuffer bytes.Buffer
	err := tmpl.Execute(&outputBuffer, templateRenderData)
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

type renderOperationTemplateData struct {
	OperationName string
	KotlinPackage string
	Variables     []renderVariableDefinition
}

func (data *renderOperationTemplateData) updateVariables(operation *ast.OperationDefinition) {
	for _, variableDefinition := range operation.VariableDefinitions {
		data.Variables = append(data.Variables, renderVariableDefinition{
			Name: variableDefinition.Variable,
			Type: renderVariableTypeFrom(variableDefinition.Type),
		})
	}
}

type renderVariableDefinition struct {
	Name string
	Type renderVariableType
}

type renderVariableType struct {
	Name       string
	IsScalar   bool
	IsNullable bool
}

func renderVariableTypeFrom(node *ast.Type) renderVariableType {
	return renderVariableType{
		Name:       node.NamedType,
		IsScalar:   isScalarTypeName(node.NamedType),
		IsNullable: !node.NonNull,
	}
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
