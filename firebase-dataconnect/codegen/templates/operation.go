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
	funcMap := template.FuncMap{"fail": fail}
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

	templateData, err := operationTemplateDataFromRenderOperationTemplateConfig(config)
	if err != nil {
		return err
	}

	var outputBuffer bytes.Buffer
	err = tmpl.Execute(&outputBuffer, templateData)
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
	KotlinPackage                         string
	OperationName                         string
	OperationType                         string
	Variables                             *kotlinClass
	VariablesKotlinType                   string
	Response                              *kotlinClass
	ResponseKotlinType                    string
	ConvenienceFunctionParameters         []kotlinFunctionParameter
	ConvenienceFunctionForwardedArguments []kotlinFunctionArgument
}

type kotlinClass struct {
	Name                  string
	ConstructorParameters []kotlinFunctionParameter
	SecondaryConstructors []kotlinSecondaryConstructor
	NestedClasses         []kotlinClass
}

type kotlinSecondaryConstructor struct {
	Parameters                  []kotlinFunctionParameter
	PrimaryConstructorArguments []kotlinFunctionArgument
}

func (r kotlinClass) HasBody() bool {
	if r.NestedClasses != nil && len(r.NestedClasses) > 0 {
		return true
	}
	if r.SecondaryConstructors != nil && len(r.SecondaryConstructors) > 0 {
		return true
	}
	return false
}

type kotlinFunctionParameter struct {
	Name       string
	KotlinType string
	IsLast     bool
}

type kotlinFunctionArgument struct {
	Name       string
	Expression *kotlinFunctionCall
	IsLast     bool
}

type kotlinFunctionCall struct {
	FunctionName string
	Arguments    []kotlinFunctionArgument
}

func operationTemplateDataFromRenderOperationTemplateConfig(config RenderOperationTemplateConfig) (operationTemplateData, error) {
	operationName := config.Operation.Name

	variables, err := kotlinClassForVariableDefinitions(config.Operation.VariableDefinitions, config.Schema)
	if err != nil {
		return operationTemplateData{}, err
	}

	variablesKotlinType := "Unit"
	if variables != nil {
		variablesKotlinType = operationName + "." + variables.Name
	}

	response, err := kotlinClassForSelectionSet(config.Operation.SelectionSet, config.Schema)
	if err != nil {
		return operationTemplateData{}, err
	}

	responseKotlinType := "Unit"
	if response != nil {
		responseKotlinType = operationName + "." + response.Name
	}

	templateData := operationTemplateData{
		KotlinPackage:       config.KotlinPackage,
		OperationName:       operationName,
		OperationType:       string(config.Operation.Operation),
		Variables:           variables,
		VariablesKotlinType: variablesKotlinType,
		Response:            response,
		ResponseKotlinType:  responseKotlinType,
	}

	if variables != nil {
		convenienceFunctionParameters, err := convenienceFunctionParametersFromVariableDefinitions(config.Operation.VariableDefinitions, config.Schema)
		if err != nil {
			return operationTemplateData{}, err
		}
		templateData.ConvenienceFunctionParameters = convenienceFunctionParameters

		convenienceFunctionForwardedArgumentsFunctionNamePrefix := config.Operation.Name + "." + variables.Name + "."
		convenienceFunctionForwardedArguments, err := convenienceFunctionForwardedArgumentsFromVariableDefinitions(config.Operation.VariableDefinitions, convenienceFunctionForwardedArgumentsFunctionNamePrefix, config.Schema)
		if err != nil {
			return operationTemplateData{}, err
		}
		templateData.ConvenienceFunctionForwardedArguments = convenienceFunctionForwardedArguments
	}

	return templateData, nil
}

