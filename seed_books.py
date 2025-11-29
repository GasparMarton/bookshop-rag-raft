import requests
import sys
import os
import json
import uuid

import argparse

# 1. Configuration
CAP_SERVICE_URL = "http://localhost:8080/api/admin" # Using AdminService
AUTH = ('admin', 'admin') # Basic Auth

def get_or_create_author(name):
    url = f"{CAP_SERVICE_URL}/Authors"
    
    # Check if author exists
    try:
        response = requests.get(url, params={"$filter": f"name eq '{name}'"}, auth=AUTH)
        if response.status_code == 200:
            results = response.json().get('value', [])
            if results:
                print(f"Author '{name}' found.")
                return results[0]['ID']
    except Exception as e:
        print(f"Error checking author: {e}")

    # Create author
    print(f"Creating author '{name}'...")
    payload = {
        "name": name
    }
    response = requests.post(url, json=payload, auth=AUTH)
    if response.status_code == 201:
        return response.json()['ID']
    else:
        raise Exception(f"Failed to create author (Status {response.status_code}): {response.text}")

def create_book(title, author_id, text):
    url = f"{CAP_SERVICE_URL}/Books"
    payload = {
        "title": title,
        "author_ID": author_id,
        "stock": 10,
        "price": 15.00,
        "currency_code": "USD",
        "fullText": text # Upload full text for backend processing
    }
    response = requests.post(url, json=payload, auth=AUTH)
    if response.status_code == 201:
        print(f"Book '{title}' created.")
        return response.json()['ID']
    else:
        raise Exception(f"Failed to create book (Status {response.status_code}): {response.text}")

def fetch_gutenberg_data(gutenberg_id):
    print(f"Fetching metadata for Gutenberg ID {gutenberg_id}...")
    meta_url = f"https://gutendex.com/books/{gutenberg_id}"
    response = requests.get(meta_url)
    if response.status_code != 200:
        raise Exception(f"Failed to fetch metadata: {response.status_code}")
    
    data = response.json()
    title = data.get('title')
    authors = data.get('authors', [])
    author_name = authors[0]['name'] if authors else "Unknown"
    
    # Clean author name (usually "Last, First" -> "First Last")
    if "," in author_name:
        parts = author_name.split(",", 1)
        author_name = f"{parts[1].strip()} {parts[0].strip()}"
    
    formats = data.get('formats', {})
    text_url = None
    for fmt, url in formats.items():
        if fmt.startswith('text/plain'):
            text_url = url
            break
    
    if not text_url:
        raise Exception(f"No plain text format available. Available formats: {list(formats.keys())}")
        
    print(f"Fetching text from {text_url}...")
    text_response = requests.get(text_url)
    text_response.encoding = 'utf-8' # Ensure utf-8
    if text_response.status_code != 200:
        raise Exception(f"Failed to fetch text: {text_response.status_code}")
        
    return title, author_name, text_response.text

def main():
    parser = argparse.ArgumentParser(description="Seed a book into the CAP service.")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--file", help="Path to the text file containing the book content")
    group.add_argument("--gutenberg-id", help="Project Gutenberg ID to fetch")
    
    parser.add_argument("--title", help="Title of the book (overrides Gutenberg data)")
    parser.add_argument("--author", help="Author of the book (overrides Gutenberg data)")
    
    args = parser.parse_args()
    
    title = args.title
    author_name = args.author
    full_text = ""

    if args.gutenberg_id:
        try:
            g_title, g_author, g_text = fetch_gutenberg_data(args.gutenberg_id)
            if not title: title = g_title
            if not author_name: author_name = g_author
            full_text = g_text
        except Exception as e:
            print(f"Error fetching Gutenberg data: {e}")
            return
    elif args.file:
        if not title or not author_name:
            print("Error: --title and --author are required when using --file")
            return
            
        if not os.path.exists(args.file):
            print(f"File {args.file} not found.")
            return

        print(f"Reading {args.file}...")
        with open(args.file, 'r', encoding='utf-8') as f:
            full_text = f.read()

    # 3. Clean Headers (Optional)
    start_marker = "*** START OF THE PROJECT GUTENBERG EBOOK"
    end_marker = "*** END OF THE PROJECT GUTENBERG EBOOK"
    # Simple check, might need regex for more robustness as Gutenberg headers vary
    if start_marker in full_text:
        full_text = full_text.split(start_marker, 1)[1]
    if end_marker in full_text:
        full_text = full_text.split(end_marker, 1)[0]

    # 4. Create Data
    try:
        author_id = get_or_create_author(author_name)
        book_id = create_book(title, author_id, full_text)
        print("Book created successfully. Backend will handle chunking and embedding.")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    main()
