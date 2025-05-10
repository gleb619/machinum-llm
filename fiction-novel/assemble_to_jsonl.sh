#!/bin/bash

INPUT_DIR=""
OUTPUT_FILE=""
INCLUDE_FILTER=""
EXCLUDE_FILTER=""

log() {
  echo "[INFO] $1"
}

while [[ $# -gt 0 ]]; do
  case $1 in
    --input)
      INPUT_DIR="$2"
      shift 2
      ;;
    --output)
      OUTPUT_FILE="$2"
      shift 2
      ;;
    --include)
      INCLUDE_FILTER="$2"
      shift 2
      ;;
    --exclude)
      EXCLUDE_FILTER="$2"
      shift 2
      ;;
    *)
      echo "[ERROR] Unknown argument: $1"
      exit 1
      ;;
  esac
done

if [[ -z "$INPUT_DIR" || -z "$OUTPUT_FILE" ]]; then
  echo "[ERROR] Usage: --input <dir> --output <file> [--include <pattern>] [--exclude <pattern>]"
  exit 1
fi

log "Searching files in $INPUT_DIR"
FILES=$(find "$INPUT_DIR" -type f -name "*.md")

if [[ -n "$INCLUDE_FILTER" ]]; then
  log "Applying include filter: $INCLUDE_FILTER"
  FILES=$(echo "$FILES" | grep "$INCLUDE_FILTER")
fi

if [[ -n "$EXCLUDE_FILTER" ]]; then
  log "Applying exclude filter: $EXCLUDE_FILTER"
  FILES=$(echo "$FILES" | grep -v "$EXCLUDE_FILTER")
fi

FILES=$(echo "$FILES" | sort)

log "Writing JSONL to $OUTPUT_FILE"
> "$OUTPUT_FILE"

COUNT=0
while IFS= read -r file; do
  FILE_NAME=$(basename "$file")
  FILE_CONTENT=$(jq -Rs '.' < "$file")
  echo "{\"key\": \"$FILE_NAME\", \"value\": $FILE_CONTENT}" >> "$OUTPUT_FILE"
  COUNT=$((COUNT + 1))
done <<< "$FILES"

log "Processed $COUNT files"

FILE_SIZE=$(stat -c%s "$OUTPUT_FILE")
if [[ "$FILE_SIZE" -gt $((5 * 1024 * 1024)) ]]; then
  ZIP_FILE="${OUTPUT_FILE}.zip"
  log "Output file exceeds 5MB. Compressing to $ZIP_FILE"
  zip -j "$ZIP_FILE" "$OUTPUT_FILE" >/dev/null
  log "Compression complete"
fi
