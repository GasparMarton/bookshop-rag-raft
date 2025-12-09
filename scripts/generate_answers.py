import json
import os
import argparse
import sys
from openai import OpenAI
from dotenv import load_dotenv

load_dotenv()

# Single Answer Prompt (for final training data)
SYSTEM_PROMPT_SINGLE = """You are a helpful Bookshop Assistant.
Answer the customer's question based strictly on the provided context.
Keep your response short and concise. Do not offer to place holds, check live stock, or mention real-time availability.
Determine if a database search is needed to find relevant books (e.g. if the user asks to find, show, or recommend books).
Output your response as a JSON object with keys "reply" (string) and "vectorSearch" (boolean).
If "vectorSearch" is true, explicitly state in the reply that you have searched for relevant books."""

# Batch Answer Prompt (for generation)
SYSTEM_PROMPT_BATCH = """You are a helpful Bookshop Assistant.
You will be provided with a text CONTEXT and a list of QUESTIONS.
Answer the customer's questions based strictly on the provided context.
For EACH question, generate a short, concise answer based strictly on the context. Do not offer to place holds, check live stock, or mention real-time availability.
Determine if a database search is needed to find relevant books (e.g. if the user asks to find, show, or recommend books).
Output your response as a JSON object with keys "reply" (string) and "vectorSearch" (boolean).
If "vectorSearch" is true, explicitly state in the reply that you have searched for relevant books.
Format: [{"reply": "...", "vectorSearch": true/false}, ...]"""

def get_single_user_prompt(context, question):
    return f"""CONTEXT: {context}

QUESTION: {question}"""

def get_batch_user_prompt(context, questions):
    q_list = "\n".join([f"{i+1}. {q}" for i, q in enumerate(questions)])
    return f"""CONTEXT: {context}

QUESTIONS:
{q_list}

Provide a JSON list of {len(questions)} answers."""

def generate_answers_sync(client: OpenAI, context, questions, model="gpt-4o-mini"):
    try:
        completion = client.chat.completions.create(
            model=model,
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT_BATCH},
                {"role": "user", "content": get_batch_user_prompt(context, questions)}
            ]
        )
        content = completion.choices[0].message.content
        return clean_json_content(content)
    except Exception as e:
        print(f"Error generating answers: {e}")
        return []

