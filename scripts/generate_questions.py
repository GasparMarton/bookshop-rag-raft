import json
import glob
import os
import argparse
from openai import OpenAI
from dotenv import load_dotenv

load_dotenv()

def find_chunks_file():
    # Check current directory
    files = glob.glob("MY_BOOKSHOP_BOOKCHUNKS_*.json")
    if files: return files[0]
    
    # Check data directory
    files = glob.glob("data/MY_BOOKSHOP_BOOKCHUNKS_*.json")
    if files: return files[0]
    
    return None

SYSTEM_PROMPT = """You are generating training data for a Bookshop AI Assistant.
The assistant has access to text chunks from books and can search the database.

Instructions:
1. Generate 5 diverse questions that a **customer** might ask the assistant, based on the provided text.
2. The questions should generally fall into these categories:
   - **Specific Inquiry**: Asking about specific events, characters, or details in this text (e.g., "What happens when...?", "Who said...?").
   - **Search/Discovery**: Explicitly asking to find or show books related to the themes/content of this text (e.g., "Show me books about...", "Do you have any books with...").
   - **Recommendation**: Asking for recommendations based on the style or content (e.g., "Can you recommend a book that...").
   
3. Output ONLY a raw JSON list of strings. Do not include markdown formatting.
   Example: ["What is this book about?", "Show me books about space adventure?", "Who is the main character?", "Do you have this book in stock?", "Find books similar to this one."]
"""

def get_user_prompt(book_title, text):
    return f"""Giving the following text chunk from the book '{book_title}':

"{text}"
"""

