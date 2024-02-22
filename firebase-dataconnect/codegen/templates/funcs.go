package templates

import (
	"github.com/vektah/gqlparser/v2/ast"
	"text/template"
)

func makeOperationFuncMap() template.FuncMap {
	return template.FuncMap{
		"fail": fail,
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
