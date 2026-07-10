# BluePath Developer Setup

This guide contains technical configuration that is intentionally excluded from the learner-facing Android screens.

## 1. Configure the Android API address

Copy the template and enter the backend address:

```bash
cp gradle.properties.example developer.properties
```

```properties
BLUEPATH_API_BASE_URL=http://10.0.2.2:8000/
```

`developer.properties` is ignored by Git. Android receives only the API base URL through `BuildConfig`; it never receives the LLM, embedding, YouTube, database, or administrator secrets.

The same value can be supplied without a file:

```bash
export BLUEPATH_API_BASE_URL="https://api.example.com/"
./gradlew assembleDebug
```

## 2. Configure the backend

Copy the server environment template:

```bash
cp backend/.env.example backend/.env
```

Enter values for the database, JWT signing secret, administrator account, LLM endpoint, embedding model, and YouTube Data API key. These values are read only by FastAPI.

Start PostgreSQL/pgvector and the API:

```bash
docker compose up --build db api
```

Useful addresses:

- API health: `http://localhost:8000/health`
- OpenAPI documentation: `http://localhost:8000/docs`
- Administration dashboard: `http://localhost:8000/admin`

## 3. Connect a marine model

The backend accepts any authenticated OpenAI-compatible chat-completions and embeddings endpoint. Configure:

```dotenv
LLM_BASE_URL=http://host.docker.internal:8001/v1
LLM_API_KEY=your-model-serving-key
LLM_MODEL=bluepath-marine
EMBEDDING_MODEL=your-embedding-model
```

For a self-hosted BluePath LoRA model, follow `docs/FINE_TUNING_GUIDE.md` and run the model server on port 8001.

## 4. Administrator workflow

1. Sign in at `/admin` with `ADMIN_EMAIL` and `ADMIN_PASSWORD`.
2. Upload CSV or Excel files for videos, programs, events, quizzes, or knowledge sources.
3. Review row-level validation results.
4. Run YouTube synchronization or enable the automatic synchronization interval.
5. Build embeddings after adding institutional material.
6. Review certification and project evidence for the Diamond pathway.

Recommended import headers:

```text
# Content catalog
id,contentType,title,url,difficulty,requiredTier,topic,source,startAt,endAt,target,method,category,description

# Promotion quizzes
id,tier,topic,question,option1,option2,option3,option4,answerNumber,explanation,sourceTitle,sourceUrl,active

# RAG knowledge
id,title,content,organization,url,topic
```

For quiz imports, `answerNumber` uses 1–4. The importer also accepts `answerIndex` using 0–3 or an `answer` value that exactly matches one option. Each row is checked for a supported tier, four non-empty unique options, a valid answer, and a non-empty explanation before it is saved.

## 5. Automatic YouTube synchronization

Set an interval and default queries in `backend/.env`:

```dotenv
YOUTUBE_SYNC_HOURS=24
YOUTUBE_SYNC_QUERIES=해양 교육,해양 환경 교육,스마트 항만,자율운항선박
```

A value of `0` disables periodic synchronization. Manual synchronization remains available in the admin dashboard.

## 6. Run tests

```bash
python scripts/build_marine_finetune_dataset.py
python scripts/validate_marine_dataset.py finetuning/data/train.jsonl finetuning/data/validation.jsonl
pytest backend/tests
./gradlew test connectedAndroidTest
```

`connectedAndroidTest` requires an Android emulator or physical device.
