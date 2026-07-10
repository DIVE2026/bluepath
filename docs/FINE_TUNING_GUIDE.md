# BluePath Marine Model Fine-Tuning Guide

BluePath uses a two-layer AI design:

1. **RAG supplies current, inspectable marine evidence.**
2. **Fine-tuning teaches stable behavior, format, tone, quiz structure, source discipline, and career-guidance patterns.**

Fine-tuning should not be used as a replacement for current laws, license requirements, schedules, or institutional notices. Those belong in the RAG knowledge base so they can be updated without retraining the model.

## Included pipeline

```text
scripts/build_marine_finetune_dataset.py   Build deterministic train/validation/evaluation splits
scripts/validate_marine_dataset.py         Validate chat structure and quiz JSON
finetuning/train_lora.py                   Train a PEFT LoRA adapter with TRL SFTTrainer
finetuning/evaluate_model.py               Run automated format, grounding, privacy, and safety checks
finetuning/compare_models.py                Compare the held-out score of the base and adapted models
finetuning/serve_model.py                  Serve the adapter through an OpenAI-compatible API
finetuning/data/                            Generated datasets and manifest
finetuning/.env.example                    Developer-entered training and serving values
```

## What the dataset teaches

The generated examples include:

- Bronze, Silver, Gold, and Platinum four-option quiz generation
- Balanced correct-answer positions
- Explanations and source-number fields
- Grounded marine education answers
- Tier promotion rules and tier emojis
- NCS and marine-career learning guidance
- Refusal to fabricate sources
- Caution for changing laws, licenses, schedules, and professional requirements
- Protection of API secrets and minor learner data
- Diamond certification and project requirements

## Step 1 — Build and inspect the dataset

```bash
python scripts/build_marine_finetune_dataset.py
python scripts/validate_marine_dataset.py \
  finetuning/data/train.jsonl \
  finetuning/data/validation.jsonl
```

Review the generated files before every production training run. Domain experts should check factual accuracy, terminology, difficulty, answer uniqueness, explanation quality, and whether every cited item is supported by the supplied evidence.

## Step 2 — Add approved institutional examples

Add organization-approved conversations in the same chat JSONL shape:

```json
{"messages":[
  {"role":"system","content":"BluePath Marine AI system instruction"},
  {"role":"user","content":"Evidence and learner request"},
  {"role":"assistant","content":"Grounded answer or validated quiz JSON"}
]}
```

Keep training, validation, and evaluation items separate. Do not put evaluation cases into the training file.

Recommended review dimensions:

- Marine-domain correctness
- Age and tier appropriateness
- Four-choice quiz validity
- Correct answer and distractor quality
- Explanation usefulness
- Source fidelity
- Safety and uncertainty handling
- Personal-data minimization

## Step 3 — Prepare the training environment

A CUDA GPU is recommended. Create an isolated environment and install the training packages:

```bash
python -m venv .venv-finetune
source .venv-finetune/bin/activate
pip install -r finetuning/requirements.txt
cp finetuning/.env.example finetuning/.env
set -a; source finetuning/.env; set +a
```

Set `BLUEPATH_BASE_MODEL` to a chat model whose license permits the intended use and deployment.

## Step 4 — Train the LoRA adapter

```bash
python finetuning/train_lora.py
```

The default run uses supervised fine-tuning with a LoRA adapter. The base-model weights remain unchanged while smaller trainable adapter matrices learn BluePath behavior. On compatible CUDA systems, the script uses NF4 4-bit loading and LoRA across all linear layers for a QLoRA-style run. It also records the base model, seed, adapter targets, best checkpoint, and best validation metric in `bluepath_training_manifest.json`.

Important environment variables:

