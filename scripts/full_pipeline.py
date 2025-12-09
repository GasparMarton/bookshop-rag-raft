import os
import argparse
import time
import subprocess
import glob
import json
import csv
import sys
from dotenv import load_dotenv

load_dotenv()

def run_command(cmd, shell=False):
    print(f"Running: {cmd}")
    # interactive output
    process = subprocess.Popen(cmd, shell=shell)
    process.wait()
    if process.returncode != 0:
        print(f"Command failed: {cmd}")
        sys.exit(1)

def convert_csv_to_json(csv_path, json_path):
    print(f"Converting CSV {csv_path} to JSON chunks...")
    chunks = []
    with open(csv_path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            # Look for common content columns
            text = row.get('TEXT') or row.get('text')
            if text:
                chunks.append({"TEXT": text})
    
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(chunks, f, indent=2)
    print(f"Saved {len(chunks)} chunks to {json_path}")

def main():
    parser = argparse.ArgumentParser(description="End-to-End Training Data Pipeline")
    parser.add_argument("--input", required=True, help="Input CSV or JSON file containing chunks")
    parser.add_argument("--limit", type=int, help="Limit number of chunks to process")
    parser.add_argument("--offset", type=int, default=0, help="Offset to start processing from")
    parser.add_argument("--chunk-ids-file", help="File containing chunk IDs to verify/retry (JSONL)")
    parser.add_argument("--append", action="store_true", help="Append results to final output")
    args = parser.parse_args()

    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        print("Please set OPENAI_API_KEY")
        return

    # 1. Prepare Content
    chunks_file = "data/MY_BOOKSHOP_BOOKCHUNKS_pipeline.json"
    if args.input.endswith(".csv"):
        convert_csv_to_json(args.input, chunks_file)
    else:
        # Assume JSON
        chunks_file = args.input

    # Ensure scripts/ exists
    if not os.path.exists("scripts/generate_questions.py"):
        print("Error: scripts/ directory not found.")
        return

    print("=== STAGE 1: Generating Questions ===")
    
    # 1. Prepare
    # Pass limit/offset to generate_questions
    cmd = f"python scripts/generate_questions.py --mode prepare --input \"{chunks_file}\" --batch-input data/questions_batch_input.jsonl"
    if args.limit: cmd += f" --limit {args.limit}"
    if args.offset: cmd += f" --offset {args.offset}"
    if args.chunk_ids_file: cmd += f" --filter-ids \"{args.chunk_ids_file}\""
    
    run_command(cmd)

    # 2. Submit & Monitor Batch (Questions)
    run_command(f"python scripts/openai_batch.py --file data/questions_batch_input.jsonl --output data/questions_batch_output.jsonl")

    # 3. Process (Questions)
    run_command(f"python scripts/generate_questions.py --mode process --batch-output data/questions_batch_output.jsonl --final-output data/generated_questions.jsonl")

    print("=== STAGE 2: Generating Answers ===")
    
    # 4. Prepare (Answers)
    run_command(f"python scripts/generate_answers.py --mode prepare --input data/generated_questions.jsonl --batch-input data/answers_batch_input.jsonl")

    # 5. Submit & Monitor Batch (Answers)
    run_command(f"python scripts/openai_batch.py --file data/answers_batch_input.jsonl --output data/answers_batch_output.jsonl")

    # 6. Process (Answers)
    cmd = f"python scripts/generate_answers.py --mode process --batch-output data/answers_batch_output.jsonl --final-output data/training_dataset.jsonl"
    if args.offset and args.offset > 0:
        cmd += " --append"
    if args.append:
        cmd += " --append"
    run_command(cmd)

    print("\n=== Pipeline Complete ===")
    print("Final output: data/training_dataset.jsonl")

if __name__ == "__main__":
    main()
