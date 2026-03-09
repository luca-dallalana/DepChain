#!/bin/bash

# Fixed output directory
OUTPUT_DIR="./rsa_keys"

# Function to display usage
usage() {
    echo "Usage: $0 <number_of_servers> <number_of_clients>"
    echo "  number_of_servers: Number of server RSA key pairs to generate"
    echo "  number_of_clients: Number of client RSA key pairs to generate"
    echo "Keys will be stored in: $OUTPUT_DIR"
    exit 1
}

# Check if both arguments are provided
if [ $# -lt 2 ]; then
    usage
fi

# Validate that both arguments are positive integers
if ! [[ "$1" =~ ^[0-9]+$ ]] || [ "$1" -eq 0 ]; then
    echo "Error: Please provide a positive integer for number of servers"
    usage
fi

if ! [[ "$2" =~ ^[0-9]+$ ]] || [ "$2" -eq 0 ]; then
    echo "Error: Please provide a positive integer for number of clients"
    usage
fi

NUM_SERVERS=$1
NUM_CLIENTS=$2
TOTAL_KEYS=$((NUM_SERVERS + NUM_CLIENTS))

# Remove existing output directory if it exists
if [ -d "$OUTPUT_DIR" ]; then
    echo "Removing existing directory: $OUTPUT_DIR"
    rm -rf "$OUTPUT_DIR"
fi

# Create fresh output directory
mkdir -p "$OUTPUT_DIR"

echo "Generating $NUM_SERVERS server key pairs and $NUM_CLIENTS client key pairs"
echo "Keys will be stored in: $OUTPUT_DIR"
echo ""

# Counter for successful key generations
success_count=0

# Generate server keys
echo "=== Generating Server Keys ==="
for ((i=0; i<NUM_SERVERS; i++)); do
    # Create subdirectory for each server key pair
    KEY_DIR="$OUTPUT_DIR/server_$i"
    mkdir -p "$KEY_DIR"
    
    echo "Generating server key pair $i of $NUM_SERVERS..."
    
    # Generate temporary RSA private key
    if ! openssl genrsa -out "$KEY_DIR/temp.key" 2048 2>/dev/null; then
        echo "  Error: Failed to generate private key for server $i"
        continue
    fi
    
    # Convert private key to DER PKCS#8 format (Java compatible)
    if ! openssl pkcs8 -topk8 -nocrypt -in "$KEY_DIR/temp.key" -outform DER -out "$KEY_DIR/server_$i.privatekey" 2>/dev/null; then
        echo "  Error: Failed to convert private key for server $i"
        rm -f "$KEY_DIR/temp.key"
        continue
    fi
    
    # Generate public key in DER X.509 format (Java compatible)
    if ! openssl rsa -in "$KEY_DIR/temp.key" -pubout -outform DER -out "$KEY_DIR/server_$i.pubkey" 2>/dev/null; then
        echo "  Error: Failed to generate public key for server $i"
        rm -f "$KEY_DIR/temp.key"
        continue
    fi
    
    # Remove temporary PEM key
    rm -f "$KEY_DIR/temp.key"

done

echo ""

# Generate client keys
echo "=== Generating Client Keys ==="
for ((i=0; i<NUM_CLIENTS; i++)); do
    # Create subdirectory for each client key pair
    KEY_DIR="$OUTPUT_DIR/client_$i"
    mkdir -p "$KEY_DIR"
    
    echo "Generating client key pair $i of $NUM_CLIENTS..."
    
    # Generate temporary RSA private key
    if ! openssl genrsa -out "$KEY_DIR/temp.key" 2048 2>/dev/null; then
        echo "  Error: Failed to generate private key for client $i"
        continue
    fi
    
    # Convert private key to DER PKCS#8 format (Java compatible)
    if ! openssl pkcs8 -topk8 -nocrypt -in "$KEY_DIR/temp.key" -outform DER -out "$KEY_DIR/client_$i.privatekey" 2>/dev/null; then
        echo "  Error: Failed to convert private key for client $i"
        rm -f "$KEY_DIR/temp.key"
        continue
    fi
    
    # Generate public key in DER X.509 format (Java compatible)
    if ! openssl rsa -in "$KEY_DIR/temp.key" -pubout -outform DER -out "$KEY_DIR/client_$i.pubkey" 2>/dev/null; then
        echo "  Error: Failed to generate public key for client $i"
        rm -f "$KEY_DIR/temp.key"
        continue
    fi
    
    # Remove temporary PEM key
    rm -f "$KEY_DIR/temp.key"
    
done

# Summary
echo ""
echo "=== Generation Complete ==="
echo "Successfully generated: $success_count/$TOTAL_KEYS key pairs"
echo "  Servers: $NUM_SERVERS"
echo "  Clients: $NUM_CLIENTS"
echo "Keys are stored in: $OUTPUT_DIR"

if [ $success_count -eq 0 ]; then
    echo "WARNING: No keys were successfully generated!"
    exit 1
fi