```dotenv
BLUEPATH_BASE_MODEL=Qwen/Qwen2.5-3B-Instruct
BLUEPATH_EPOCHS=3
BLUEPATH_LEARNING_RATE=0.0002
BLUEPATH_LORA_R=16
BLUEPATH_LORA_ALPHA=32
BLUEPATH_LORA_TARGETS=all-linear
BLUEPATH_MAX_SEQ_LENGTH=2048
BLUEPATH_ASSISTANT_ONLY_LOSS=false
BLUEPATH_EOS_TOKEN=<|im_end|>
BLUEPATH_SEED=20260711
BLUEPATH_FINETUNE_OUTPUT=./finetuning/output/marine-lora
```

Use validation loss to choose the best checkpoint. More epochs are not automatically better; stop or reduce training when validation quality worsens. `BLUEPATH_ASSISTANT_ONLY_LOSS` is disabled by default for broad tokenizer compatibility; enable it only after confirming that the selected model's chat template exposes an assistant-generation mask.

## Step 5 — Serve the trained adapter

```bash
export BLUEPATH_BASE_MODEL="Qwen/Qwen2.5-3B-Instruct"
export BLUEPATH_ADAPTER_PATH="./finetuning/output/marine-lora"
export BLUEPATH_SERVED_MODEL="bluepath-marine"
export BLUEPATH_MODEL_API_KEY="replace-with-a-long-random-value"
export BLUEPATH_SERVE_4BIT="true"
uvicorn finetuning.serve_model:app --host 0.0.0.0 --port 8001
```

The server exposes:

```text
POST /v1/chat/completions
GET  /health
```

Only the FastAPI backend should know `BLUEPATH_MODEL_API_KEY`. Do not place it in Android resources, `BuildConfig`, source code, or My Page.

## Step 6 — Evaluate before deployment

```bash
export BLUEPATH_EVAL_BASE_URL="http://localhost:8001/v1"
export BLUEPATH_EVAL_MODEL="bluepath-marine"
export BLUEPATH_EVAL_API_KEY="$BLUEPATH_MODEL_API_KEY"
python finetuning/evaluate_model.py
```

The evaluation script checks:

- Non-empty answers
- Parseable quiz JSON and the expected question count
- Exactly four unique options
- Valid answer indexes, explanations, and source-number fields
- Citation markers for grounded answers
- Fabricated URLs against the evidence supplied to the case
- Accidental API-key or bearer-token leakage
- Official-verification language for changing rules, licenses, and schedules

The generated report is saved to `finetuning/output/evaluation_report.json`. Automated checks are a release gate, not a substitute for expert review.

To compare the candidate against the same base model before adaptation, expose both through OpenAI-compatible endpoints and run:

```bash
export BLUEPATH_BASELINE_URL="http://localhost:8002/v1"
export BLUEPATH_BASELINE_MODEL="base-model-name"
export BLUEPATH_EVAL_BASE_URL="http://localhost:8001/v1"
export BLUEPATH_EVAL_MODEL="bluepath-marine"
python finetuning/compare_models.py
```

The comparison fails when the candidate is below the minimum pass rate or performs worse than the baseline on the held-out checks. The detailed result is written to `finetuning/output/model_comparison.json`.

## Step 7 — Connect the model to BluePath

Enter the model server values in `backend/.env`:

```dotenv
LLM_BASE_URL=http://host.docker.internal:8001/v1
LLM_API_KEY=replace-with-the-model-serving-key
LLM_MODEL=bluepath-marine
```

Restart the API. The same model is then used by the RAG AI Agent and promotion-quiz generator. The backend still validates every generated quiz and falls back to the verified local bank when the output is incomplete or invalid.

## Step 8 — Production release checklist

- Freeze the reviewed training and evaluation datasets with version tags.
- Record the base model, adapter configuration, random seed, dependencies, and checkpoint.
- Run automated evaluation and marine-domain expert review.
- Red-team source fabrication, unsafe maritime advice, privacy leakage, and prompt injection.
- Deploy behind authentication, rate limits, request logging with personal-data minimization, and rollback support.
- Monitor failed quiz validation, unsupported answers, latency, and user feedback.
- Update changing facts through RAG; retrain only when behavior or domain coverage needs improvement.
