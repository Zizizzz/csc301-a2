#!/bin/bash

# Set the base directory
BASE_DIR=$(pwd)

# Function to compile a service
compile_service() {
    SERVICE_NAME=$1
    SRC_DIR="src/${SERVICE_NAME}"
    COMPILED_DIR="compiled/${SERVICE_NAME}"

    # Create the compiled directory if it doesn't exist
    mkdir -p "${COMPILED_DIR}"

    # Compile the service
    javac -d "${COMPILED_DIR}" -cp "src/*:src" "${SRC_DIR}/${SERVICE_NAME}.java"

    echo "Compilation completed for ${SERVICE_NAME} service."
}

# Function to start a service
start_service() {
    SERVICE_NAME=$1
    CONFIG_FILE=$2
    COMPILED_DIR="compiled/${SERVICE_NAME}"

    # Change to the compiled directory
    cd "${COMPILED_DIR}" || exit

    # Run the service

    java -cp ".:../../src/*" "${SERVICE_NAME}" "${CONFIG_FILE}"

    # Return to the base directory
    cd "${BASE_DIR}" || exit
}
compile_parser() {
    javac -d "./compiled" -cp "src/*:src" "src/WorkloadParser.java"

    echo "Compilation completed for parser."
}
# Function to start the workload parser
start_workload_parser() {
    WORKLOAD_FILE="../$1"

    cd "compiled" || exit

    java -cp ".:../src/*" "WorkloadParser" "../config.json" "${WORKLOAD_FILE}"

    # Replace this line with the command to start the workload parser
    echo "Starting workload parser with file: ${WORKLOAD_FILE}"
    cd "${BASE_DIR}" || exit
}

# Main script logic
case "$1" in
    -c)
        # Compile all services
        compile_service "UserService"
        compile_service "ProductService"
        compile_service "OrderService"
        compile_parser
        ;;

    -u)
        # Start User service
        start_service "UserService" "../../config.json"
        ;;

    -p)
        # Start Product service
        start_service "ProductService" "../../config.json"
        ;;

    -i)
        # Start ISCS
        ;;

    -o)
        # Start Order service
        start_service "OrderService" "../../config.json"
        ;;

    -w)
        # Start workload parser
        start_workload_parser $2
        ;;

    *)
        echo "Usage: $0 {-c|-u|-p|-i|-o|-w workloadfile}"
        exit 1
        ;;
esac