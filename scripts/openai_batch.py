import os
import argparse
import time
import sys
from openai import OpenAI
from dotenv import load_dotenv

load_dotenv()

def upload_file(client: OpenAI, file_path: str):
    print(f"Uploading {file_path}...")
    with open(file_path, "rb") as f:
        file_obj = client.files.create(
            file=f,
            purpose="batch"
        )
    print(f"Uploaded file ID: {file_obj.id}")
    return file_obj.id

def create_batch(client: OpenAI, input_file_id: str, endpoint="/v1/chat/completions"):
    print(f"Creating batch for file {input_file_id}...")
    batch_obj = client.batches.create(
        input_file_id=input_file_id,
        endpoint=endpoint,
        completion_window="24h"
    )
    print(f"Batch created. ID: {batch_obj.id}")
    return batch_obj.id

def print_batch_errors(batch):
    print(f"Batch {batch.id} failed/cancelled. Status: {batch.status}")
    if hasattr(batch, 'errors') and batch.errors:
        print("Errors:")
        for err in batch.errors.data:
            print(f" - Code: {err.code}")
            print(f" - Message: {err.message}")
            if err.line: print(f" - Line: {err.line}")
    else:
        print("No error details provided by OpenAI.")

def wait_for_batch(client: OpenAI, batch_id: str):
    print(f"Waiting for batch {batch_id} to complete...")
    while True:
        batch = client.batches.retrieve(batch_id)
        status = batch.status
        counts = f"({batch.request_counts.completed}/{batch.request_counts.total})" if batch.request_counts and batch.request_counts.total else ""
        print(f"Status: {status} {counts}")
        
        if status in ["completed", "failed", "expired", "cancelled"]:
            if status != "completed":
                print_batch_errors(batch)
            return batch
        
        time.sleep(30) # Poll every 30 seconds

def download_file(client: OpenAI, file_id: str, output_path: str):
    print(f"Downloading result file {file_id} to {output_path}...")
    content = client.files.content(file_id).text
    with open(output_path, "w", encoding='utf-8') as f:
        f.write(content)
    print("Download complete.")

def main():
    parser = argparse.ArgumentParser(description="Manage OpenAI Batch API requests")
    parser.add_argument("--file", help="Input JSONL file to upload and process")
    parser.add_argument("--batch-id", help="Existing batch ID to check/download")
    parser.add_argument("--output", help="Output file path for results", default="batch_output.jsonl")
    parser.add_argument("--submit-only", action="store_true", help="Submit batch and exit without waiting")
    parser.add_argument("--batch-jobs", nargs='+', help="List of input:output file pairs for parallel processing")
    args = parser.parse_args()

    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        print("Please set OPENAI_API_KEY")
        return

    client = OpenAI(api_key=api_key)

    # Multi-Batch Handling
    if args.batch_jobs:
        jobs = []
        # 1. Submit all
        print(f"Submitting {len(args.batch_jobs)} batch jobs...")
        for job_str in args.batch_jobs:
            if ":" not in job_str:
                print(f"Invalid format '{job_str}'. Use input.jsonl:output.jsonl")
                continue
            inp, outp = job_str.split(":", 1)
            try:
                file_id = upload_file(client, inp)
                batch_id = create_batch(client, file_id)
                jobs.append({"batch_id": batch_id, "output": outp, "status": "pending"})
            except Exception as e:
                print(f"Failed to submit {inp}: {e}")
        
        if args.submit_only:
             print("All batches submitted. IDs:", [j["batch_id"] for j in jobs])
             return

        # 2. Monitor all
        print("Monitoring all batches...")
        while True:
            all_done = True
            for job in jobs:
                if job["status"] == "pending":
                    all_done = False
                    try:
                        batch = client.batches.retrieve(job["batch_id"])
                        counts = f"({batch.request_counts.completed}/{batch.request_counts.total})" if batch.request_counts and batch.request_counts.total else ""
                        print(f"[{job['batch_id']}] {batch.status} {counts}")
                        
                        if batch.status == "completed":
                            if batch.output_file_id:
                                download_file(client, batch.output_file_id, job["output"])
                            job["status"] = "done"
                        elif batch.status in ["failed", "expired", "cancelled"]:
                             print_batch_errors(batch)
                             job["status"] = "failed"
                    except Exception as e:
                        print(f"Error checking {job['batch_id']}: {e}")
            
            if all_done:
                print("All batch jobs completed.")
                break
            
            time.sleep(30)
        return

    # Single File Handling (Legacy/Simple)
    if args.batch_id:
        batch = wait_for_batch(client, args.batch_id)
        if batch.status == "completed" and batch.output_file_id:
            download_file(client, batch.output_file_id, args.output)
        else:
             print("Batch not completed or no output.")
    elif args.file:
        file_id = upload_file(client, args.file)
        batch_id = create_batch(client, file_id)
        
        if args.submit_only:
            print(f"Batch submitted. ID: {batch_id}")
            print(f"Check status later with: python scripts/openai_batch.py --batch-id {batch_id} --output {args.output}")
            return

        batch = wait_for_batch(client, batch_id)
        if batch.status == "completed" and batch.output_file_id:
            download_file(client, batch.output_file_id, args.output)
        else:
            print("Batch did not complete successfully or has no output file.")
    else:
        parser.print_help()

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nOperation cancelled.")
