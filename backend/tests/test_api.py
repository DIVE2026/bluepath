from __future__ import annotations

import os
import sys
from io import BytesIO
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
TEST_DB = Path('/tmp/bluepath_test_api.db')
TEST_DB.unlink(missing_ok=True)
os.environ['DATABASE_URL'] = f'sqlite:///{TEST_DB}'
os.environ['JWT_SECRET'] = 'test-secret-that-is-more-than-thirty-two-bytes-long'
os.environ['ADMIN_EMAIL'] = 'admin@bluepath.example.com'
os.environ['ADMIN_PASSWORD'] = 'AdminPassword123!'
os.environ['YOUTUBE_SYNC_HOURS'] = '0'
os.environ['LLM_BASE_URL'] = ''
os.environ['LLM_MODEL'] = ''

from fastapi.testclient import TestClient

from backend.app.main import app


def auth_header(token: str) -> dict[str, str]:
    return {'Authorization': f'Bearer {token}'}


def test_registration_sync_quiz_and_diamond_pathway() -> None:
    with TestClient(app) as client:
        register = client.post('/api/v1/auth/register', json={
            'email': 'learner@bluepath.example.com',
            'password': 'LearnerPassword123!',
            'guardianEmail': None,
            'nickname': 'OceanLearner',
        })
        assert register.status_code == 200, register.text
        token = register.json()['accessToken']

        quiz = client.post('/api/v1/ai/quiz', headers=auth_header(token), json={
            'tier': '브론즈',
            'profile': {'interest': '해양환경', 'level': '초급'},
            'videos': [],
        })
        assert quiz.status_code == 200, quiz.text
        questions = quiz.json()['questions']
        assert len(questions) == 10
        assert all(len(item['options']) == 4 for item in questions)
        assert all(0 <= item['answerIndex'] <= 3 for item in questions)
        assert all(item['explanation'] for item in questions)

        search = client.post('/api/v1/ai/search', headers=auth_header(token), json={
            'query': '해양환경 입문 영상',
            'resourceType': 'video',
            'limit': 5,
        })
        assert search.status_code == 200, search.text
        assert isinstance(search.json()['items'], list)
        assert search.json()['summary']

        sync = client.post('/api/v1/sync', headers=auth_header(token), json={
            'snapshot': {
                'tier': '플래티넘',
                'diamondAdvancedQuizPassed': True,
                'guardianConsent': False,
            },
            'learningRecords': [{
                'id': 'local-1',
                'recordType': 'quiz',
                'targetId': '플래티넘',
                'title': 'Advanced promotion quiz',
                'status': 'passed',
                'updatedAt': 1,
                'synced': False,
            }],
        })
        assert sync.status_code == 200, sync.text
        assert sync.json()['diamondStatus']['advancedQuizPassed'] is True
        assert sync.json()['diamondStatus']['eligible'] is False
        cloud = client.get('/api/v1/sync', headers=auth_header(token))
        assert cloud.status_code == 200
        assert cloud.json()['snapshot']['tier'] == '플래티넘'

        for evidence_type in ('certification', 'project'):
            submitted = client.post('/api/v1/diamond/evidence', headers=auth_header(token), json={
                'evidenceType': evidence_type,
                'title': f'{evidence_type} evidence',
                'evidenceUrl': f'https://example.com/{evidence_type}',
            })
            assert submitted.status_code == 200, submitted.text

        status = client.get('/api/v1/diamond/status', headers=auth_header(token))
        assert status.status_code == 200
        assert status.json()['certificationStatus'] == 'pending'
        assert status.json()['projectStatus'] == 'pending'


