package templates

import (
	_ "embed"
	"log"
	"text/template"
)

//go:embed operation.gotmpl
var operationTemplate string

func LoadOperationTemplate() (*template.Template, error) {
	templateName := "operation.gotmpl"
	log.Println("Loading Go template:", templateName)
	return template.New(templateName).Parse(operationTemplate)
}
