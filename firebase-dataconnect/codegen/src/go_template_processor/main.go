package main

import "bytes"
import "encoding/json"
import "flag"
import "log"
import "os"
import "text/template"

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

	log.Printf("loading JSON config file: %s", configFile)
	configFileBytes, err := os.ReadFile(configFile)
	if err != nil {
		log.Fatal("reading JSON config file failed: ", err)
	}

	log.Printf("loading Go template file: %s", templateFile)
	templateFileBytes, err := os.ReadFile(templateFile)
	if err != nil {
		log.Fatal("reading Go template file failed: ", err)
	}
	templateFileText := string(templateFileBytes)

	var config map[string]interface{}
	err = json.Unmarshal(configFileBytes, &config)
	if err != nil {
		log.Fatal("decoding JSON config file failed: ", configFile, " (", err, ")")
	}

	funcs := template.FuncMap{"fail": templateFail1, "fail2": templateFail2, "fail3": templateFail3}

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

func templateFail1(msg string) error {
	panic(msg)
}

func templateFail2(msg1 string, msg2 string) error {
	panic(msg1 + ": " + msg2)
}

func templateFail3(msg1 string, msg2 string, msg3 string) error {
	panic(msg1 + ": " + msg2 + ": " + msg3)
}