def test_admin_content_and_quiz_management() -> None:
    with TestClient(app) as client:
        login = client.post('/api/v1/auth/login', json={
            'email': 'admin@bluepath.example.com',
            'password': 'AdminPassword123!',
            'guardianEmail': None,
        })
        assert login.status_code == 200, login.text
        headers = auth_header(login.json()['accessToken'])

        radar = client.get('/api/v1/admin/analytics/demand', headers=headers)
        assert radar.status_code == 200, radar.text
        assert radar.json()['survey']['sampleSize'] == 43
        assert isinstance(radar.json()['recommendations'], list)

        content_payload = {
            'id': 'program-test-1',
            'title': 'Ocean Safety Workshop',
            'contentType': 'program',
            'source': 'BluePath Test Institute',
            'url': 'https://example.com/program',
            'difficulty': '중',
            'requiredTier': '실버',
            'topic': '해양안전',
            'careerTag': '안전관리',
            'minutes': 90,
            'startAt': '2026-08-15',
            'endAt': '2026-08-15',
            'target': '중학생 이상',
            'method': '오프라인',
            'category': '워크숍',
            'description': '해양 안전 장비를 체험하는 교육 프로그램',
        }
        saved = client.post('/api/v1/admin/content', headers=headers, json=content_payload)
        assert saved.status_code == 200, saved.text
        listed = client.get('/api/v1/admin/content?content_type=program', headers=headers)
        assert listed.status_code == 200
        assert any(item['id'] == 'program-test-1' for item in listed.json())
        catalog = client.get('/api/v1/catalog', headers=headers)
        assert catalog.status_code == 200
        catalog_item = next(item for item in catalog.json() if item['id'] == 'program-test-1')
        assert catalog_item['startAt'] == '2026-08-15'
        assert catalog_item['description'].startswith('해양 안전')

        knowledge_csv = "title,content,organization,url,topic\nMarine Safety,Use approved safety equipment,Test Institute,https://example.com/knowledge,해양안전\n"
        knowledge = client.post(
            '/api/v1/admin/knowledge/upload',
            headers=headers,
            files={'file': ('knowledge.csv', knowledge_csv.encode('utf-8'), 'text/csv')},
        )
        assert knowledge.status_code == 200, knowledge.text
        assert knowledge.json()['imported'] == 1

        quiz_csv = (
            'id,tier,topic,question,option1,option2,option3,option4,answerNumber,explanation,sourceTitle,sourceUrl\n'
            'csv-bronze-test,Bronze,해양안전,구명조끼는 어떤 기능을 제공하는가?,부력,추진력,통신,조명,1,'
            '구명조끼는 물에서 몸이 뜨도록 부력을 제공합니다.,Marine safety guide,https://example.com/safety\n'
        )
        quiz_upload = client.post(
            '/api/v1/admin/quizzes/upload',
            headers=headers,
            files={'file': ('quizzes.csv', quiz_csv.encode('utf-8'), 'text/csv')},
        )
        assert quiz_upload.status_code == 200, quiz_upload.text
        assert quiz_upload.json() == {'imported': 1, 'errors': []}

        import pandas as pd

        workbook = BytesIO()
        pd.DataFrame([{
            'id': 'xlsx-silver-test',
            'tier': 'Silver',
            'topic': '항만물류',
            'question': '항만 물류에서 화물 흐름을 관리하는 이유는 무엇인가?',
            'option1': '운영 효율과 안전을 높이기 위해',
            'option2': '바닷물의 염도를 바꾸기 위해',
            'option3': '선박의 색상을 정하기 위해',
            'option4': '파도의 방향을 바꾸기 위해',
            'answerIndex': 0,
            'explanation': '화물 흐름 관리는 항만 운영의 효율성과 안전성을 높이는 데 필요합니다.',
            'sourceTitle': 'Port logistics guide',
            'sourceUrl': 'https://example.com/port-logistics',
            'active': True,
        }]).to_excel(workbook, index=False)
        quiz_excel_upload = client.post(
            '/api/v1/admin/quizzes/upload',
            headers=headers,
            files={
                'file': (
                    'quizzes.xlsx',
                    workbook.getvalue(),
                    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                )
            },
        )
        assert quiz_excel_upload.status_code == 200, quiz_excel_upload.text
        assert quiz_excel_upload.json() == {'imported': 1, 'errors': []}

        invalid_quiz_csv = (
            'tier,question,option1,option2,option3,option4,answerNumber,explanation\n'
            '브론즈,중복 선택지는 허용되는가?,같음,같음,다름,기타,1,중복 검증 테스트\n'
        )
        invalid_quiz_upload = client.post(
            '/api/v1/admin/quizzes/upload',
            headers=headers,
            files={'file': ('invalid-quizzes.csv', invalid_quiz_csv.encode('utf-8'), 'text/csv')},
        )
        assert invalid_quiz_upload.status_code == 200
        assert invalid_quiz_upload.json()['imported'] == 0
        assert 'unique' in invalid_quiz_upload.json()['errors'][0]

        quiz_payload = {
            'id': 'admin-bronze-test',
            'tier': '브론즈',
            'topic': '해양안전',
            'question': '구명조끼의 주된 목적은 무엇인가?',
            'options': ['부력을 제공한다', '선박 속도를 높인다', '파도를 만든다', '통신을 대신한다'],
            'answerIndex': 0,
            'explanation': '구명조끼는 물에 빠졌을 때 부력을 제공해 생존 가능성을 높입니다.',
            'sourceTitle': 'Marine safety guide',
            'sourceUrl': 'https://example.com/safety',
            'active': True,
        }
        quiz_saved = client.post('/api/v1/admin/quizzes', headers=headers, json=quiz_payload)
        assert quiz_saved.status_code == 200, quiz_saved.text
        quiz_list = client.get('/api/v1/admin/quizzes?tier=브론즈', headers=headers)
        assert quiz_list.status_code == 200
        assert any(item['id'] == 'admin-bronze-test' for item in quiz_list.json())
        assert any(item['id'] == 'csv-bronze-test' for item in quiz_list.json())
        silver_quiz_list = client.get('/api/v1/admin/quizzes?tier=실버', headers=headers)
        assert silver_quiz_list.status_code == 200
        assert any(item['id'] == 'xlsx-silver-test' for item in silver_quiz_list.json())

        assert client.delete('/api/v1/admin/quizzes/admin-bronze-test', headers=headers).status_code == 200
        assert client.delete('/api/v1/admin/quizzes/csv-bronze-test', headers=headers).status_code == 200
        assert client.delete('/api/v1/admin/quizzes/xlsx-silver-test', headers=headers).status_code == 200
        assert client.delete('/api/v1/admin/content/program-test-1', headers=headers).status_code == 200


