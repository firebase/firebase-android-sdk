package main

import (
	"fmt"
	"github.com/vektah/gqlparser/v2/ast"
	"github.com/vektah/gqlparser/v2/parser"
	"os"
)

func ParseGraphQLFile(file string) error {
	fileBytes, err := os.ReadFile(file)
	if err != nil {
		return err
	}
	fileText := string(fileBytes)

	graphqlDocument, err := parser.ParseQuery(&ast.Source{Name: file, Input: fileText})
	if err != nil {
		return err
	}

	fmt.Println(graphqlDocument)
	return nil
}
