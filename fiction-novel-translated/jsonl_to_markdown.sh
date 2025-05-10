#!/bin/bash

INPUT_FILE=""
OUTPUT_DIR=""

log() {
  echo "[INFO] $1"
}

while [[ $# -gt 0 ]]; do
  case $1 in
    --input)
      INPUT_FILE="$2"
      shift 2
      ;;
    --output)
      OUTPUT_DIR="$2"
      shift 2
      ;;
    *)
      echo "[ERROR] Unknown argument: $1"
      exit 1
      ;;
  esac
done

if [[ -z "$INPUT_FILE" || -z "$OUTPUT_DIR" ]]; then
  echo "[ERROR] Usage: --input <file.jsonl|file.zip> --output <directory>"
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

TEMP_JSONL=""

if [[ "$INPUT_FILE" == *.zip ]]; then
  TEMP_JSONL=$(mktemp)
  log "Extracting JSONL from zip: $INPUT_FILE"
  unzip -p "$INPUT_FILE" > "$TEMP_JSONL"
else
  TEMP_JSONL="$INPUT_FILE"
fi

log "Processing entries from JSONL"

jq -c '.' "$TEMP_JSONL" | while read -r line; do
  FILE_NAME=$(echo "$line" | jq -r '.sourceKey')
  TITLE=$(echo "$line" | jq -r '.translatedTitle')
  TEXT=$(echo "$line" | jq -r '.translatedText')

  echo -e "$TITLE\n$TEXT" > "$OUTPUT_DIR/$FILE_NAME"
  log "Created $FILE_NAME"
done

if [[ "$INPUT_FILE" == *.zip ]]; then
  rm "$TEMP_JSONL"
fi

log "All markdown files created in $OUTPUT_DIR"