def test_rag_quiz_validator_rejects_ungrounded_or_duplicate_choices() -> None:
    import json
    import pytest

    from backend.app.models import KnowledgeChunk
    from backend.app.services import validate_quiz_payload

    sources = [KnowledgeChunk(
        id='source-1',
        title='Verified marine safety source',
        organization='BluePath Test Institute',
        url='https://example.com/source',
        content='Approved lifejackets provide buoyancy.',
        topic='해양안전',
    )]
    duplicate_choices = json.dumps({'questions': [{
        'topic': '해양안전',
        'question': '구명조끼의 목적은?',
        'options': ['부력 제공', '부력 제공', '속도 증가', '통신'],
        'answerIndex': 0,
        'explanation': '부력을 제공합니다.',
        'sourceNumbers': [1],
    }]}, ensure_ascii=False)
    missing_source = json.dumps({'questions': [{
        'topic': '해양안전',
        'question': '구명조끼의 목적은?',
        'options': ['부력 제공', '속도 증가', '통신', '파도 생성'],
        'answerIndex': 0,
        'explanation': '부력을 제공합니다.',
        'sourceNumbers': [],
    }]}, ensure_ascii=False)

    with pytest.raises(ValueError):
        validate_quiz_payload(duplicate_choices, '브론즈', 1, sources)
    with pytest.raises(ValueError):
        validate_quiz_payload(missing_source, '브론즈', 1, sources)


def test_password_reset_is_one_time_and_does_not_disclose_accounts(monkeypatch) -> None:
    from backend.app import main as main_module

    captured: dict[str, str] = {}

    def capture_reset_email(email: str, token: str) -> None:
        captured['email'] = email
        captured['token'] = token

    monkeypatch.setattr(main_module, 'send_password_reset_email', capture_reset_email)

    with TestClient(app) as client:
        reset_page = client.get('/reset-password?token=sample-token')
        assert reset_page.status_code == 200
        assert '새 비밀번호 설정' in reset_page.text

        email = 'reset-user@bluepath.example.com'
        old_password = 'OriginalPassword123!'
        new_password = 'ChangedPassword456!'
        register = client.post('/api/v1/auth/register', json={
            'email': email,
            'password': old_password,
            'guardianEmail': None,
                'nickname': 'ResetOcean',
            })
        assert register.status_code == 200, register.text

        unknown = client.post('/api/v1/auth/password-reset/request', json={
            'email': 'not-found@bluepath.example.com',
        })
        known = client.post('/api/v1/auth/password-reset/request', json={'email': email})
        assert unknown.status_code == 200
        assert known.status_code == 200
        assert unknown.json()['message'] == known.json()['message']
        assert captured['email'] == email
        assert captured['token']

        confirmed = client.post('/api/v1/auth/password-reset/confirm', json={
            'token': captured['token'],
            'newPassword': new_password,
        })
        assert confirmed.status_code == 200, confirmed.text

        reused = client.post('/api/v1/auth/password-reset/confirm', json={
            'token': captured['token'],
            'newPassword': 'AnotherPassword789!',
        })
        assert reused.status_code == 400

        old_login = client.post('/api/v1/auth/login', json={
            'email': email,
            'password': old_password,
            'guardianEmail': None,
        })
        new_login = client.post('/api/v1/auth/login', json={
            'email': email,
            'password': new_password,
            'guardianEmail': None,
        })
        assert old_login.status_code == 401
        assert new_login.status_code == 200


