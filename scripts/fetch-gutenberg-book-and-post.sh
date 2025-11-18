#!/usr/bin/env bash
# Clean script: Fetch Gutenberg book and ensure author & book exist (create if missing)
set -euo pipefail

usage() { echo "Usage: $0 <gutenberg_id> [--admin-base <url>] [--token <token>|--user <user>] [--debug]" >&2; }

GUTENBERG_ID=""
ADMIN_BASE="http://localhost:8080/api/admin"
AUTH_USER=""
AUTH_TOKEN=""
DEBUG=0
declare -a CURL_AUTH_ARGS=()

while [ $# -gt 0 ]; do
  case "$1" in
    --admin-base) ADMIN_BASE="$2"; shift 2;;
    --user) AUTH_USER="$2"; shift 2;;
    --token) AUTH_TOKEN="$2"; shift 2;;
    --debug|-v) DEBUG=1; shift;;
    -h|--help) usage; exit 0;;
    *) if [ -z "$GUTENBERG_ID" ]; then GUTENBERG_ID="$1"; shift; else echo "Unexpected arg: $1" >&2; usage; exit 1; fi;;
  esac
done

[ -z "$GUTENBERG_ID" ] && { echo "Missing Gutenberg ID" >&2; usage; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "Need curl" >&2; exit 1; }
command -v jq   >/dev/null 2>&1 || { echo "Need jq" >&2; exit 1; }

if [ -n "$AUTH_USER" ]; then CURL_AUTH_ARGS=("-u" "$AUTH_USER"); fi
if [ -n "$AUTH_TOKEN" ]; then
  if [[ "$AUTH_TOKEN" == *:* ]] && [ -z "$AUTH_USER" ]; then CURL_AUTH_ARGS=("-u" "$AUTH_TOKEN"); else CURL_AUTH_ARGS=("-H" "Authorization: Bearer $AUTH_TOKEN"); fi
fi

TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

BOOK_URL="https://www.gutenberg.org/cache/epub/${GUTENBERG_ID}/pg${GUTENBERG_ID}.txt"
[ "$DEBUG" -eq 1 ] && echo "[debug] GET $BOOK_URL" >&2
curl -fsSL "$BOOK_URL" -o "$TMPDIR/book.txt" || { echo "Failed to fetch Gutenberg book $GUTENBERG_ID" >&2; exit 1; }

