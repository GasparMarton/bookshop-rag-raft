import json
import os
import argparse

def load_json(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        return json.load(f)

def load_jsonl(filepath):
    data = []
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            data.append(json.loads(line))
    return data

def verify_bookchunks(args):
    bookchunks_path = os.path.join('data', 'MY_BOOKSHOP_BOOKCHUNKS_202512070110.json')
    training_data_path = os.path.join('data', 'training_dataset.jsonl')

    print(f"Loading bookchunks from {bookchunks_path}...")
    bookchunks_data = load_json(bookchunks_path)
    bookchunks = bookchunks_data.get('MY_BOOKSHOP_BOOKCHUNKS', [])
    print(f"Loaded {len(bookchunks)} bookchunks.")

    print(f"Loading training data from {training_data_path}...")
    training_data = load_jsonl(training_data_path)
    print(f"Loaded {len(training_data)} training records.")

    # Create a set of all texts in the training dataset inputs for faster lookup
    # The requirement is: "input": "CONTEXT": "xy" should contain the bookchunk
    # So we'll check if chunk['TEXT'] is in record['input']
    
    # Since checking substring in 15k records for 36k chunks is O(M*N), 
    # it might be slow (15k * 36k ~ 540 million ops).
    # However, Python string search is fast. Let's try the simple approach first.
    # To optimize, we can invert the check if meaningful, but "contains" is directional.
    
    # Wait, the user said: "input": "CONTEXT": "xy" should contain the bookchunk
    # This means record['input'] (which is "CONTEXT: ...") should contain chunk['TEXT'].
    
    missing_chunks = []
    
    # Optimization: Pre-process training inputs to potentially reduce search space or just use brute force with progress bar?
    # Brute force might be acceptable for this size, but let's be slightly smart.
    # Actually, iterate through chunks, and for each chunk, check if it exists in ANY training record.
    # This is indeed O(N*M). 
    
    # 540 million is a bit much for pure python loop without optimization.
    # Let's try to verify. 
    # Maybe we can tokenize or something, but exact string match is requested.
    
    # Let's add a simple progress indicator.
    
    found_count = 0
    total_chunks = len(bookchunks)
    
    # Pre-load all inputs into memory
    all_inputs = [record['input'] for record in training_data]
    
    print("Verifying chunks...")
    for i, chunk in enumerate(bookchunks):
        chunk_text = chunk.get('TEXT', '')
        if not chunk_text:
            continue
            
        found = False
        for inp in all_inputs:
            if chunk_text in inp:
                found = True
                break
        
        if found:
            found_count += 1
        else:
            missing_chunks.append(chunk['ID'])
        
        if (i + 1) % 100 == 0:
            print(f"Processed {i + 1}/{total_chunks} chunks. Found: {found_count}", end='\r')

    print(f"\nVerification complete.")
    print(f"Total chunks: {total_chunks}")
    print(f"Found: {found_count}")
    print(f"Missing: {len(missing_chunks)}")
    
    if args.output and missing_chunks:
        print(f"Saving missing chunk IDs to {args.output}...")
        with open(args.output, 'w', encoding='utf-8') as f:
            for chunk_id in missing_chunks:
                f.write(json.dumps({"id": chunk_id}) + "\n")
        print("Saved.")

    if missing_chunks:
        print("Missing Chunk IDs (first 10):")
        for missed_id in missing_chunks[:10]:
            print(missed_id)
        if len(missing_chunks) > 10:
            print(f"... and {len(missing_chunks) - 10} more.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", help="Output file to save missing chunk IDs (JSONL)")
    args = parser.parse_args()
    verify_bookchunks(args)
