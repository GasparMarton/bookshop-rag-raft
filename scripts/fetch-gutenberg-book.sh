#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <gutenberg-book-id> [output-path]" >&2
  echo "" >&2
  echo "Environment overrides:" >&2
  echo "  GUTENBERG_AUTHOR_ID  Existing author UUID in the catalog (default: Emily Brontë)" >&2
  echo "  GUTENBERG_GENRE_ID   Existing genre UUID in the catalog (default: Fiction root)" >&2
  echo "  GUTENBERG_STOCK      Stock quantity to assign (default: 10)" >&2
  echo "  GUTENBERG_PRICE      Price to assign (default: 14.99)" >&2
  echo "  GUTENBERG_CURRENCY   Currency code (default: USD)" >&2
  exit 1
fi

BOOK_ID="$1"
OUTPUT_PATH="${2:-tmp/gutenberg-book-${BOOK_ID}.csv}"

AUTHOR_ID="${GUTENBERG_AUTHOR_ID:-335c7bcd-b826-4f14-a788-e0bf6738617a}"
GENRE_ID="${GUTENBERG_GENRE_ID:-8bbf14c6-b378-4e35-9b4f-05a9c8878001}"
STOCK="${GUTENBERG_STOCK:-10}"
PRICE="${GUTENBERG_PRICE:-14.99}"
CURRENCY="${GUTENBERG_CURRENCY:-USD}"

TMP_TEXT="$(mktemp)"
trap 'rm -f "$TMP_TEXT"' EXIT

URL="https://www.gutenberg.org/cache/epub/${BOOK_ID}/pg${BOOK_ID}.txt"
echo "Downloading Project Gutenberg book ${BOOK_ID}..." >&2
curl -fsSL "$URL" -o "$TMP_TEXT"

TITLE="$(grep -m1 '^Title:' "$TMP_TEXT" | sed 's/^Title:[[:space:]]*//')"
AUTHOR_NAME="$(grep -m1 '^Author:' "$TMP_TEXT" | sed 's/^Author:[[:space:]]*//')"
if [[ -z "$TITLE" ]]; then
  TITLE="Project Gutenberg Book ${BOOK_ID}"
fi
if [[ -z "$AUTHOR_NAME" ]]; then
  AUTHOR_NAME="Unknown author"
fi

DESCRIPTION="$(awk '
  BEGIN {capture=0}
  /\*\*\* START OF THE PROJECT GUTENBERG/ {capture=1; next}
  /\*\*\* END OF THE PROJECT GUTENBERG/ {capture=0}
  capture {print}
' "$TMP_TEXT")"
BOOK_BODY="$DESCRIPTION"
DESCRIPTION="$(printf '%s\n' "$BOOK_BODY" | head -n 20 | tr '\n' ' ')"
DESCRIPTION="$(echo "${DESCRIPTION:-${TITLE} by ${AUTHOR_NAME}}" | tr -d '\r')"
DESCRIPTION="${DESCRIPTION//;/,}"
DESCRIPTION="$(echo "$DESCRIPTION" | sed 's/[[:space:]]\+/ /g' | sed 's/^ //;s/ $//')"

FULL_TEXT_CONTENT="$BOOK_BODY"
FULL_TEXT_CONTENT="${FULL_TEXT_CONTENT:-${TITLE} by ${AUTHOR_NAME}}"
FULL_TEXT_CONTENT="$(printf '%s' "$FULL_TEXT_CONTENT" | tr -d '\r')"
FULL_TEXT_CONTENT="${FULL_TEXT_CONTENT//;/,}"
FULL_TEXT_CONTENT="${FULL_TEXT_CONTENT//$'\n'/\\n}"
FULL_TEXT_CONTENT="$(echo "$FULL_TEXT_CONTENT" | sed 's/^ //;s/ $//')"

BOOK_UUID="$(uuidgen)"
mkdir -p "$(dirname "$OUTPUT_PATH")"

{
  echo "ID;title;descr;author_ID;stock;price;currency_code;genre_ID;fullText"
  printf '%s;%s;%s;%s;%s;%s;%s;%s;%s\n' \
    "$BOOK_UUID" \
    "$TITLE" \
    "$DESCRIPTION" \
    "$AUTHOR_ID" \
    "$STOCK" \
    "$PRICE" \
    "$CURRENCY" \
    "$GENRE_ID" \
    "$FULL_TEXT_CONTENT"
} > "$OUTPUT_PATH"

echo ""
echo "Saved CSV to $OUTPUT_PATH"
echo " -> Title: $TITLE"
echo " -> Author metadata in file: $AUTHOR_NAME (linked author ID: $AUTHOR_ID)"
echo ""
echo "Upload this file via the Admin Service \"Import Books\" dialog."
