package graphql

import (
	"embed"
	"github.com/vektah/gqlparser/v2/ast"
	"log"
)

//go:embed prelude/*.gql
var preludeFS embed.FS

func loadPreludeSources() ([]*ast.Source, error) {
	sources := make([]*ast.Source, 0, 1)

	log.Println("Loading GraphQL schema prelude files")
	preludeFileNames, err := getPreludeFileNames()
	if err != nil {
		return nil, err
	}
	for _, fileName := range preludeFileNames {
		log.Println("Loading GraphQL schema prelude file:", fileName)
		source, err := loadGraphQLSourceFromPrelude(fileName)
		if err != nil {
			return nil, err
		}
		sources = append(sources, source)
	}

	return sources, nil
}

func loadGraphQLSourceFromPrelude(file string) (*ast.Source, error) {
	fileBytes, err := preludeFS.ReadFile("prelude/" + file)
	if err != nil {
		return nil, err
	}
	return &ast.Source{Name: file, Input: string(fileBytes), BuiltIn: true}, nil
}

func getPreludeFileNames() ([]string, error) {
	dirEntries, err := preludeFS.ReadDir("prelude")
	if err != nil {
		return nil, err
	}

	fileNames := make([]string, 0, len(dirEntries))
	for _, dirEntry := range dirEntries {
		fileNames = append(fileNames, dirEntry.Name())
	}

	return fileNames, nil
}