func kotlinClassForVariableDefinitions(variableDefinitions []*ast.VariableDefinition, schema *ast.Schema) (*kotlinClass, error) {
	if variableDefinitions == nil || len(variableDefinitions) == 0 {
		return nil, nil
	}

	nestedClasses, err := nestedClassesFromVariableDefinitions(variableDefinitions, schema)
	if err != nil {
		return nil, err
	}

	secondaryConstructors, err := secondaryConstructorsFromVariableDefinitions(variableDefinitions, schema)
	if err != nil {
		return nil, err
	}

	return &kotlinClass{
		Name:                  "Variables",
		ConstructorParameters: variablesClassConstructorParametersFromVariableDefinitions(variableDefinitions),
		NestedClasses:         nestedClasses,
		SecondaryConstructors: secondaryConstructors,
	}, nil
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

func nestedClassesFromVariableDefinitions(variableDefinitions []*ast.VariableDefinition, schema *ast.Schema) ([]kotlinClass, error) {
	nestedTypeNames := make([]string, 0, 0)
	nestedTypeDefinitionByName := make(map[string]*ast.Definition)

	for _, variableDefinition := range variableDefinitions {
		if isScalarType(variableDefinition.Type) {
			continue
		}

		typeName := variableDefinition.Type.NamedType
		nestedTypeNames = append(nestedTypeNames, typeName)

		typeInfo := schema.Types[typeName]
		if typeInfo == nil {
			return nil, errors.New("schema.Types does not include entry for type: " + typeName)
		}

		typeInfoWithUnpickedFieldsDeleted := &ast.Definition{}
		*typeInfoWithUnpickedFieldsDeleted = *typeInfo
		typeInfoWithUnpickedFieldsDeleted.Fields = deleteUnpickedFields(typeInfoWithUnpickedFieldsDeleted.Fields, variableDefinition)

		nestedTypeDefinitionByName[typeName] = typeInfoWithUnpickedFieldsDeleted
	}

	nestedClasses := make([]kotlinClass, 0, 0)

	for len(nestedTypeNames) > 0 {
		typeName := nestedTypeNames[0]
		nestedTypeNames = nestedTypeNames[1:]
		typeDefinition := nestedTypeDefinitionByName[typeName]

		for _, fieldDefinition := range typeDefinition.Fields {
			if isScalarType(fieldDefinition.Type) {
				continue
			}

			fieldTypeName := fieldDefinition.Type.NamedType
			_, nestedTypeDefinitionExists := nestedTypeDefinitionByName[fieldTypeName]
			if nestedTypeDefinitionExists {
				continue
			}

			nestedTypeNames = append(nestedTypeNames, fieldTypeName)

			fieldTypeInfo := schema.Types[fieldTypeName]
			if fieldTypeInfo == nil {
				return nil, errors.New("schema.Types does not include entry for type: " + fieldTypeName)
			}
			nestedTypeDefinitionByName[fieldTypeName] = fieldTypeInfo
		}

		nestedClasses = append(nestedClasses, kotlinClass{
			Name:                  typeName,
			ConstructorParameters: constructorParametersFromFieldDefinitions(typeDefinition.Fields),
		})
	}

	return nestedClasses, nil
}

func secondaryConstructorsFromVariableDefinitions(variableDefinitions []*ast.VariableDefinition, schema *ast.Schema) ([]kotlinSecondaryConstructor, error) {
	nonScalarVariableCount := 0
	for _, variableDefinition := range variableDefinitions {
		if !isScalarType(variableDefinition.Type) {
			nonScalarVariableCount++
		}
	}
	if nonScalarVariableCount == 0 {
		return nil, nil
	}

	parameters, err := convenienceFunctionParametersFromVariableDefinitions(variableDefinitions, schema)
	if err != nil {
		return nil, err
	}

	primaryConstructorArguments, err := convenienceFunctionForwardedArgumentsFromVariableDefinitions(variableDefinitions, "", schema)
	if err != nil {
		return nil, err
	}

	return []kotlinSecondaryConstructor{
		{
			Parameters:                  parameters,
			PrimaryConstructorArguments: primaryConstructorArguments,
		},
	}, nil
}

func constructorParametersFromFieldDefinitions(fieldDefinitions []*ast.FieldDefinition) []kotlinFunctionParameter {
	kotlinFunctionParameters := make([]kotlinFunctionParameter, 0, 0)
	for i, fieldDefinition := range fieldDefinitions {
		kotlinFunctionParameters = append(kotlinFunctionParameters, kotlinFunctionParameter{
			Name:       fieldDefinition.Name,
			KotlinType: kotlinTypeFromTypeNode(fieldDefinition.Type),
			IsLast:     i+1 == len(fieldDefinitions),
		})
	}
	return kotlinFunctionParameters
}

func convenienceFunctionParametersFromVariableDefinitions(variableDefinitions []*ast.VariableDefinition, schema *ast.Schema) ([]kotlinFunctionParameter, error) {
	kotlinFunctionParameters := make([]kotlinFunctionParameter, 0, 0)
	for _, variableDefinition := range variableDefinitions {
		if isScalarType(variableDefinition.Type) {
			kotlinFunctionParameters = append(kotlinFunctionParameters, kotlinFunctionParameter{
				Name:       variableDefinition.Variable,
				KotlinType: kotlinTypeFromTypeNode(variableDefinition.Type),
				IsLast:     false,
			})
		} else {
			variableTypeName := variableDefinition.Type.NamedType
			variableTypeInfo := schema.Types[variableTypeName]
			if variableTypeInfo == nil {
				return nil, errors.New("schema.Types does not include entry for type: " + variableTypeName)
			}

			pickedFields := deleteUnpickedFields(variableTypeInfo.Fields, variableDefinition)

			childFunctionParameters, err := convenienceFunctionParametersFromFieldDefinitions(pickedFields, schema)
			if err != nil {
				return nil, err
			}
			kotlinFunctionParameters = append(kotlinFunctionParameters, childFunctionParameters...)
		}
	}

	for i := range kotlinFunctionParameters {
		kotlinFunctionParameters[i].IsLast = i+1 == len(kotlinFunctionParameters)
	}

	return kotlinFunctionParameters, nil
}

func convenienceFunctionParametersFromFieldDefinitions(fieldDefinitions []*ast.FieldDefinition, schema *ast.Schema) ([]kotlinFunctionParameter, error) {
	kotlinFunctionParameters := make([]kotlinFunctionParameter, 0, 0)
	for _, fieldDefinition := range fieldDefinitions {
		if isScalarType(fieldDefinition.Type) {
			kotlinFunctionParameters = append(kotlinFunctionParameters, kotlinFunctionParameter{
				Name:       fieldDefinition.Name,
				KotlinType: kotlinTypeFromTypeNode(fieldDefinition.Type),
				IsLast:     false,
			})
		} else {
			fieldTypeName := fieldDefinition.Type.NamedType
			fieldTypeInfo := schema.Types[fieldTypeName]
			if fieldTypeInfo == nil {
				return nil, errors.New("schema.Types does not include entry for type: " + fieldTypeName)
			}
			childFunctionParameters, err := convenienceFunctionParametersFromFieldDefinitions(fieldTypeInfo.Fields, schema)
			if err != nil {
				return nil, err
			}
			kotlinFunctionParameters = append(kotlinFunctionParameters, childFunctionParameters...)
		}
	}

	for i := range kotlinFunctionParameters {
		kotlinFunctionParameters[i].IsLast = i+1 == len(kotlinFunctionParameters)
	}

	return kotlinFunctionParameters, nil
}

func convenienceFunctionForwardedArgumentsFromVariableDefinitions(variableDefinitions []*ast.VariableDefinition, functionNamePrefix string, schema *ast.Schema) ([]kotlinFunctionArgument, error) {
	kotlinFunctionArguments := make([]kotlinFunctionArgument, 0, 0)
	for _, variableDefinition := range variableDefinitions {
		if isScalarType(variableDefinition.Type) {
			kotlinFunctionArguments = append(kotlinFunctionArguments, kotlinFunctionArgument{
				Name:   variableDefinition.Variable,
				IsLast: false,
			})
		} else {
			variableTypeName := variableDefinition.Type.NamedType
			variableTypeInfo := schema.Types[variableTypeName]
			if variableTypeInfo == nil {
				return nil, errors.New("schema.Types does not include entry for type: " + variableTypeName)
			}

			pickedFields := deleteUnpickedFields(variableTypeInfo.Fields, variableDefinition)

			childFunctionArguments, err := convenienceFunctionForwardedArgumentsFromFieldDefinitions(pickedFields, functionNamePrefix, schema)
			if err != nil {
				return nil, err
			}

			kotlinFunctionArguments = append(kotlinFunctionArguments, kotlinFunctionArgument{
				Name: variableDefinition.Variable,
				Expression: &kotlinFunctionCall{
					FunctionName: functionNamePrefix + variableTypeInfo.Name,
					Arguments:    childFunctionArguments,
				},
				IsLast: false,
			})
		}
	}

	for i := range kotlinFunctionArguments {
		kotlinFunctionArguments[i].IsLast = i+1 == len(kotlinFunctionArguments)
	}

	return kotlinFunctionArguments, nil
}

func convenienceFunctionForwardedArgumentsFromFieldDefinitions(fieldDefinitions []*ast.FieldDefinition, functionNamePrefix string, schema *ast.Schema) ([]kotlinFunctionArgument, error) {
	kotlinFunctionArguments := make([]kotlinFunctionArgument, 0, 0)
	for _, fieldDefinition := range fieldDefinitions {
		if isScalarType(fieldDefinition.Type) {
			kotlinFunctionArguments = append(kotlinFunctionArguments, kotlinFunctionArgument{
				Name:   fieldDefinition.Name,
				IsLast: false,
			})
		} else {
			fieldTypeName := fieldDefinition.Type.NamedType
			fieldTypeInfo := schema.Types[fieldTypeName]
			if fieldTypeInfo == nil {
				return nil, errors.New("schema.Types does not include entry for type: " + fieldTypeName)
			}
			childFunctionArguments, err := convenienceFunctionForwardedArgumentsFromFieldDefinitions(fieldTypeInfo.Fields, functionNamePrefix, schema)
			if err != nil {
				return nil, err
			}

			kotlinFunctionArguments = append(kotlinFunctionArguments, kotlinFunctionArgument{
				Name: fieldDefinition.Name,
				Expression: &kotlinFunctionCall{
					FunctionName: functionNamePrefix + fieldTypeInfo.Name,
					Arguments:    childFunctionArguments,
				},
				IsLast: false,
			})
		}
	}

	for i := range kotlinFunctionArguments {
		kotlinFunctionArguments[i].IsLast = i+1 == len(kotlinFunctionArguments)
	}

	return kotlinFunctionArguments, nil
}

func deleteUnpickedFields(fieldDefinitions []*ast.FieldDefinition, variableDefinition *ast.VariableDefinition) []*ast.FieldDefinition {
	pickedFieldsList := pickedFieldsForVariableDefinition(variableDefinition)
	pickedFieldNames := fieldNameSetFromFieldDefinitions(pickedFieldsList)

	pickedFields := make([]*ast.FieldDefinition, 0, 0)
	for _, fieldDefinition := range fieldDefinitions {
		_, isFieldPicked := pickedFieldNames[fieldDefinition.Name]
		if isFieldPicked {
			pickedFields = append(pickedFields, fieldDefinition)
		}
	}
	return pickedFields
}

func pickedFieldsForVariableDefinition(variableDefinition *ast.VariableDefinition) []*ast.FieldDefinition {
	pickDirective := pickDirectiveForVariableDefinition(variableDefinition)
	if pickDirective == nil {
		return variableDefinition.Definition.Fields
	}

	pickedFields := make(map[string]*ast.ChildValue)
	for _, pickDirectiveArgument := range pickDirective.Arguments {
		if pickDirectiveArgument.Name == "fields" {
			for _, pickDirectiveArgumentChildValue := range pickDirectiveArgument.Value.Children {
				pickedFields[pickDirectiveArgumentChildValue.Value.Raw] = pickDirectiveArgumentChildValue
			}
		}
	}

	fieldDefinitions := make([]*ast.FieldDefinition, 0, 0)
	for _, field := range variableDefinition.Definition.Fields {
		if _, isFieldPicked := pickedFields[field.Name]; isFieldPicked {
			fieldDefinitions = append(fieldDefinitions, field)
		}
	}

	return fieldDefinitions
}

func pickDirectiveForVariableDefinition(variableDefinition *ast.VariableDefinition) *ast.Directive {
	for _, directive := range variableDefinition.Directives {
		if directive.Name == "pick" {
			return directive
		}
	}
	return nil
}

func fieldNameSetFromFieldDefinitions(fieldDefinitions []*ast.FieldDefinition) map[string]*any {
	fieldNameSet := make(map[string]*any)
	for _, fieldDefinition := range fieldDefinitions {
		fieldNameSet[fieldDefinition.Name] = nil
	}
	return fieldNameSet
}

func kotlinClassForSelectionSet(selectionSet []ast.Selection, schema *ast.Schema) (*kotlinClass, error) {
	fields := make([]*ast.Field, 0, 0)
	for _, selection := range selectionSet {
		fields = append(fields, fieldFromSelection(selection))
	}

	return &kotlinClass{
		Name: "Data",
		ConstructorParameters: []kotlinFunctionParameter{
			{
				Name:       "replaceMeZzyzx",
				KotlinType: "String",
				IsLast:     true,
			},
		},
	}, nil
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

func fieldFromSelection(selection ast.Selection) *ast.Field {
	// only fields right now and not fragments - so can safely cast
	switch field := selection.(type) {
	case *ast.Field:
		return field
	default:
		panic("Unsupported ast.Selection type")
	}
}

func fail(a ...any) (any, error) {
	return 42, errors.New(fmt.Sprint(a...))
}
