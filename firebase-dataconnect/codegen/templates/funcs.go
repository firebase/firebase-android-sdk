package templates

import (
	"github.com/vektah/gqlparser/v2/ast"
	"text/template"
)

func makeOperationFuncMap() template.FuncMap {
	return template.FuncMap{
		"createConvenienceFunctionVariablesArgumentsRecursiveArgFromArgAndField": createConvenienceFunctionVariablesArgumentsRecursiveArgFromArgAndField,
		"createConvenienceFunctionVariablesArgumentsRecursiveArgFromConfig":      createConvenienceFunctionVariablesArgumentsRecursiveArgFromConfig,
		"fail":                              fail,
		"flattenedVariablesFor":             flattenedVariablesFor,
		"isScalarType":                      isScalarType,
		"kotlinTypeFromGraphQLType":         kotlinTypeFromTypeNode,
		"pickedFieldsForVariableDefinition": pickedFieldsForVariableDefinition,
	}
}

type fieldWithPickedSubFields struct {
	Field           *ast.FieldDefinition
	PickedSubFields map[string]*ast.FieldDefinition
}

type convenienceFunctionVariablesArgumentsRecursiveArg struct {
	OperationName string
	Schema        *ast.Schema
	Fields        []fieldWithPickedSubFields
}

func createConvenienceFunctionVariablesArgumentsRecursiveArgFromArgAndField(arg convenienceFunctionVariablesArgumentsRecursiveArg, field fieldWithPickedSubFields) convenienceFunctionVariablesArgumentsRecursiveArg {
	typeInfo := arg.Schema.Types[field.Field.Type.NamedType]

	newFields := make([]fieldWithPickedSubFields, 0, 0)
	for _, subField := range typeInfo.Fields {
		_, isSubFieldPicked := field.PickedSubFields[subField.Name]
		if isSubFieldPicked {
			newFields = append(newFields, fieldWithPickedSubFields{Field: subField})
		}
	}

	arg.Fields = newFields
	return arg
}

func createConvenienceFunctionVariablesArgumentsRecursiveArgFromConfig(config RenderOperationTemplateConfig) convenienceFunctionVariablesArgumentsRecursiveArg {
	fields := make([]fieldWithPickedSubFields, 0, 0)

	for _, variableDefinition := range config.Operation.VariableDefinitions {
		fieldDefinition := fieldDefinitionFromVariableDefinition(variableDefinition)
		pickedSubFields := fieldDefinitionByFieldNameMapFromFieldDefinitions(pickedFieldsForVariableDefinition(variableDefinition))
		fields = append(fields, fieldWithPickedSubFields{
			Field:           fieldDefinition,
			PickedSubFields: pickedSubFields,
		})
	}

	return convenienceFunctionVariablesArgumentsRecursiveArg{
		OperationName: config.Operation.Name,
		Schema:        config.Schema,
		Fields:        fields,
	}
}

func fieldDefinitionFromVariableDefinition(variableDefinition *ast.VariableDefinition) *ast.FieldDefinition {
	return &ast.FieldDefinition{
		Name:       variableDefinition.Variable,
		Type:       variableDefinition.Type,
		Directives: variableDefinition.Directives,
		Position:   variableDefinition.Position,
	}
}

func flattenedVariablesFor(operation *ast.OperationDefinition, schema *ast.Schema) []*ast.VariableDefinition {
	flattenedVariables := make([]*ast.VariableDefinition, 0, 0)

	for _, variableDefinition := range operation.VariableDefinitions {
		if isScalarType(variableDefinition.Type) {
			flattenedVariables = append(flattenedVariables, variableDefinition)
			continue
		}

		childFlattenedVariables := flattenedVariablesForType(variableDefinition.Type, schema)
		pickedFieldDefinitions := pickedFieldsForVariableDefinition(variableDefinition)
		pickedFieldNames := fieldDefinitionByFieldNameMapFromFieldDefinitions(pickedFieldDefinitions)
		for _, childFlattenedVariable := range childFlattenedVariables {
			_, isChildFlattenedVariablePicked := pickedFieldNames[childFlattenedVariable.Variable]
			if isChildFlattenedVariablePicked {
				flattenedVariables = append(flattenedVariables, childFlattenedVariable)
			}
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
