from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))


def test_offline_quiz_bank_is_complete_and_valid() -> None:
    questions = json.loads((ROOT / 'app/src/main/assets/fallback_quizzes.json').read_text(encoding='utf-8'))
    counts = Counter(item['tier'] for item in questions)
    assert counts == {'브론즈': 10, '실버': 12, '골드': 15, '플래티넘': 20}
    assert all(len(item['options']) == 4 for item in questions)
    assert all(item['answerIndex'] in range(4) for item in questions)
    assert all(item['explanation'].strip() for item in questions)


def test_video_seed_contains_all_spreadsheet_items() -> None:
    videos = json.loads((ROOT / 'app/src/main/assets/seed_videos.json').read_text(encoding='utf-8'))
    counts = Counter(item['difficulty'] for item in videos)
    assert len(videos) == 28
    assert counts == {'하': 10, '중': 10, '상': 8}
    assert all(item['url'].startswith('http') for item in videos)


def test_program_event_and_finetuning_assets_are_complete() -> None:
    programs = json.loads((ROOT / 'app/src/main/assets/seed_programs.json').read_text(encoding='utf-8'))
    events = json.loads((ROOT / 'app/src/main/assets/seed_events.json').read_text(encoding='utf-8'))
    manifest = json.loads((ROOT / 'finetuning/data/manifest.json').read_text(encoding='utf-8'))

    assert len(programs) >= 8
    assert len(events) >= 6
    assert all(item['id'] and item['title'] and item['description'] for item in programs + events)
    split_total = sum(item['examples'] for item in manifest['splits'].values())
    category_total = sum(manifest['categories'].values())
    assert manifest['totalExamples'] == split_total == category_total
    assert manifest['totalExamples'] == 196
    assert set(manifest['categories']) == {'grounded_agent', 'policy', 'quiz', 'safety'}

    papers = json.loads((ROOT / 'app/src/main/assets/seed_papers.json').read_text(encoding='utf-8'))
    assert len(papers) >= 5
    assert all(item['id'] and item['title'] and item['authors'] and item['url'].startswith('http') for item in papers)
