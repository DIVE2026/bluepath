# BluePath Marine LLM setup

## Included implementation

- `MarineLlmClient` calls an OpenAI-compatible chat-completions endpoint for both quiz generation and the in-app AI agent.
- `marine_finetune_dataset.jsonl` is a marine-domain starter dataset generated from the verified local quiz bank plus agent dialogues.
- `build_marine_finetune_dataset.py` rebuilds the JSONL file and rotates correct-answer positions to reduce answer-position bias.
- The app validates that an LLM quiz contains exactly the required number of questions, four options per question, a valid answer index, and a non-empty explanation. Invalid responses fall back to the local marine quiz bank.

## Training and deployment flow

1. Run `python3 scripts/build_marine_finetune_dataset.py`.
2. Review the JSONL for factual accuracy and add organization-approved marine references and examples.
3. Fine-tune a chat model with the generated JSONL using the selected model provider or an internal training platform.
4. Deploy the trained model behind an authenticated OpenAI-compatible endpoint.
5. In the app's My Page, set the endpoint, fine-tuned model ID, and API key.

The repository cannot perform the provider-side training job by itself because training requires model-provider credentials or a GPU training environment. The included files make the app training-ready and connect the resulting marine model to quizzes and the AI agent. For production, keep API keys on a server proxy rather than storing them in the Android client.
