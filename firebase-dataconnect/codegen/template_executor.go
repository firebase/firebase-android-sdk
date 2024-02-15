package main

import (
	"bytes"
	"log"
	"os"
	"path"
	"text/template"
)

type RenderOperationTemplateConfig struct {
	OperationName string
	KotlinPackage string
}

func RenderOperationTemplate(tmpl *template.Template, outputFile string, config RenderOperationTemplateConfig) error {
	log.Println("Generating:", outputFile)

	var outputBuffer bytes.Buffer
	err := tmpl.Execute(&outputBuffer, config)
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