def generate_questions_sync(client, chunk, model="gpt-4o-mini"):
    book = chunk.get('book', {})
    book_title = book.get('title') if isinstance(book, dict) else "Unknown Book"
    text = chunk.get('text') or chunk.get('TEXT', '')
    
    if not text:
        return []


    try:
        completion = client.chat.completions.create(
            model=model,
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": get_user_prompt(book_title, text)}
            ]
        )
        content = completion.choices[0].message.content
        
         # Clean up markdown
        if content.startswith("```json"):
            content = content[7:]
        if content.startswith("```"):
            content = content[3:]
        if content.endswith("```"):
            content = content[:-3]
            
        questions = json.loads(content.strip())
        return questions
    except Exception as e:
        print(f"Error generating questions: {e}")
        return []

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["sync", "prepare", "process"], default="sync", help="Execution mode")
    parser.add_argument("--input", help="Input JSON chunks file")
    parser.add_argument("--limit", type=int, default=None, help="Limit number of chunks to process")
    parser.add_argument("--offset", type=int, default=0, help="Offset to start processing from")
    parser.add_argument("--batch-input", default="data/questions_batch_input.jsonl", help="Output file for batch preparation")
    parser.add_argument("--batch-output", default="data/questions_batch_output.jsonl", help="Input file for batch processing")
    parser.add_argument("--final-output", default="data/generated_questions.jsonl", help="Final results file")
    parser.add_argument("--filter-ids", help="JSONL file containing chunk IDs to verify/retry")
    
    args = parser.parse_args()

    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        print("Please set OPENAI_API_KEY")
        return

    # Load chunks
    chunks = []
    if args.input:
        files = [args.input]
    else:
        files = glob.glob("MY_BOOKSHOP_BOOKCHUNKS_*.json") + glob.glob("data/MY_BOOKSHOP_BOOKCHUNKS_*.json")
        
    for fpath in files:
        with open(fpath, 'r', encoding='utf-8') as f:
            content = json.load(f)
            if isinstance(content, list):
                chunks.extend(content)
            elif isinstance(content, dict) and 'value' in content:
                chunks.extend(content['value'])
            elif isinstance(content, dict):
                 # Try to find a list value
                 for v in content.values():
                     if isinstance(v, list):
                         chunks.extend(v)
                         break
    
    if not chunks:
        print("No chunks found.")
        sys.exit(1)

    # Filter by IDs if provided
    if args.filter_ids:
        print(f"Filtering chunks using IDs from {args.filter_ids}...")
        filter_ids = set()
        if os.path.exists(args.filter_ids):
            with open(args.filter_ids, 'r', encoding='utf-8') as f:
                for line in f:
                    try:
                        item = json.loads(line)
                        if 'id' in item:
                            filter_ids.add(item['id'])
                    except: 
                        pass
        
        print(f"Loaded {len(filter_ids)} IDs to filter.")
        original_count = len(chunks)
        chunks = [c for c in chunks if c.get('ID') in filter_ids]
        print(f"Filtered chunks from {original_count} to {len(chunks)}.")
        
        if not chunks:
            print("No chunks matched the filter IDs.")
            return

    # Apply Offset and Limit
    start = args.offset
    end = start + args.limit if args.limit else len(chunks)
    chunks = chunks[start:end]
    print(f"Processing chunks {start} to {end} (Total: {len(chunks)})...")

    # SYNC MODE
    if args.mode == "sync":
        client = OpenAI(api_key=api_key)
        with open(args.final_output, 'w', encoding='utf-8') as f:
            for i, chunk in enumerate(chunks):
                print(f"Processing chunk {i+1}/{len(chunks)}...")
                questions = generate_questions_sync(client, chunk)
                for q in questions:
                    entry = {
                        "chunk_id": chunk.get('ID'),
                        "context": chunk.get('text') or chunk.get('TEXT', ''),
                        "question": q
                    }
                    f.write(json.dumps(entry) + "\n")

    # PREPARE MODE
    elif args.mode == "prepare":
        print(f"Preparing batch requests for {len(chunks)} chunks...")
        with open(args.batch_input, 'w', encoding='utf-8') as f:
            for i, chunk in enumerate(chunks):
                book = chunk.get('book', {})
                book_title = book.get('title') if isinstance(book, dict) else "Unknown Book"
                text = chunk.get('text') or chunk.get('TEXT', '')
                chunk_id = chunk.get('ID')

                if not text: continue

                request = {
                    "custom_id": f"chunk-{chunk_id}",
                    "method": "POST",
                    "url": "/v1/chat/completions",
                    "body": {
                        "model": "gpt-4o-mini",
                        "messages": [
                            {"role": "system", "content": SYSTEM_PROMPT},
                            {"role": "user", "content": get_user_prompt(book_title, text)}
                        ]
                    }
                }
                f.write(json.dumps(request) + "\n")
        print(f"Batch input saved to {args.batch_input}")

    # PROCESS MODE
    elif args.mode == "process":
        print(f"Processing batch results from {args.batch_output}...")
        
        # Index chunks by ID for context retrieval
        chunks_map = {c.get('ID'): (c.get('text') or c.get('TEXT', '')) for c in chunks}
        
        results = []
        if os.path.exists(args.batch_output):
            with open(args.batch_output, 'r', encoding='utf-8') as f:
                for line in f:
                    try:
                        res = json.loads(line)
                        custom_id = res.get('custom_id')
                        chunk_id = custom_id.replace("chunk-", "")
                        
                        response_body = res.get('response', {}).get('body', {})
                        content = response_body.get('choices', [{}])[0].get('message', {}).get('content', '[]')
                        
                        # Clean markdown
                        if content.startswith("```json"): content = content[7:]
                        if content.startswith("```"): content = content[3:]
                        if content.endswith("```"): content = content[:-3]
                        
                        questions = json.loads(content.strip())
                        context = chunks_map.get(chunk_id, "")
                        
                        for q in questions:
                            results.append({
                                "chunk_id": chunk_id,
                                "context": context,
                                "question": q
                            })
                    except Exception as e:
                        print(f"Error parsing line: {e}")

            with open(args.final_output, 'w', encoding='utf-8') as f:
                for entry in results:
                    f.write(json.dumps(entry) + "\n")
            print(f"Saved {len(results)} generated questions to {args.final_output}")
        else:
            print(f"Batch output file {args.batch_output} not found.")

if __name__ == "__main__":
    main()