HEADER=$(head -n 400 "$TMPDIR/book.txt" || true)
TITLE=$(echo "$HEADER" | grep -m1 '^Title:' | sed 's/^Title:[ ]*//' || true)
AUTHOR_RAW=$(echo "$HEADER" | grep -m1 '^Author:' | sed 's/^Author:[ ]*//' | tr -d '\r' || true)
[ -z "$TITLE" ] && TITLE="Book $GUTENBERG_ID"
[ -z "$AUTHOR_RAW" ] && AUTHOR_RAW="Unknown"
TITLE=$(printf '%s' "$TITLE" | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
AUTHOR_NAME=$(echo "$AUTHOR_RAW" | awk -F",| and " '{print $1}' | sed 's/^\s*//;s/\s*$//' | tr -d '\r')
[ -z "$AUTHOR_NAME" ] && AUTHOR_NAME="Unknown"
urlenc() { printf '%s' "$1" | jq -s -R -r @uri; }
upload_full_text() {
  local entity_segment="$1"
  [ -z "$entity_segment" ] && return
  local upload_url="${ADMIN_BASE}/${entity_segment}/fullText/\$value"
  local code
  code=$(curl -sS -o /dev/null -w '%{http_code}' -X PUT "$upload_url" "${CURL_AUTH_ARGS[@]}" \
    -H 'Content-Type: text/plain; charset=utf-8' --data-binary @"$TMPDIR/book.txt" || echo 0)
  [ "$DEBUG" -eq 1 ] && echo "[debug] PUT $upload_url (fullText) -> HTTP $code" >&2
  if ! [[ "$code" =~ ^2 ]]; then
    echo "Warning: uploading fullText failed for $entity_segment (HTTP $code)" >&2
  fi
}

# 1. Book existence check
ENC_TITLE=$(urlenc "$TITLE")
BOOK_SEARCH_URL="${ADMIN_BASE}/Books?\$filter=title%20eq%20%27${ENC_TITLE}%27&\$top=1&\$format=json"
[ "$DEBUG" -eq 1 ] && echo "[debug] GET $BOOK_SEARCH_URL" >&2
BOOK_SEARCH_CODE=$(curl -sS --get "$BOOK_SEARCH_URL" "${CURL_AUTH_ARGS[@]}" -H 'Accept: application/json' -w '%{http_code}' -o "$TMPDIR/book_search.json" || echo 0)
if [ "$BOOK_SEARCH_CODE" = "200" ]; then
  EXISTING_BOOK_ID=$(jq -r '.value[0].ID // .value[0].id // empty' < "$TMPDIR/book_search.json" || true)
  if [ -n "$EXISTING_BOOK_ID" ]; then echo "Book already exists (ID=$EXISTING_BOOK_ID) — skipping create" >&2; exit 0; fi
fi

# 2. Author existence or create
ENC_AUTHOR=$(urlenc "$AUTHOR_NAME")
AUTHOR_SEARCH_URL="${ADMIN_BASE}/Authors?\$filter=name%20eq%20%27${ENC_AUTHOR}%27&\$top=5&\$format=json"
[ "$DEBUG" -eq 1 ] && echo "[debug] GET $AUTHOR_SEARCH_URL" >&2
AUTHOR_SEARCH_CODE=$(curl -sS --get "$AUTHOR_SEARCH_URL" "${CURL_AUTH_ARGS[@]}" -H 'Accept: application/json' -w '%{http_code}' -o "$TMPDIR/author_search.json" || echo 0)
AUTHOR_ID=""
if [ "$AUTHOR_SEARCH_CODE" = "200" ]; then
  AUTHOR_COUNT=$(jq -r '.value | length' < "$TMPDIR/author_search.json" 2>/dev/null || echo 0)
  [ "$DEBUG" -eq 1 ] && echo "[debug] Author search HTTP $AUTHOR_SEARCH_CODE, count=$AUTHOR_COUNT" >&2
  AUTHOR_ID=$(jq -r '.value[0].ID // .value[0].id // empty' < "$TMPDIR/author_search.json" || true)
  if [ -n "$AUTHOR_ID" ] && [ "$DEBUG" -eq 1 ]; then echo "[debug] Reusing existing author ID=$AUTHOR_ID" >&2; fi
fi
if [ -z "$AUTHOR_ID" ]; then
  [ "$DEBUG" -eq 1 ] && echo "[debug] POST ${ADMIN_BASE}/Authors" >&2
  AUTHOR_PAYLOAD=$(jq -nc --arg name "$AUTHOR_NAME" '{name: $name}')
  AUTHOR_CREATE_CODE=$(curl -sS -X POST "${ADMIN_BASE}/Authors" "${CURL_AUTH_ARGS[@]}" -H 'Content-Type: application/json' -H 'Accept: application/json' -d "$AUTHOR_PAYLOAD" -o "$TMPDIR/author_create.json" -w '%{http_code}' || echo 0)
  [ "$DEBUG" -eq 1 ] && echo "[debug] Author create HTTP $AUTHOR_CREATE_CODE" >&2
  AUTHOR_ID=$(jq -r '.ID // .id // empty' < "$TMPDIR/author_create.json" || true)
  [ -n "$AUTHOR_ID" ] && [ "$DEBUG" -eq 1 ] && echo "[debug] New author created ID=$AUTHOR_ID" >&2
fi
[ -z "$AUTHOR_ID" ] && { echo "Failed to locate or create Author. Response body:" >&2; sed 's/^/[resp] /' "$TMPDIR/author_create.json" >&2; exit 1; }

echo "Author ID: $AUTHOR_ID" >&2

#############################################
# 3. Derive description & attempt Gutenberg genre via RDF
#############################################
# Description: first paragraph (>80 chars) or fallback first long line
DESCR_RAW=""
if command -v awk >/dev/null 2>&1; then
  DESCR_RAW=$(awk 'BEGIN{RS=""} {gsub(/\r/,"",$0); if(length($0)>80){print; exit}}' "$TMPDIR/book.txt" 2>/dev/null || true)
fi
if [ -z "$DESCR_RAW" ]; then
  while IFS= read -r line; do
    case "$line" in
      Title:*|Author:*|Release\ Date:*|Language:*|"" ) continue;;
    esac
    clean=$(printf '%s' "$line" | tr -d '\r')
    if [ ${#clean} -gt 80 ]; then DESCR_RAW="$clean"; break; fi
  done < "$TMPDIR/book.txt"
fi
DESCR=$(printf '%s' "$DESCR_RAW" | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
if [ ${#DESCR} -gt 300 ]; then DESCR="${DESCR:0:300}"; fi
[ -z "$DESCR" ] && DESCR="${TITLE} by ${AUTHOR_NAME}"

# Attempt to fetch RDF metadata for subjects
GENRE_ID=""; GENRE_NAME="";
RDF_URL="https://www.gutenberg.org/cache/epub/${GUTENBERG_ID}/pg${GUTENBERG_ID}.rdf"
if curl -fsSL "$RDF_URL" -o "$TMPDIR/book.rdf"; then
  SUBJECTS=()
  while IFS= read -r line; do
    val=$(echo "$line" | sed -E 's/.*<rdf:value>([^<]+)<\/rdf:value>.*/\1/' | tr -d '\r')
    [ -n "$val" ] && SUBJECTS+=("$val")
  done < <(grep -i '<rdf:value>' "$TMPDIR/book.rdf")
  if [ "$DEBUG" -eq 1 ]; then
    echo "[debug] RDF subjects (${#SUBJECTS[@]}):" >&2
    idx=0; for s in "${SUBJECTS[@]}"; do echo "[debug]   subj: $s" >&2; idx=$((idx+1)); [ $idx -ge 6 ] && break; done
  fi
  GENRES_CSV="db/data/my.bookshop-Genres.csv"
  if [ -f "$GENRES_CSV" ]; then
    LOCAL_GENRE_NAMES=()
    LOCAL_GENRE_IDS=()
    while IFS=';' read -r id name parent sibling; do
      [ "$id" = "ID" ] && continue
      if [ -n "$name" ]; then
        LOCAL_GENRE_IDS+=("$id")
        LOCAL_GENRE_NAMES+=("$name")
      fi
    done < "$GENRES_CSV"
    lower_subjects=$(printf '%s\n' "${SUBJECTS[@]}" | tr '[:upper:]' '[:lower:]')
    for idx in "${!LOCAL_GENRE_NAMES[@]}"; do
      g="${LOCAL_GENRE_NAMES[$idx]}"
      gl=$(echo "$g" | tr '[:upper:]' '[:lower:]')
      if printf '%s\n' "$lower_subjects" | grep -q -E "\b$gl\b"; then GENRE_NAME="$g"; GENRE_ID="${LOCAL_GENRE_IDS[$idx]}"; break; fi
      if printf '%s\n' "$lower_subjects" | grep -q "$gl"; then GENRE_NAME="$g"; GENRE_ID="${LOCAL_GENRE_IDS[$idx]}"; break; fi
    done
    if [ -n "$GENRE_NAME" ]; then
      if [ -n "$GENRE_ID" ]; then
        [ "$DEBUG" -eq 1 ] && echo "[debug] Genre matched locally name='$GENRE_NAME' ID='$GENRE_ID'" >&2
        # Optional remote verification
        if [ "$DEBUG" -eq 1 ]; then
          VERIFY_URL="${ADMIN_BASE}/Genres('${GENRE_ID}')"
          echo "[debug] GET $VERIFY_URL (verify existence)" >&2
          curl -sS --get "$VERIFY_URL" "${CURL_AUTH_ARGS[@]}" -H 'Accept: application/json' -o "$TMPDIR/genre_verify.json" -w '' || true
        fi
      fi
    else
      [ "$DEBUG" -eq 1 ] && echo "[debug] No genre match from RDF subjects" >&2
    fi
  else
    [ "$DEBUG" -eq 1 ] && echo "[debug] Genres CSV not found; skipping genre mapping" >&2
  fi
else
  [ "$DEBUG" -eq 1 ] && echo "[debug] RDF fetch failed; skipping genre mapping" >&2
fi

# 4. Create Book
BIND_PATH="/Authors('$AUTHOR_ID')"
# Include author_ID field explicitly for consumers relying on raw foreign key
# Build payload using files to avoid 'Argument list too long' for large texts
# Stream payload: avoid huge command-line args by constructing JSON manually with jq escaping title only.
ESC_TITLE=$(jq -Rn --arg t "$TITLE" '$t')
ESC_AUTHOR_ID=$(jq -Rn --arg a "$AUTHOR_ID" '$a')
ESC_BIND=$(jq -Rn --arg b "$BIND_PATH" '$b')
ESC_DESCR=$(jq -Rn --arg d "$DESCR" '$d')
BOOK_PAYLOAD_FILE="$TMPDIR/payload.json"
cat > "$BOOK_PAYLOAD_FILE" <<EOF
{
  "title": $ESC_TITLE,
  "descr": $ESC_DESCR,
  "author_ID": $ESC_AUTHOR_ID,
  "author@odata.bind": $ESC_BIND
}
EOF
if [ -n "$GENRE_ID" ]; then
  jq --arg gid "$GENRE_ID" --arg gbind "/Genres('$GENRE_ID')" '. + {genre_ID:$gid, "genre@odata.bind": $gbind}' "$BOOK_PAYLOAD_FILE" > "$BOOK_PAYLOAD_FILE.tmp" && mv "$BOOK_PAYLOAD_FILE.tmp" "$BOOK_PAYLOAD_FILE"
fi
[ "$DEBUG" -eq 1 ] && echo "[debug] POST ${ADMIN_BASE}/Books" >&2
BOOK_CREATE_CODE=$(curl -sS -X POST "${ADMIN_BASE}/Books" "${CURL_AUTH_ARGS[@]}" -H 'Content-Type: application/json' -H 'Accept: application/json' -d @"$BOOK_PAYLOAD_FILE" -o "$TMPDIR/book_create.json" -w '%{http_code}' || echo 0)
[ "$DEBUG" -eq 1 ] && echo "[debug] Book create HTTP $BOOK_CREATE_CODE" >&2
BOOK_ID=$(jq -r '.ID // .id // empty' < "$TMPDIR/book_create.json" || true)
if [ -z "$BOOK_ID" ]; then
  echo "Warning: book created (HTTP $BOOK_CREATE_CODE) but ID not found in response" >&2
  [ "$DEBUG" -eq 1 ] && sed 's/^/[resp] /' "$TMPDIR/book_create.json" >&2
else
  echo "Created Book ID: $BOOK_ID" >&2
  # 4. Activate draft (draftPrepare) if we have a Book ID
  BOOK_DRAFT_SEGMENT="Books(ID=${BOOK_ID},IsActiveEntity=false)"
  upload_full_text "$BOOK_DRAFT_SEGMENT"
  DRAFT_PREPARE_URL="${ADMIN_BASE}/${BOOK_DRAFT_SEGMENT}/AdminService.draftPrepare"
  [ "$DEBUG" -eq 1 ] && echo "[debug] POST $DRAFT_PREPARE_URL" >&2
  DRAFT_CODE=$(curl -sS -X POST "$DRAFT_PREPARE_URL" "${CURL_AUTH_ARGS[@]}" -H 'Accept: application/json' -o "$TMPDIR/book_draft_prepare.json" -w '%{http_code}' || echo 0)
  [ "$DEBUG" -eq 1 ] && echo "[debug] draftPrepare HTTP $DRAFT_CODE" >&2
  # Optionally show errors if not 2xx
  if ! [[ "$DRAFT_CODE" =~ ^2 ]]; then
    echo "Warning: draftPrepare failed (HTTP $DRAFT_CODE)" >&2
    [ -s "$TMPDIR/book_draft_prepare.json" ] && sed 's/^/[resp] /' "$TMPDIR/book_draft_prepare.json" >&2
  fi
  # 5. Activate draft (draftActivate)
  DRAFT_ACTIVATE_URL="${ADMIN_BASE}/${BOOK_DRAFT_SEGMENT}/AdminService.draftActivate"
  [ "$DEBUG" -eq 1 ] && echo "[debug] POST $DRAFT_ACTIVATE_URL" >&2
  ACTIVATE_CODE=$(curl -sS -X POST "$DRAFT_ACTIVATE_URL" "${CURL_AUTH_ARGS[@]}" -H 'Accept: application/json' -o "$TMPDIR/book_draft_activate.json" -w '%{http_code}' || echo 0)
  [ "$DEBUG" -eq 1 ] && echo "[debug] draftActivate HTTP $ACTIVATE_CODE" >&2
  if ! [[ "$ACTIVATE_CODE" =~ ^2 ]]; then
    echo "Warning: draftActivate failed (HTTP $ACTIVATE_CODE)" >&2
    [ -s "$TMPDIR/book_draft_activate.json" ] && sed 's/^/[resp] /' "$TMPDIR/book_draft_activate.json" >&2
  fi
fi

echo "Done." >&2
