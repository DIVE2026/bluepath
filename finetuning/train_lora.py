#!/usr/bin/env python3
"""Train a BluePath marine adapter with TRL SFTTrainer and PEFT LoRA/QLoRA."""
from __future__ import annotations

import json
import os
from pathlib import Path

import torch
from datasets import load_dataset
from peft import LoraConfig
from transformers import AutoTokenizer, BitsAndBytesConfig
from trl import SFTConfig, SFTTrainer

ROOT = Path(__file__).resolve().parents[1]
BASE_MODEL = os.getenv("BLUEPATH_BASE_MODEL", "Qwen/Qwen2.5-3B-Instruct")
OUTPUT_DIR = Path(os.getenv("BLUEPATH_FINETUNE_OUTPUT", str(ROOT / "finetuning/output/marine-lora")))
TRAIN_FILE = os.getenv("BLUEPATH_TRAIN_FILE", str(ROOT / "finetuning/data/train.jsonl"))
VALIDATION_FILE = os.getenv("BLUEPATH_VALIDATION_FILE", str(ROOT / "finetuning/data/validation.jsonl"))
USE_4BIT = os.getenv("BLUEPATH_USE_4BIT", "true").lower() == "true"
SEED = int(os.getenv("BLUEPATH_SEED", "20260711"))


def build_quantization_config() -> BitsAndBytesConfig | None:
    if not (USE_4BIT and torch.cuda.is_available()):
        return None
    compute_dtype = torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16
    return BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=compute_dtype,
        bnb_4bit_use_double_quant=True,
    )


def main() -> None:
    dataset = load_dataset("json", data_files={"train": TRAIN_FILE, "validation": VALIDATION_FILE})
    tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    tokenizer.padding_side = "right"

    target_value = os.getenv("BLUEPATH_LORA_TARGETS", "all-linear").strip()
    target_modules: str | list[str] = target_value
    if target_value != "all-linear":
        target_modules = [value.strip() for value in target_value.split(",") if value.strip()]

    peft_config = LoraConfig(
        r=int(os.getenv("BLUEPATH_LORA_R", "16")),
        lora_alpha=int(os.getenv("BLUEPATH_LORA_ALPHA", "32")),
        lora_dropout=float(os.getenv("BLUEPATH_LORA_DROPOUT", "0.05")),
        bias="none",
        task_type="CAUSAL_LM",
        target_modules=target_modules,
    )

    quantization_config = build_quantization_config()
    training_args = SFTConfig(
        output_dir=str(OUTPUT_DIR),
        num_train_epochs=float(os.getenv("BLUEPATH_EPOCHS", "3")),
        per_device_train_batch_size=int(os.getenv("BLUEPATH_TRAIN_BATCH", "1")),
        per_device_eval_batch_size=int(os.getenv("BLUEPATH_EVAL_BATCH", "1")),
        gradient_accumulation_steps=int(os.getenv("BLUEPATH_GRAD_ACCUM", "8")),
        learning_rate=float(os.getenv("BLUEPATH_LEARNING_RATE", "2e-4")),
        warmup_ratio=float(os.getenv("BLUEPATH_WARMUP_RATIO", "0.05")),
        weight_decay=float(os.getenv("BLUEPATH_WEIGHT_DECAY", "0.01")),
        logging_steps=int(os.getenv("BLUEPATH_LOGGING_STEPS", "5")),
        eval_strategy="epoch",
        save_strategy="epoch",
        save_total_limit=int(os.getenv("BLUEPATH_SAVE_TOTAL_LIMIT", "2")),
        load_best_model_at_end=True,
        metric_for_best_model="eval_loss",
        greater_is_better=False,
        bf16=torch.cuda.is_available() and torch.cuda.is_bf16_supported(),
        fp16=torch.cuda.is_available() and not torch.cuda.is_bf16_supported(),
        gradient_checkpointing=True,
        max_length=int(os.getenv("BLUEPATH_MAX_SEQ_LENGTH", "2048")),
        packing=os.getenv("BLUEPATH_PACKING", "false").lower() == "true",
        report_to=os.getenv("BLUEPATH_REPORT_TO", "none"),
        run_name=os.getenv("BLUEPATH_RUN_NAME", "bluepath-marine-sft"),
        assistant_only_loss=os.getenv("BLUEPATH_ASSISTANT_ONLY_LOSS", "false").lower() == "true",
        eos_token=os.getenv("BLUEPATH_EOS_TOKEN") or ("<|im_end|>" if "qwen" in BASE_MODEL.lower() else None),
        dataset_num_proc=int(os.getenv("BLUEPATH_DATASET_NUM_PROC", "1")),
        trust_remote_code=True,
        seed=SEED,
        data_seed=SEED,
        optim="paged_adamw_8bit" if quantization_config else "adamw_torch",
        model_init_kwargs={"dtype": "auto"},
    )

    trainer = SFTTrainer(
        model=BASE_MODEL,
        args=training_args,
        train_dataset=dataset["train"],
        eval_dataset=dataset["validation"],
        processing_class=tokenizer,
        peft_config=peft_config,
        quantization_config=quantization_config,
    )
    trainer.train(resume_from_checkpoint=os.getenv("BLUEPATH_RESUME_CHECKPOINT") or None)
    trainer.save_model(str(OUTPUT_DIR))
    tokenizer.save_pretrained(str(OUTPUT_DIR))

    run_manifest = {
        "baseModel": BASE_MODEL,
        "output": str(OUTPUT_DIR),
        "trainFile": TRAIN_FILE,
        "validationFile": VALIDATION_FILE,
        "seed": SEED,
        "use4Bit": bool(quantization_config),
        "loraTargets": target_modules,
        "bestCheckpoint": trainer.state.best_model_checkpoint,
        "bestMetric": trainer.state.best_metric,
    }
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUTPUT_DIR / "bluepath_training_manifest.json").write_text(
        json.dumps(run_manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(run_manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
