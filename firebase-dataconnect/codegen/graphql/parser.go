package graphql

import (
	pluralize "github.com/gertd/go-pluralize"
	"github.com/vektah/gqlparser/v2"
	"github.com/vektah/gqlparser/v2/ast"
	"github.com/vektah/gqlparser/v2/gqlerror"
	"github.com/vektah/gqlparser/v2/parser"
	"log"
	"os"
	"strings"
)

func LoadSchemaFile(file string) (*ast.Schema, error) {
	sources, err := loadPreludeSources()
	if err != nil {
		return nil, err
	}

	log.Println("Loading GraphQL schema file:", file)
	source, err := loadGraphQLSourceFromFile(file)
	if err != nil {
		return nil, err
	}
	sources = append(sources, source)

	graphqlSchema, err := gqlparser.LoadSchema(sources...)
	if err != nil {
		return nil, err
	}

	addSynthesizedInputTypesAndFields(graphqlSchema)

	return graphqlSchema, nil
}

func addSynthesizedInputTypesAndFields(schema *ast.Schema) {
	typesRequiringSynthesizedTypesAndFields := make([]*ast.Definition, 0, 0)
	for _, typeInfo := range schema.Types {
		if !typeInfo.BuiltIn {
			typesRequiringSynthesizedTypesAndFields = append(typesRequiringSynthesizedTypesAndFields, typeInfo)
		}
	}

	synthesizedInputTypes := make([]*ast.Definition, 0, 0)
	for _, typeInfo := range typesRequiringSynthesizedTypesAndFields {
		synthesizedInputType := new(ast.Definition)
		*synthesizedInputType = *typeInfo
		synthesizedInputType.Name = typeInfo.Name + "_Data"
		synthesizedInputType.Kind = ast.InputObject
		synthesizedInputTypes = append(synthesizedInputTypes, synthesizedInputType)

		log.Println("Adding input type to schema:", synthesizedInputType.Name)
		schema.Types[synthesizedInputType.Name] = synthesizedInputType
	}

	for i, typeInfo := range typesRequiringSynthesizedTypesAndFields {
		synthesizedTypeInfo := synthesizedInputTypes[i]

		mutationFields := make([]*ast.FieldDefinition, 0, 0)
		mutationFields = append(mutationFields, createInsertMutationField(typeInfo, synthesizedTypeInfo))
		mutationFields = append(mutationFields, createDeleteMutationField(typeInfo))
		mutationFields = append(mutationFields, createUpdateMutationField(typeInfo, synthesizedTypeInfo))

		for _, mutationField := range mutationFields {
			log.Println("Adding mutation field to schema:", mutationField.Name)
			schema.Mutation.Fields = append(schema.Mutation.Fields, mutationField)
		}
	}

	for _, typeInfo := range typesRequiringSynthesizedTypesAndFields {
		queryFields := make([]*ast.FieldDefinition, 0, 0)
		queryFields = append(queryFields, createSingularQueryField(typeInfo))
		queryFields = append(queryFields, createPluralQueryField(typeInfo))

		for _, queryField := range queryFields {
			log.Println("Adding query field to schema:", queryField.Name)
			schema.Query.Fields = append(schema.Query.Fields, queryField)
		}
	}
}

func createInsertMutationField(definition *ast.Definition, synthesizedDefinition *ast.Definition) *ast.FieldDefinition {
	arguments := []*ast.ArgumentDefinition{
		&ast.ArgumentDefinition{
			Name: "data",
			Type: &ast.Type{
				NamedType: synthesizedDefinition.Name,
				NonNull:   false,
			},
		},
	}

	return &ast.FieldDefinition{
		Name:      strings.ToLower(definition.Name) + "_insert",
		Arguments: arguments,
		Type:      &ast.Type{NamedType: "pseudo_name_should_never_be_seen", NonNull: false},
	}
}

func createDeleteMutationField(definition *ast.Definition) *ast.FieldDefinition {
	arguments := []*ast.ArgumentDefinition{
		&ast.ArgumentDefinition{
			Name: "id",
			Type: &ast.Type{
				NamedType: "String",
				Elem:      nil,
				NonNull:   false,
				Position:  nil,
			},
		},
	}

	return &ast.FieldDefinition{
		Name:      strings.ToLower(definition.Name) + "_delete",
		Arguments: arguments,
		Type:      &ast.Type{NamedType: "pseudo_name_should_never_be_seen", NonNull: false},
	}
}

func createUpdateMutationField(definition *ast.Definition, synthesizedDefinition *ast.Definition) *ast.FieldDefinition {
	arguments := []*ast.ArgumentDefinition{
		&ast.ArgumentDefinition{
			Name: "id",
			Type: &ast.Type{
				NamedType: "String",
				NonNull:   false,
			},
		},
		&ast.ArgumentDefinition{
			Name: "data",
			Type: &ast.Type{
				NamedType: synthesizedDefinition.Name,
				NonNull:   false,
			},
		},
	}

	return &ast.FieldDefinition{
		Name:      strings.ToLower(definition.Name) + "_update",
		Arguments: arguments,
		Type:      &ast.Type{NamedType: "pseudo_name_should_never_be_seen", NonNull: false},
	}
}

func createQueryFieldArguments(definition *ast.Definition) []*ast.ArgumentDefinition {
	arguments := make([]*ast.ArgumentDefinition, 0, 0)
	for _, field := range definition.Fields {
		argumentType := new(ast.Type)
		*argumentType = *field.Type
		argumentType.NonNull = false
		arguments = append(arguments, &ast.ArgumentDefinition{
			Description: field.Description,
			Name:        field.Name,
			Type:        argumentType,
		})
	}
	return arguments
}

func createSingularQueryField(definition *ast.Definition) *ast.FieldDefinition {
	arguments := createQueryFieldArguments(definition)
	return &ast.FieldDefinition{
		Name:      strings.ToLower(definition.Name),
		Arguments: arguments,
		Type:      &ast.Type{NamedType: "pseudo_name_should_never_be_seen"},
	}
}

func createPluralQueryField(definition *ast.Definition) *ast.FieldDefinition {
	arguments := createQueryFieldArguments(definition)

	return &ast.FieldDefinition{
		Name:      pluralize.NewClient().Plural(strings.ToLower(definition.Name)),
		Arguments: arguments,
		Type:      &ast.Type{NamedType: "pseudo_name_should_never_be_seen"},
	}
}

func LoadOperationsFile(file string, schema *ast.Schema) (*ast.QueryDocument, error) {
	log.Println("Loading GraphQL operations file:", file)
	source, err := loadGraphQLSourceFromFile(file)
	if err != nil {
		return nil, err
	}

	query, err := parser.ParseQuery(source)
	if err != nil {
		gqlErr, ok := err.(*gqlerror.Error)
		if ok {
			return nil, gqlerror.List{gqlErr}
		}
		return nil, gqlerror.List{gqlerror.Wrap(err)}
	}

	return query, nil
}

func loadGraphQLSourceFromFile(file string) (*ast.Source, error) {
	fileBytes, err := os.ReadFile(file)
	if err != nil {
		return nil, err
	}
	return &ast.Source{Name: file, Input: string(fileBytes), BuiltIn: false}, nil
}
