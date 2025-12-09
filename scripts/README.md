# Training Data Generation Scripts

This folder contains scripts to generate RAFT (Retrieval Augmented Fine-Tuning) training data from book chunks or CSV files.

## Prerequisites

1.  **OpenAI API Key**: Ensure you have a `.env` file in `scripts/` or project root with:
    ```
    OPENAI_API_KEY=sk-...
    ```
2.  **Dependencies**:
    ```bash
    pip install openai python-dotenv
    ```
3.  **Data**: 
    - A JSON file `data/MY_BOOKSHOP_BOOKCHUNKS_*.json` containing your book chunks.
    - OR a CSV file with a `TEXT` or `content` column.

---

## 🚀 Recommended: Full Pipeline (One-Click)

The **Full Pipeline** automates the entire process: CSV conversion, Question Generation, Answer Generation, and Batch Management.

```bash
python scripts/full_pipeline.py --input data/my_book_chunks.csv
```

**What it does:**
1. Validates input and converts CSV to JSON if needed.
2. **Generates Questions**: Prepares batch, submits to OpenAI, waits for completion, and processes results.
3. **Generates Answers**: Prepares batch (using generated questions), submits, waits, and processes results.
4. **Final Output**: `data/training_dataset.jsonl` ready for fine-tuning.

*Note: This runs purely sequentially. For large datasets, it may take 24+ hours due to OpenAI Batch queue times.*

### 📦 Handling Large Datasets (Token Limits)

OpenAI has a limit of **2,000,000 enqueued tokens** per tier on the Batch API. If you have >3,000 chunks, you may hit `token_limit_exceeded`.

To fix this, process your data in parts using `--limit` and `--offset`:

**Run 1 (First 1600 chunks):**
```bash
python scripts/full_pipeline.py --input data/book_chunks.json --limit 1600
# Rename output to avoid overwriting
mv data/training_dataset.jsonl data/training_part1.jsonl
```

**Run 2 (Next 1600 chunks):**
```bash
python scripts/full_pipeline.py --input data/book_chunks.json --limit 1600 --offset 1600
# Rename output
mv data/training_dataset.jsonl data/training_part2.jsonl
```

---

## 🔄 Targeted Retry & Verification

If some chunks were missed during the pipeline execution (e.g. checks failed, or specific errors), you can retry just those chunks and append them to your training set.

### 1. Verify and Find Missing Chunks
Run the verification script to identify missing chunks and save their IDs:
```bash
python scripts/verify_bookchunks.py --output data/missing_chunks.jsonl
```

### 2. Run Pipeline for Missing Chunks
Use the generated ID file to run the pipeline only for the missing chunks and append the results:
```bash
python scripts/full_pipeline.py \
  --input data/MY_BOOKSHOP_BOOKCHUNKS_202512070110.json \
  --chunk-ids-file data/missing_chunks.jsonl \
  --append
```

**Arguments:**
- `--chunk-ids-file`: Path to the JSONL file containing `{"id": "..."}` of chunks to process.
- `--append`: Appends the new results to `data/training_dataset.jsonl` instead of overwriting.

---

## 🛠️ Advanced: Manual Batch Workflow

Use this if you want more control (e.g., submitting both batches in parallel to save time).

### 1. Questions Step

**Option A: Submit and Wait (Blocking)**
```bash
python scripts/generate_questions.py --mode prepare
python scripts/openai_batch.py --file data/questions_batch_input.jsonl --output data/questions_batch_output.jsonl
python scripts/generate_questions.py --mode process
```

**Option B: Submit Only (Non-Blocking)**
```bash
python scripts/generate_questions.py --mode prepare
python scripts/openai_batch.py --file data/questions_batch_input.jsonl --output data/questions_batch_output.jsonl --submit-only
# Note the Batch ID!
# Check status later:
python scripts/openai_batch.py --batch-id <BATCH_ID> --output data/questions_batch_output.jsonl
```

### 2. Answers Step

(Follows the same pattern as Questions, using `generate_answers.py`)

```bash
python scripts/generate_answers.py --mode prepare
python scripts/openai_batch.py --file data/answers_batch_input.jsonl --output data/answers_batch_output.jsonl
python scripts/generate_answers.py --mode process
```

### ⚡ Parallel Batch Submission

You can submit **both** batches at once and wait for both:

1. Prepare both:
   ```bash
   python scripts/generate_questions.py --mode prepare
   python scripts/generate_answers.py --mode prepare
   ```
2. Submit & Monitor both:
   ```bash
   python scripts/openai_batch.py --batch-jobs data/questions_batch_input.jsonl:data/questions_batch_output.jsonl data/answers_batch_input.jsonl:data/answers_batch_output.jsonl
   ```
3. Process both:
   ```bash
   python scripts/generate_questions.py --mode process
   python scripts/generate_answers.py --mode process
   ```

---

## 🧪 Quick Test (Synchronous)

For small data (e.g. testing 10 chunks) without waiting for batches:

```bash
python scripts/generate_questions.py --mode sync --limit 10
python scripts/generate_answers.py --mode sync
```

---

## Output Format (Alpaca)

The final `data/training_dataset.jsonl` follows the Alpaca format for fine-tuning:

```json
{
  "instruction": "You are a helpful Bookshop Assistant...",
  "input": "CONTEXT: [Book Text] ... QUESTION: [Generated Question]",
  "output": "{\"reply\": \"[Answer]\", \"vectorSearch\": true}"
}
```

---

## 🧠 Step 2: Fine-Tuning (Colab)

