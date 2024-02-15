package main

import (
	"fmt"
	"log"
	"os"
)

func main() {
	log.SetPrefix("codegen: ")
	log.SetFlags(0)

	parsedCommandLineArguments, err := ParseCommandLineArguments()
	if err != nil {
		fmt.Println("ERROR: invalid command-line arguments:", err)
		os.Exit(2)
	}

	fmt.Println("DestDir", parsedCommandLineArguments.DestDir)
	fmt.Println("SchemaFile", parsedCommandLineArguments.SchemaFile)
	fmt.Println("OperationsFile", parsedCommandLineArguments.OperationsFile)
}