def test_nickname_community_follow_reactions_and_dashboard() -> None:
    with TestClient(app) as client:
        first = client.post('/api/v1/auth/register', json={
            'email': 'community-one@bluepath.example.com',
            'password': 'CommunityPassword123!',
            'nickname': 'BlueWhale',
            'guardianEmail': None,
        })
        second = client.post('/api/v1/auth/register', json={
            'email': 'community-two@bluepath.example.com',
            'password': 'CommunityPassword123!',
            'nickname': 'SeaTurtle',
            'guardianEmail': None,
        })
        assert first.status_code == 200, first.text
        assert second.status_code == 200, second.text
        duplicate = client.get('/api/v1/auth/nickname-available?nickname=BlueWhale')
        assert duplicate.status_code == 200
        assert duplicate.json()['available'] is False
        case_insensitive = client.get('/api/v1/auth/nickname-available?nickname=bluewhale')
        assert case_insensitive.status_code == 200
        assert case_insensitive.json()['available'] is False

        case_duplicate = client.post('/api/v1/auth/register', json={
            'email': 'community-duplicate@bluepath.example.com',
            'password': 'CommunityPassword123!',
            'nickname': 'BLUEWHALE',
            'guardianEmail': None,
        })
        assert case_duplicate.status_code == 409

        first_headers = auth_header(first.json()['accessToken'])
        second_headers = auth_header(second.json()['accessToken'])
        png_1x1 = (
            b'\x89PNG\r\n\x1a\n'
            b'\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89'
            b'\x00\x00\x00\rIDAT\x08\xd7c\xf8\xcf\xc0\xf0\x1f\x00\x05\x00\x01\xff\x89\x99\x8d\x1d'
            b'\x00\x00\x00\x00IEND\xaeB`\x82'
        )
        uploaded = client.post('/api/v1/profile/image', headers=first_headers, files={
            'file': ('avatar.png', png_1x1, 'image/png'),
        })
        assert uploaded.status_code == 200, uploaded.text
        assert uploaded.json()['profileImageUrl'].endswith('.png')
        post = client.post('/api/v1/community/posts', headers=first_headers, json={
            'category': 'question',
            'title': '해양 진로 질문',
            'body': '항만 물류 진로를 준비하려면 무엇부터 시작해야 하나요?',
        })
        assert post.status_code == 200, post.text
        post_id = post.json()['id']
        author_id = post.json()['author']['userId']

        comment = client.post(f'/api/v1/community/posts/{post_id}/comments', headers=second_headers, json={
            'body': '기초 물류 영상과 현장 교육부터 추천합니다.',
            'parentId': None,
        })
        assert comment.status_code == 200, comment.text
        reply = client.post(f'/api/v1/community/posts/{post_id}/comments', headers=first_headers, json={
            'body': '답변 감사합니다.',
            'parentId': comment.json()['id'],
        })
        assert reply.status_code == 200, reply.text
        reaction = client.post('/api/v1/community/reactions', headers=second_headers, json={
            'targetType': 'post', 'targetId': post_id, 'emoji': '🌊',
        })
        assert reaction.status_code == 200, reaction.text
        assert reaction.json()['active'] is True
        follow = client.post(f'/api/v1/community/users/{author_id}/follow', headers=second_headers)
        assert follow.status_code == 200, follow.text
        assert follow.json()['following'] is True

        feed = client.get('/api/v1/community/posts?category=question', headers=second_headers)
        assert feed.status_code == 200, feed.text
        item = feed.json()[0]
        assert len(item['comments']) == 2
        assert item['author']['isFollowing'] is True
        assert any(value['emoji'] == '🌊' and value['count'] == 1 for value in item['reactions'])

        dashboard = client.get('/api/v1/dashboard', headers=first_headers)
        assert dashboard.status_code == 200, dashboard.text
        assert dashboard.json()['profile']['nickname'] == 'BlueWhale'
        assert dashboard.json()['profile']['followerCount'] == 1
        assert sum(dashboard.json()['activity'].values()) >= 2