def clean_json_content(content):
    if content.startswith("```json"): content = content[7:]
    if content.startswith("```"): content = content[3:]
    if content.endswith("```"): content = content[:-3]
    return json.loads(content.strip())

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["sync", "prepare", "process"], default="sync", help="Execution mode")
    parser.add_argument("--input", default="data/generated_questions.jsonl")
    parser.add_argument("--batch-input", default="data/answers_batch_input.jsonl")
    parser.add_argument("--batch-output", default="data/answers_batch_output.jsonl")
    parser.add_argument("--final-output", default="data/training_dataset.jsonl")
    parser.add_argument("--group-map", default="data/answer_group_map.json")
    parser.add_argument("--append", action="store_true", help="Append to final output instead of overwriting")
    args = parser.parse_args()

    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        print("Please set OPENAI_API_KEY")
        return

    client = OpenAI(api_key=api_key)

    # Load Questions
    if not os.path.exists(args.input):
        print(f"{args.input} not found.")
        return

    with open(args.input, 'r', encoding='utf-8') as f:
        questions_lines = [json.loads(line) for line in f if line.strip()]
    
    # Group by Context
    groups = {} # context_hash -> {context: str, questions: [{q_text, orig_idx}]}
    for i, item in enumerate(questions_lines):
        ctx = item['context']
        # Use simple hashing or string key
        if ctx not in groups:
            groups[ctx] = {"context": ctx, "questions": []}
        groups[ctx]["questions"].append({"text": item['question'], "orig_index": i})
    
    group_list = list(groups.values())
    print(f"Found {len(questions_lines)} questions, grouped into {len(group_list)} contexts.")

    # SYNC MODE
    if args.mode == "sync":
        with open(args.final_output, 'w', encoding='utf-8') as f:
            for g_idx, group in enumerate(group_list):
                print(f"Processing Group {g_idx+1}/{len(group_list)}...")
                q_texts = [q['text'] for q in group['questions']]
                answers = generate_answers_sync(client, group['context'], q_texts)
                
                if len(answers) != len(q_texts):
                    print(f"Warning: Got {len(answers)} answers for {len(q_texts)} questions. Skipping group.")
                    continue

                for i, ans in enumerate(answers):
                    orig_q_idx = group['questions'][i]['orig_index']
                    q_text = group['questions'][i]['text']
                    
                    entry = {
                        "instruction": SYSTEM_PROMPT_SINGLE.replace("\n", " "),
                        "input": get_single_user_prompt(group['context'], q_text),
                        "output": json.dumps(ans, ensure_ascii=False)
                    }
                    f.write(json.dumps(entry, ensure_ascii=False) + "\n")

    # PREPARE MODE
    elif args.mode == "prepare":
        with open(args.batch_input, 'w', encoding='utf-8') as f:
            msg_list = []
            
            for g_idx, group in enumerate(group_list):
                q_texts = [q['text'] for q in group['questions']]
                custom_id = f"group-{g_idx}"
                
                # Save mapping for process step
                msg_list.append({
                    "batch_id": custom_id,
                    "question_indices": [q['orig_index'] for q in group['questions']]
                })

                request = {
                    "custom_id": custom_id,
                    "method": "POST",
                    "url": "/v1/chat/completions",
                    "body": {
                        "model": "gpt-4o-mini",
                        "messages": [
                            {"role": "system", "content": SYSTEM_PROMPT_BATCH},
                            {"role": "user", "content": get_batch_user_prompt(group['context'], q_texts)}
                        ]
                    }
                }
                f.write(json.dumps(request) + "\n")
        
        # Save mapping
        with open(args.group_map, 'w', encoding='utf-8') as f:
            json.dump(msg_list, f)
        
        print(f"Batch input saved to {args.batch_input}")
        print(f"Group mapping saved to {args.group_map}")

    # PROCESS MODE
    elif args.mode == "process":
        if not os.path.exists(args.group_map):
            print("Group map not found. Cannot process.")
            return

        with open(args.group_map, 'r') as f:
            group_map = {item['batch_id']: item['question_indices'] for item in json.load(f)}

        results = []
        if os.path.exists(args.batch_output):
            with open(args.batch_output, 'r', encoding='utf-8') as f:
                for line in f:
                    try:
                        res = json.loads(line)
                        custom_id = res.get('custom_id')
                        
                        if custom_id not in group_map:
                            continue
                        
                        response_body = res.get('response', {}).get('body', {})
                        content = response_body.get('choices', [{}])[0].get('message', {}).get('content', '[]')
                        answers = clean_json_content(content)
                        
                        indices = group_map[custom_id]
                        if len(answers) != len(indices):
                            # Fallback/Error handling: if mismatched, try to map as many as possible or skip
                             print(f"Warning: {custom_id} mismatch. Questions: {len(indices)}, Answers: {len(answers)}")
                        
                        for i, ans in enumerate(answers):
                            if i >= len(indices): break
                            
                            orig_idx = indices[i]
                            orig_item = questions_lines[orig_idx]
                            
                            entry = {
                                "instruction": SYSTEM_PROMPT_SINGLE.replace("\n", " "),
                                "input": get_single_user_prompt(orig_item['context'], orig_item['question']),
                                "output": json.dumps(ans, ensure_ascii=False)
                            }
                            results.append(entry)

                    except Exception as e:
                        print(f"Error processing line: {e}")
            
            mode = 'a' if args.append else 'w'
            with open(args.final_output, mode, encoding='utf-8') as f:
                for item in results:
                    f.write(json.dumps(item, ensure_ascii=False) + "\n")
            print(f"Saved {len(results)} training examples to {args.final_output} (mode={mode})")
        else:
             print(f"Batch output {args.batch_output} not found.")

if __name__ == "__main__":
    main()
