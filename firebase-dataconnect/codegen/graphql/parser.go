package graphql

import (
	"github.com/vektah/gqlparser/v2"
	"github.com/vektah/gqlparser/v2/ast"
	"github.com/vektah/gqlparser/v2/gqlerror"
	"github.com/vektah/gqlparser/v2/parser"
	"log"
	"os"
	"strings"
)

func LoadSchemaFile(file string, preludeDir string) (*ast.Schema, error) {
	sources := make([]*ast.Source, 0, 1)

	if len(preludeDir) > 0 {
		log.Println("Loading GraphQL schema prelude files from directory:", preludeDir)
		preludeFileNames, err := getFileNamesOfGqlFilesInDir(preludeDir)
		if err != nil {
			return nil, err
		}
		for _, fileName := range preludeFileNames {
			preludeFile := preludeDir + "/" + fileName
			log.Println("Loading GraphQL schema prelude file:", preludeFile)
			source, err := loadGraphQLSource(preludeFile, true)
			if err != nil {
				return nil, err
			}
			sources = append(sources, source)
		}
	}

	log.Println("Loading GraphQL schema file:", file)
	source, err := loadGraphQLSource(file, false)
	if err != nil {
		return nil, err
	}
	sources = append(sources, source)

	graphqlSchema, err := gqlparser.LoadSchema(sources...)
	if err != nil {
		return nil, err
	}

	return graphqlSchema, nil
}

func LoadOperationsFile(file string) (*ast.QueryDocument, error) {
	log.Println("Loading GraphQL operations file:", file)
	source, err := loadGraphQLSource(file, false)
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

func loadGraphQLSource(file string, builtIn bool) (*ast.Source, error) {
	fileBytes, err := os.ReadFile(file)
	if err != nil {
		return nil, err
	}
	return &ast.Source{Name: file, Input: string(fileBytes), BuiltIn: builtIn}, nil
}

func getFileNamesOfGqlFilesInDir(dir string) ([]string, error) {
	dirEntries, err := os.ReadDir(dir)
	if err != nil {
		return nil, err
	}

	gqlFileNames := make([]string, 0, len(dirEntries))
	for _, dirEntry := range dirEntries {
		if !dirEntry.IsDir() && strings.HasSuffix(dirEntry.Name(), ".gql") {
			gqlFileNames = append(gqlFileNames, dirEntry.Name())
		}
	}

	return gqlFileNames, nil
}
