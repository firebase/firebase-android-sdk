package main

import "bytes"
import "flag"
import "log"
import "os"
import "text/template"

import "github.com/BurntSushi/toml"

func main() {
	log.SetPrefix("codegen: ")
	log.SetFlags(0)

	flag.Parse()
	args := flag.Args()
	if len(args) != 3 {
		log.Fatal("expected exactly 3 arguments "+"(configFile, templateFile, outputFile)"+", but got ", len(args), ": ", args)
	}

	configFile := args[0]
	templateFile := args[1]
	outputFile := args[2]

	log.Printf("loading TOML config file: %s", configFile)
	configFileBytes, err := os.ReadFile(configFile)
	if err != nil {
		log.Fatal("reading TOML config file failed: ", err)
	}
	configFileText := string(configFileBytes)

	log.Printf("loading Go template file: %s", templateFile)
	templateFileBytes, err := os.ReadFile(templateFile)
	if err != nil {
		log.Fatal("reading Go template file failed: ", err)
	}
	templateFileText := string(templateFileBytes)

	var config map[string]interface{}
	configMetaData, err := toml.Decode(configFileText, &config)
	if err != nil {
		log.Fatal("decoding TOML config file failed: ", configFile, " (", err, ")")
	}

	if !configMetaData.IsDefined("kotlinPackage") {
		log.Fatal("TOML config file must specify KotlinPackage: ", configFile)
	}
	if !configMetaData.IsDefined("operationName") {
		log.Fatal("TOML config file must specify OperationName: ", configFile)
	}

	funcs := template.FuncMap{"fail": templateFail}

	loadedTemplate, err := template.New(templateFile).Funcs(funcs).Parse(templateFileText)
	if err != nil {
		log.Fatal("parsing Go template file failed: ", templateFile, " (", err, ")")
	}

	var outputBuffer bytes.Buffer
	err = loadedTemplate.Execute(&outputBuffer, config)
	if err != nil {
		log.Fatal("executing Go template file failed: ", templateFile, " (", err, ")")
	}

	log.Printf("writing output to file: %s", outputFile)
	err = os.WriteFile(outputFile, outputBuffer.Bytes(), 0644)
	if err != nil {
		log.Fatal("writing output to file ", outputFile, " failed: ", err)
	}
}

func templateFail(msg string) error {
	panic(msg)
}