Once you have `data/training_dataset.jsonl`, you can fine-tune Llama 3.1 8B using Unsloth on Google Colab.

**Reference Colab Notebook:**
[Google Colab: Unsloth Llama 3.1 8B Training](https://colab.research.google.com/drive/1Ys44kVvmeZtnICzWz0xgpRnrIOjZAuxp?usp=sharing#scrollTo=pCqnaKmlO1U9)

### Process
1.  **Setup**: Use the Unsloth Llama 3.1 8B model.
2.  **Upload**: Upload your `data/training_dataset.jsonl` to the Colab environment.
3.  **Train**: Run the training cells. (Approximate duration: 5 hours for ~15k examples).

### Export to Hugging Face
After training, export the merged model to Hugging Face using the following script:

```python
from huggingface_hub import login

login("hf-token") 

model.push_to_hub_merged(
    "GasparDoesAI/bookshop-llama3-v1",
    save_method = "merged_16bit",
    token = "hf-token"
)
```

This merges the LoRA adapters into the base model for easier deployment.

---

## 🚀 Step 3: Run Inference (Colab)

After fine-tuning, you can run an inference API directly in Colab to test your model or connect it to the application.

### 1. Install Dependencies

```python
!pip install unsloth
!pip install fastapi uvicorn pyngrok nest_asyncio
```

### 2. Run Inference API

Run the following cell to start the FastAPI server with ngrok tunneling.

```python
from unsloth import FastLanguageModel
import uvicorn
import nest_asyncio
import time
from fastapi import FastAPI
from pyngrok import ngrok
from pydantic import BaseModel
from typing import List, Optional

# Load Model
model, tokenizer = FastLanguageModel.from_pretrained(
    model_name = "GasparDoesAI/bookshop-llama3-v1", # Replace with your fine-tuned model
    load_in_4bit = True,
    max_seq_length = 4096,
)
FastLanguageModel.for_inference(model)

app = FastAPI()

class Message(BaseModel):
    role: str
    content: str

class ChatCompletionRequest(BaseModel):
    model: str = "gpt-4o-mini"
    messages: List[Message]
    temperature: Optional[float] = 0.1
    max_tokens: Optional[int] = 512

alpaca_template = """Below is an instruction that describes a task, paired with an input that provides further context. Write a response that appropriately completes the request.

### Instruction:
{}

### Input:
{}

### Response:
"""

@app.post("/v1/chat/completions")
async def chat_completions(request: ChatCompletionRequest):
    system_instruction = ""
    user_input = ""
    
    for msg in request.messages:
        if msg.role == "system":
            system_instruction = msg.content
        elif msg.role == "user":
            user_input = msg.content

    formatted_prompt = alpaca_template.format(system_instruction, user_input)

    inputs = tokenizer([formatted_prompt], return_tensors="pt").to("cuda")
    
    outputs = model.generate(
        **inputs, 
        max_new_tokens=request.max_tokens,
        temperature=request.temperature,
        use_cache=True
    )
    
    clean_response = tokenizer.batch_decode(outputs)[0].split("### Response:\\n")[-1].replace("<|end_of_text|>", "").strip()

    return {
        "id": "chatcmpl-bookshop",
        "object": "chat.completion",
        "created": int(time.time()),
        "model": request.model,
        "choices": [{"index": 0, "message": {"role": "assistant", "content": clean_response}, "finish_reason": "stop"}],
        "usage": {"prompt_tokens": len(inputs[0]), "completion_tokens": len(outputs[0]) - len(inputs[0]), "total_tokens": len(outputs[0])}
    }

# Start Server
NGROK_TOKEN = "36Zk8kaOik190bNsxPXlcZEAUY8_2rANYhic9fHzCcBnQUVeR" # Replace with your token if needed
ngrok.set_auth_token(NGROK_TOKEN)
ngrok.kill()
public_url = ngrok.connect(8000).public_url
print(f"🚀 API LIVE AT: {public_url}")
print(f"👉 Configure your backend URL to: {public_url}/v1")

nest_asyncio.apply()

config = uvicorn.Config(app, port=8000)
server = uvicorn.Server(config)
await server.serve()
```

### 3. Memory Cleanup

If you need to restart or free up resources:

```python
import gc
import platform

def clean_memory():
    """
    Force-clears system RAM and GPU VRAM (for PyTorch/TensorFlow/Numba).
    """
    print("Starting memory cleanup...")
    
    gc.collect()
    print("- System RAM (Garbage Collector): Done")

    try:
        import torch
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
            torch.cuda.ipc_collect()
            print(f"   - PyTorch GPU Cache: Cleared (Allocated: {torch.cuda.memory_allocated() / 1024**3:.2f} GB)")
        else:
            print("   - PyTorch: No GPU detected or PyTorch not installed")
    except ImportError:
        pass

    try:
        import tensorflow as tf
        tf.keras.backend.clear_session()
        print("- TensorFlow/Keras Session: Cleared")
    except ImportError:
        pass

    try:
        from numba import cuda
        device = cuda.get_current_device()
        device.reset()
        print("- Numba: GPU Device Reset (Aggressive)")
    except ImportError:
        pass
    except Exception as e:
        pass

    gc.collect()
    print("Memory cleanup finished.")

if __name__ == "__main__":
    clean_memory()
```

### Configuration

Copy the public URL (e.g., `https://...ngrok-free.app/v1`) and a dummy token (some value like `dummy`) into your `application.local.properties`:

```properties
my.bookshop.rag.google-colab.url=https://your-ngrok-url.ngrok-free.app/v1
my.bookshop.rag.google-colab.token=dummy
```

