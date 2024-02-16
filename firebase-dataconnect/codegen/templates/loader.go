package templates

import (
	"log"
	"os"
	"text/template"
)

func LoadGoTemplateFromFile(file string) (*template.Template, error) {
	log.Println("Loading Go template from file:", file)

	fileBytes, err := os.ReadFile(file)
	if err != nil {
		return nil, err
	}

	parsedTemplate, err := template.New(file).Parse(string(fileBytes))
	if err != nil {
		return nil, err
	}

	return parsedTemplate, nil
}
