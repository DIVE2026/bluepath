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
os.environ['QR_SIGNING_SECRET'] = 'test-qr-secret-that-is-more-than-thirty-two-bytes-long'

from fastapi.testclient import TestClient

from backend.app.main import app
from backend.app.database import SessionLocal
from backend.app.models import QuizSession


def auth_header(token: str) -> dict[str, str]:
    return {'Authorization': f'Bearer {token}'}


def issue_qr(client: TestClient, exhibit_code: str = 'submersible', exhibit_title: str = '잠수정 전시') -> dict:
    login = client.post('/api/v1/auth/login', json={
        'email': 'admin@bluepath.example.com',
        'password': 'AdminPassword123!',
        'guardianEmail': None,
    })
    assert login.status_code == 200, login.text
    response = client.post('/api/v1/admin/missions/qr-token', headers=auth_header(login.json()['accessToken']), json={
        'exhibitCode': exhibit_code,
        'exhibitTitle': exhibit_title,
        'validMinutes': 10,
    })
    assert response.status_code == 200, response.text
    payload = response.json()
    payload.pop('qrJson', None)
    return payload


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
        assert all(item['answerIndex'] == -1 for item in questions)
        assert all(not item['explanation'] for item in questions)
        with SessionLocal() as db:
            quiz_session = db.get(QuizSession, quiz.json()['sessionId'])
            answers = [item['answerIndex'] for item in quiz_session.questions_json]
        submitted_quiz = client.post('/api/v1/ai/quiz/submit', headers=auth_header(token), json={
            'sessionId': quiz.json()['sessionId'], 'answers': answers,
        })
        assert submitted_quiz.status_code == 200, submitted_quiz.text
        assert submitted_quiz.json()['passed'] is True
        assert submitted_quiz.json()['xpAwarded'] > 0
        assert all(0 <= item['answerIndex'] <= 3 for item in submitted_quiz.json()['questions'])

        search = client.post('/api/v1/ai/search', headers=auth_header(token), json={
            'query': '해양환경 입문 영상',
            'resourceType': 'video',
            'limit': 5,
        })
        assert search.status_code == 200, search.text
        assert isinstance(search.json()['items'], list)
        assert search.json()['summary']
        unrelated = client.post('/api/v1/ai/search', headers=auth_header(token), json={
            'query': 'zzqv unrelated resource token 987654321', 'resourceType': 'paper', 'limit': 5,
        })
        assert unrelated.status_code == 200, unrelated.text
        assert unrelated.json()['items'] == []

        route = client.post('/api/v1/routes/plan', headers=auth_header(token), json={
            'targetCareer': '해양환경 교육 기획자',
            'routeType': 'family',
            'profile': {
                'ageGroup': '초등학생',
                'interest': '해양생물',
                'level': '입문',
                'tier': '브론즈',
                'skillMastery': {'해양생물': 58, '해양환경': 51},
                'completedContentIds': [],
            },
            'constraints': {'family': True},
            'maxNodes': 5,
        })
        assert route.status_code == 200, route.text
        route_data = route.json()
        assert route_data['targetCareer'] == '해양환경 교육 기획자'
        assert 3 <= len(route_data['nodes']) <= 5
        assert route_data['readinessAfter'] > route_data['readinessBefore']
        assert all(item['whyThisOrder'] for item in route_data['nodes'])
        assert all(item['evidenceBasis'] for item in route_data['nodes'])

        first_node = route_data['nodes'][0]
        simulation = client.post('/api/v1/routes/simulate', headers=auth_header(token), json={
            'routeId': route_data['routeId'],
            'nodeId': first_node['id'],
            'activityTitle': first_node['title'],
            'skillTopic': first_node['topic'],
            'expectedSkillGain': first_node['expectedSkillGain'],
            'readinessGain': first_node['readinessGain'],
            'profile': {'skillMastery': {first_node['topic']: 50}},
        })
        assert simulation.status_code == 200, simulation.text
        assert simulation.json()['masteryAfter'] > simulation.json()['masteryBefore']
        assert simulation.json()['nextRecommendation']

        started = client.post('/api/v1/routes/outcomes', headers=auth_header(token), json={
            'routeId': route_data['routeId'],
            'nodeId': first_node['id'],
            'eventType': 'started',
            'value': 1,
            'metadata': {'client': 'test'},
        })
        assert started.status_code == 200, started.text

        rerouted = client.post('/api/v1/routes/reroute', headers=auth_header(token), json={
            'routeId': route_data['routeId'],
            'blockedNodeId': first_node['id'],
            'reason': 'time_shortage',
            'profile': {'interest': '해양생물'},
            'constraints': {'maxMinutes': 30},
        })
        assert rerouted.status_code == 200, rerouted.text
        assert rerouted.json()['routeId'] != route_data['routeId']
        assert rerouted.json()['routeType'] == 'fastest'
        assert rerouted.json()['nodes'][0]['targetId'] != first_node['targetId']

        qr_payload = issue_qr(client)
        mission = client.post('/api/v1/missions/generate', headers=auth_header(token), json={
            'exhibitCode': 'submersible',
            'exhibitTitle': '잠수정 전시',
            'participantCount': 3,
            'qrPayload': qr_payload,
            'profile': {'ageGroup': '초등학생', 'interest': '해양생물', 'level': '입문'},
        })
        assert mission.status_code == 200, mission.text
        mission_data = mission.json()
        assert len(mission_data['roles']) >= 2
        assert mission_data['expectedSkillGains']
        verified = client.post('/api/v1/missions/verify', headers=auth_header(token), json={
            'missionId': mission_data['missionId'],
            'completionNote': '가족이 안전 장치와 압력 단서를 찾아 잠수정을 설계했다.',
            'participantCount': 3,
            'qrPayload': qr_payload,
        })
        assert verified.status_code == 200, verified.text
        assert verified.json()['verified'] is True
        assert verified.json()['newlyVerified'] is True
        assert verified.json()['badge'] == mission_data['badge']

        duplicate = client.post('/api/v1/missions/verify', headers=auth_header(token), json={
            'missionId': mission_data['missionId'],
            'completionNote': '가족이 안전 장치와 압력 단서를 찾아 잠수정을 설계했다.',
            'participantCount': 3,
            'qrPayload': qr_payload,
        })
        assert duplicate.status_code == 200, duplicate.text
        assert duplicate.json()['verified'] is True
        assert duplicate.json()['newlyVerified'] is False

        blank_note = client.post('/api/v1/missions/verify', headers=auth_header(token), json={
            'missionId': mission_data['missionId'],
            'completionNote': '   ',
            'participantCount': 3,
            'qrPayload': qr_payload,
        })
        assert blank_note.status_code == 422

        record_id = '123e4567-e89b-42d3-a456-426614174000'
        sync = client.post('/api/v1/sync', headers=auth_header(token), json={
            'snapshot': {
                'tier': '플래티넘',
                'xp': 999999,
                'diamondAdvancedQuizPassed': True,
                'guardianConsent': True,
            },
            'learningRecords': [{
                'id': record_id,
                'recordType': 'quiz',
                'targetId': '브론즈',
                'title': 'Server verified promotion quiz',
                'status': 'passed',
                'updatedAt': 1,
                'synced': False,
            }],
            'baseVersion': 0,
            'deviceId': 'test-device-a',
        })
        assert sync.status_code == 200, sync.text
        assert sync.json()['snapshot']['tier'] == '실버'
        assert sync.json()['snapshot']['xp'] < 999999
        assert sync.json()['diamondStatus']['advancedQuizPassed'] is False
        assert record_id in sync.json()['acceptedRecordIds']
        cloud = client.get('/api/v1/sync', headers=auth_header(token))
        assert cloud.status_code == 200
        assert cloud.json()['snapshot']['tier'] == '실버'
        assert cloud.json()['learningRecords'][0]['id'] == record_id
        assert cloud.json()['learningRecords'][0]['status'] == 'passed'

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



def test_route_ownership_pending_reroute_and_operational_outcomes() -> None:
    with TestClient(app) as client:
        def register(email: str, nickname: str) -> str:
            response = client.post('/api/v1/auth/register', json={
                'email': email,
                'password': 'LearnerPassword123!',
                'guardianEmail': None,
                'nickname': nickname,
            })
            assert response.status_code == 200, response.text
            return response.json()['accessToken']

        owner_token = register('route-owner@bluepath.example.com', 'RouteOwner')
        other_token = register('route-other@bluepath.example.com', 'RouteOther')
        route = client.post('/api/v1/routes/plan', headers=auth_header(owner_token), json={
            'targetCareer': '항해사',
            'routeType': 'balanced',
            'profile': {'interest': '항해', 'skillMastery': {'항해': 40}},
            'constraints': {},
            'maxNodes': 4,
        })
        assert route.status_code == 200, route.text
        route_data = route.json()
        node = route_data['nodes'][0]

        foreign_route = client.post('/api/v1/routes/simulate', headers=auth_header(other_token), json={
            'routeId': route_data['routeId'],
            'activityTitle': 'foreign route',
            'skillTopic': '항해',
            'profile': {},
        })
        assert foreign_route.status_code == 404
        foreign_node = client.post('/api/v1/routes/simulate', headers=auth_header(other_token), json={
            'nodeId': node['id'],
            'activityTitle': node['title'],
            'skillTopic': node['topic'],
            'profile': {},
        })
        assert foreign_node.status_code == 404

        preview = client.post('/api/v1/routes/reroute/preview', headers=auth_header(owner_token), json={
            'routeId': route_data['routeId'],
            'reason': 'inactivity',
            'profile': {},
            'constraints': {},
        })
        assert preview.status_code == 200, preview.text
        pending = preview.json()
        assert pending['routeId'] != route_data['routeId']
        other_activation = client.post('/api/v1/routes/activate', headers=auth_header(other_token), json={
            'routeId': pending['routeId'],
        })
        assert other_activation.status_code == 404
        activation = client.post('/api/v1/routes/activate', headers=auth_header(owner_token), json={
            'routeId': pending['routeId'],
        })
        assert activation.status_code == 200, activation.text
        assert activation.json()['routeId'] == pending['routeId']

        for status, pre, post in [('enrolled', 42, None), ('attended', 42, None), ('completed', 42, 67)]:
            participation = client.post('/api/v1/program-participation', headers=auth_header(owner_token), json={
                'programId': 'museum-navigation-101',
                'programTitle': '박물관 항해 입문',
                'status': status,
                'preAssessment': pre,
                'postAssessment': post,
                'metadata': {'source': 'test'},
            })
            assert participation.status_code == 200, participation.text

        admin_login = client.post('/api/v1/auth/login', json={
            'email': 'admin@bluepath.example.com',
            'password': 'AdminPassword123!',
            'guardianEmail': None,
        })
        analytics = client.get('/api/v1/admin/analytics/outcomes', headers=auth_header(admin_login.json()['accessToken']))
        assert analytics.status_code == 200, analytics.text
        body = analytics.json()
        assert body['metricScope'] == 'operational_enrollment_attendance_assessment'
        outcome = next(item for item in body['programOutcomes'] if item['programId'] == 'museum-navigation-101')
        assert outcome['participants'] == 1
        assert outcome['attendanceRate'] == 100
        assert outcome['completionRate'] == 100
        assert outcome['averageSkillGain'] == 25.0


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

        outcomes = client.get('/api/v1/admin/analytics/outcomes', headers=headers)
        assert outcomes.status_code == 200, outcomes.text
        assert 'programOutcomes' in outcomes.json()
        assert 'aiSuggestions' in outcomes.json()

        draft = client.post('/api/v1/admin/program-draft', headers=headers, json={
            'topic': '해양안전',
            'targetAudience': '초등학생과 보호자',
            'durationMinutes': 60,
            'institutionContext': '국립해양박물관 잠수정 전시 연계',
            'objective': '가족 체험을 지속 학습과 진로 탐색으로 연결',
        })
        assert draft.status_code == 200, draft.text
        draft_data = draft.json()
        assert draft_data['title']
        assert draft_data['agenda30'] and draft_data['agenda60'] and draft_data['agenda90']
        assert draft_data['ncsCompetencies']
        assert draft_data['measurementPlan']

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
            'applicationUrl': 'https://example.com/program/apply',
            'applicationDeadline': '2026-08-01T18:00:00+09:00',
            'capacity': 24,
            'waitlistAvailable': True,
            'timezone': 'Asia/Seoul',
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
        assert catalog_item['applicationUrl'].endswith('/apply')
        assert catalog_item['applicationDeadline'].startswith('2026-08-01')
        assert catalog_item['capacity'] == 24
        assert catalog_item['waitlistAvailable'] is True
        assert catalog_item['timezone'] == 'Asia/Seoul'

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

        searched = client.get('/api/v1/community/posts?category=question&q=BlueWhale&limit=1&offset=0', headers=second_headers)
        assert searched.status_code == 200, searched.text
        assert searched.json()[0]['id'] == post_id

        updated = client.put(f'/api/v1/community/posts/{post_id}', headers=first_headers, json={
            'title': '수정된 해양 진로 질문',
            'body': '항만 물류와 해양 데이터 진로를 함께 준비하려면 무엇부터 시작해야 하나요?',
        })
        assert updated.status_code == 200, updated.text
        assert updated.json()['canEdit'] is True

        updated_comment = client.put(f"/api/v1/community/comments/{comment.json()['id']}", headers=second_headers, json={
            'body': '기초 물류 영상, 현장 교육, 데이터 기초부터 추천합니다.',
        })
        assert updated_comment.status_code == 200, updated_comment.text

        reported = client.post('/api/v1/community/reports', headers=second_headers, json={
            'targetType': 'post', 'targetId': post_id, 'reason': '관리자 검토 테스트',
        })
        assert reported.status_code == 200, reported.text

        blocked = client.post(f'/api/v1/community/users/{author_id}/block', headers=second_headers)
        assert blocked.status_code == 200 and blocked.json()['blocked'] is True
        hidden = client.get('/api/v1/community/posts?category=question', headers=second_headers)
        assert hidden.status_code == 200
        assert all(value['id'] != post_id for value in hidden.json())
        unblocked = client.post(f'/api/v1/community/users/{author_id}/block', headers=second_headers)
        assert unblocked.status_code == 200 and unblocked.json()['blocked'] is False

        admin_login = client.post('/api/v1/auth/login', json={
            'email': 'admin@bluepath.example.com',
            'password': 'AdminPassword123!',
            'guardianEmail': None,
        })
        admin_headers = auth_header(admin_login.json()['accessToken'])
        reports = client.get('/api/v1/admin/community/reports', headers=admin_headers)
        assert reports.status_code == 200, reports.text
        report_id = next(value['id'] for value in reports.json() if value['targetId'] == post_id)
        reviewed = client.post(f'/api/v1/admin/community/reports/{report_id}/review', headers=admin_headers, json={
            'status': 'dismissed', 'action': 'none', 'reviewNote': '테스트 신고 검토 완료',
        })
        assert reviewed.status_code == 200, reviewed.text

        dashboard = client.get('/api/v1/dashboard', headers=first_headers)
        assert dashboard.status_code == 200, dashboard.text
        assert dashboard.json()['profile']['nickname'] == 'BlueWhale'
        assert dashboard.json()['profile']['followerCount'] == 0
        assert sum(dashboard.json()['activity'].values()) >= 2


def test_integrity_guards_for_sync_video_mission_and_community(monkeypatch) -> None:
    from backend.app.models import User, CommunityReport

    with TestClient(app) as client:
        def register(email: str, nickname: str) -> tuple[str, str]:
            response = client.post('/api/v1/auth/register', json={
                'email': email, 'password': 'LearnerPassword123!', 'guardianEmail': None, 'nickname': nickname,
            })
            assert response.status_code == 200, response.text
            with SessionLocal() as db:
                user = db.scalar(__import__('sqlalchemy').select(User).where(User.email == email))
                return response.json()['accessToken'], user.id

        token_a, user_a = register('integrity-a@bluepath.example.com', 'IntegrityA')
        token_b, user_b = register('integrity-b@bluepath.example.com', 'IntegrityB')

        first_uuid = '123e4567-e89b-42d3-a456-426614174101'
        second_uuid = '123e4567-e89b-42d3-a456-426614174102'
        first_sync = client.post('/api/v1/sync', headers=auth_header(token_a), json={
            'snapshot': {
                'bookmarks': ['a'], 'bookmarkState': {'a': {'active': True, 'updatedAt': 10}},
                'contentReflections': {'lesson': 'old reflection'},
                'contentReflectionUpdatedAt': {'lesson': 10},
                'ageGroup': '성인', 'interest': '항해', 'profileUpdatedAt': 10,
                'xp': 999999,
            },
            'learningRecords': [{'id': first_uuid, 'recordType': 'video', 'targetId': 'a', 'title': 'a', 'status': 'done', 'updatedAt': 10}],
            'baseVersion': 0, 'deviceId': 'device-a-1234',
        })
        assert first_sync.status_code == 200, first_sync.text
        stale_sync = client.post('/api/v1/sync', headers=auth_header(token_a), json={
            'snapshot': {
                'bookmarks': ['b'],
                'bookmarkState': {
                    'a': {'active': False, 'updatedAt': 20},
                    'b': {'active': True, 'updatedAt': 20},
                },
                'contentReflections': {'lesson': 'new reflection'},
                'contentReflectionUpdatedAt': {'lesson': 20},
                'ageGroup': '성인', 'interest': '해양생물', 'profileUpdatedAt': 20,
                'xp': 888888,
            },
            'learningRecords': [{'id': second_uuid, 'recordType': 'video', 'targetId': 'b', 'title': 'b', 'status': 'done', 'updatedAt': 11}],
            'baseVersion': 0, 'deviceId': 'device-b-1234',
        })
        assert stale_sync.status_code == 200, stale_sync.text
        assert stale_sync.json()['conflictResolved'] is True
        assert set(stale_sync.json()['acceptedRecordIds']) == {second_uuid}
        assert stale_sync.json()['snapshot']['xp'] < 888888
        assert set(stale_sync.json()['snapshot']['bookmarks']) == {'b'}
        assert stale_sync.json()['snapshot']['bookmarkState']['a']['active'] is False
        assert stale_sync.json()['snapshot']['contentReflections']['lesson'] == 'new reflection'
        assert stale_sync.json()['snapshot']['interest'] == '해양생물'
        cloud_ids = {item['id'] for item in stale_sync.json()['learningRecords']}
        assert {first_uuid, second_uuid}.issubset(cloud_ids)

        catalog = client.get('/api/v1/catalog', headers=auth_header(token_a)).json()
        video = next(item for item in catalog if item['contentType'] == 'video')
        seek_only = client.post('/api/v1/learning/video/verify', headers=auth_header(token_a), json={
            'contentId': video['id'], 'durationSeconds': 100,
            'intervals': [{'start': 70, 'end': 72}, {'start': 90, 'end': 92}],
        })
        assert seek_only.status_code == 200
        assert seek_only.json()['verified'] is False
        full_coverage = client.post('/api/v1/learning/video/verify', headers=auth_header(token_a), json={
            'contentId': video['id'], 'durationSeconds': 100,
            'intervals': [{'start': value, 'end': value + 10} for value in range(0, 70, 10)],
        })
        assert full_coverage.status_code == 200, full_coverage.text
        assert full_coverage.json()['verified'] is True

        qr_payload = issue_qr(client, 'six-person-exhibit', '6인 협동 전시')
        mission = client.post('/api/v1/missions/generate', headers=auth_header(token_a), json={
            'exhibitCode': 'six-person-exhibit', 'exhibitTitle': '6인 협동 전시', 'participantCount': 6,
            'qrPayload': qr_payload, 'profile': {'ageGroup': '가족', 'interest': '해양안전'},
        })
        assert mission.status_code == 200, mission.text
        assert len(mission.json()['roles']) == 6
        mismatch = client.post('/api/v1/missions/verify', headers=auth_header(token_a), json={
            'missionId': mission.json()['missionId'], 'completionNote': '여섯 명이 각 역할을 수행했다.',
            'participantCount': 1, 'qrPayload': qr_payload,
        })
        assert mismatch.status_code in {400, 422}

        post = client.post('/api/v1/community/posts', headers=auth_header(token_a), json={
            'category': 'free', 'title': 'block test', 'body': 'server enforced block test',
        })
        assert post.status_code == 200
        blocked = client.post(f'/api/v1/community/users/{user_b}/block', headers=auth_header(token_a))
        assert blocked.status_code == 200 and blocked.json()['blocked'] is True
        assert client.post(f"/api/v1/community/posts/{post.json()['id']}/comments", headers=auth_header(token_b), json={'body': 'blocked'}).status_code == 403
        assert client.post('/api/v1/community/reactions', headers=auth_header(token_b), json={'targetType': 'post', 'targetId': post.json()['id'], 'emoji': '👍'}).status_code == 403
        assert client.post(f'/api/v1/community/users/{user_a}/follow', headers=auth_header(token_b)).status_code == 403

        report = client.post('/api/v1/community/reports', headers=auth_header(token_b), json={
            'targetType': 'post', 'targetId': post.json()['id'], 'reason': 'moderation test',
        })
        assert report.status_code == 200
        with SessionLocal() as db:
            report_id = db.scalar(__import__('sqlalchemy').select(CommunityReport.id).where(CommunityReport.target_id == post.json()['id']))
        admin_login = client.post('/api/v1/auth/login', json={
            'email': 'admin@bluepath.example.com', 'password': 'AdminPassword123!', 'guardianEmail': None,
        })
        admin_headers = auth_header(admin_login.json()['accessToken'])
        reviewed = client.post(f'/api/v1/admin/community/reports/{report_id}/review', headers=admin_headers, json={
            'status': 'resolved', 'action': 'delete', 'reviewNote': 'confirmed by integrity test',
        })
        assert reviewed.status_code == 200, reviewed.text
        assert client.delete(f"/api/v1/community/posts/{post.json()['id']}", headers=auth_header(token_a)).status_code == 404

        with SessionLocal() as db:
            admin_user = db.scalar(__import__('sqlalchemy').select(User).where(User.email == 'admin@bluepath.example.com'))
        admin_report = client.post('/api/v1/community/reports', headers=auth_header(token_b), json={
            'targetType': 'user', 'targetId': admin_user.id, 'reason': 'admin protection test',
        })
        assert admin_report.status_code == 200
        with SessionLocal() as db:
            admin_report_id = db.scalar(__import__('sqlalchemy').select(CommunityReport.id).where(
                CommunityReport.target_type == 'user', CommunityReport.target_id == admin_user.id,
            ))
        protected = client.post(f'/api/v1/admin/community/reports/{admin_report_id}/review', headers=admin_headers, json={
            'status': 'resolved', 'action': 'deactivate', 'reviewNote': 'must be rejected',
        })
        assert protected.status_code == 400


def test_guardian_paper_and_portfolio_credentials(monkeypatch) -> None:
    import backend.app.main as main_module

    captured: dict[str, str] = {}
    monkeypatch.setattr(main_module, 'send_guardian_consent_email',
                        lambda recipient, token, learner_name, consent_version: captured.update(token=token, recipient=recipient))
    with TestClient(app) as client:
        registered = client.post('/api/v1/auth/register', json={
            'email': 'credential-user@bluepath.example.com', 'password': 'LearnerPassword123!',
            'guardianEmail': None, 'nickname': 'CredentialUser',
        })
        token = registered.json()['accessToken']
        headers = auth_header(token)
        minor_sync = client.post('/api/v1/sync', headers=headers, json={
            'snapshot': {'ageGroup': '중학생', 'bookmarks': []}, 'learningRecords': [],
            'baseVersion': 0, 'deviceId': 'guardian-device-1234',
        })
        assert minor_sync.status_code == 200, minor_sync.text
        blocked_credential = client.post('/api/v1/portfolio/credentials', headers=headers, json={'title': 'Blocked Minor Portfolio'})
        assert blocked_credential.status_code == 403
        requested = client.post('/api/v1/guardian-consent/request', headers=headers, json={
            'guardianEmail': 'guardian@example.com', 'consentVersion': '2026-07',
        })
        assert requested.status_code == 200 and requested.json()['status'] == 'pending'
        assert captured['recipient'] == 'guardian@example.com'
        assert client.get('/api/v1/guardian-consent/status', headers=headers).json()['status'] == 'pending'
        consent_details = client.get('/api/v1/guardian-consent/details', params={'token': captured['token']})
        assert consent_details.status_code == 200
        assert consent_details.json()['status'] == 'pending'
        assert consent_details.json()['learnerName'] == 'CredentialUser'
        assert consent_details.json()['guardianEmailMasked'] == 'g***@example.com'
        assert consent_details.json()['consentVersion'] == '2026-07'
        assert len(consent_details.json()['terms']) >= 4
        confirmed = client.post('/api/v1/guardian-consent/confirm', json={'token': captured['token']})
        assert confirmed.status_code == 200 and confirmed.json()['status'] == 'confirmed'
        assert client.get('/api/v1/guardian-consent/status', headers=headers).json()['consentVersion'] == '2026-07'
        assert client.get('/api/v1/guardian-consent/details', params={'token': captured['token']}).json()['status'] == 'confirmed'

        admin_login = client.post('/api/v1/auth/login', json={
            'email': 'admin@bluepath.example.com', 'password': 'AdminPassword123!', 'guardianEmail': None,
        })
        admin_headers = auth_header(admin_login.json()['accessToken'])
        current_paper = {
            'id': 'paper-integrity-current', 'title': 'Current marine paper', 'contentType': 'paper',
            'source': 'Integrity Journal', 'url': 'https://example.com/current-paper', 'topic': '해양환경',
            'description': 'A current peer reviewed paper.', 'authors': 'A Researcher', 'year': '2026',
            'doi': '10.9999/bluepath.current', 'paperStatus': 'current', 'versionNote': '',
        }
        retracted_paper = {**current_paper, 'id': 'paper-integrity-retracted', 'title': 'Retracted marine paper',
                           'url': 'https://example.com/retracted-paper', 'doi': '10.9999/bluepath.retracted',
                           'paperStatus': 'retracted', 'versionNote': 'Retracted after data review'}
        assert client.post('/api/v1/admin/content', headers=admin_headers, json=current_paper).status_code == 200
        assert client.post('/api/v1/admin/content', headers=admin_headers, json=retracted_paper).status_code == 200
        duplicate_doi = {**current_paper, 'id': 'paper-integrity-duplicate', 'title': 'Duplicate DOI'}
        assert client.post('/api/v1/admin/content', headers=admin_headers, json=duplicate_doi).status_code == 409

        reflection = '이 논문은 해양환경 변화의 원인과 관측 근거를 비교하며 한계와 후속 연구 필요성을 함께 제시한다.'
        paper_done = client.post('/api/v1/learning/paper/complete', headers=headers, json={
            'contentId': current_paper['id'], 'reflection': reflection,
        })
        assert paper_done.status_code == 200, paper_done.text
        assert paper_done.json()['verified'] is True and paper_done.json()['xpAwarded'] > 0
        retracted = client.post('/api/v1/learning/paper/complete', headers=headers, json={
            'contentId': retracted_paper['id'], 'reflection': reflection,
        })
        assert retracted.status_code == 409

        issued = client.post('/api/v1/portfolio/credentials', headers=headers, json={'title': 'Integrity Portfolio'})
        assert issued.status_code == 200, issued.text
        credential = issued.json()
        assert credential['signature'] and credential['verifyUrl']
        assert credential['payload']['verifiedPapers'] == 1
        verified = client.get(f"/api/v1/portfolio/credentials/{credential['credentialId']}")
        assert verified.status_code == 200 and verified.json()['revoked'] is False
        assert verified.json()['valid'] is True
        assert verified.json()['signature'] == credential['signature']

        retracted_update = {**current_paper, 'paperStatus': 'retracted', 'versionNote': 'Retracted after integrity review'}
        assert client.post('/api/v1/admin/content', headers=admin_headers, json=retracted_update).status_code == 200
        invalidated = client.get(f"/api/v1/portfolio/credentials/{credential['credentialId']}")
        assert invalidated.status_code == 200 and invalidated.json()['valid'] is False
        cloud_after_retraction = client.get('/api/v1/sync', headers=headers)
        assert current_paper['id'] not in cloud_after_retraction.json()['snapshot']['verifiedPaperIds']
        reissued = client.post('/api/v1/portfolio/credentials', headers=headers, json={'title': 'Post Retraction Portfolio'})
        assert reissued.status_code == 200 and reissued.json()['payload']['verifiedPapers'] == 0

        assert client.delete(f"/api/v1/portfolio/credentials/{credential['credentialId']}", headers=headers).status_code == 200
        revoked_credential = client.get(f"/api/v1/portfolio/credentials/{credential['credentialId']}").json()
        assert revoked_credential['revoked'] is True and revoked_credential['valid'] is False
        revoked = client.delete('/api/v1/guardian-consent', headers=headers)
        assert revoked.status_code == 200 and revoked.json()['status'] == 'revoked'
        assert client.post('/api/v1/portfolio/credentials', headers=headers, json={'title': 'Revoked Minor Portfolio'}).status_code == 403


def test_official_schedule_feed_sync_replaces_removed_items(monkeypatch) -> None:
    import backend.app.services as services
    from backend.app.models import Content
    from sqlalchemy import select

    class FakeResponse:
        def __init__(self, rows):
            self._rows = rows
            self.url = 'https://official.example.com/schedules.json'

        def raise_for_status(self) -> None:
            return None

        def json(self):
            return {'items': self._rows}

    rows = [
        {
            'id': 'official-program-1', 'contentType': 'program', 'title': 'Official Ocean Workshop',
            'startAt': '2026-09-01T10:00:00+09:00', 'endAt': '2026-09-01T12:00:00+09:00',
            'applicationUrl': 'https://official.example.com/apply/1', 'applicationDeadline': '2026-08-25T18:00:00+09:00',
            'capacity': 30, 'waitlistAvailable': True, 'timezone': 'Asia/Seoul', 'source': 'Official Institute',
        },
        {
            'id': 'official-event-2', 'contentType': 'event', 'title': 'Official Marine Festival',
            'startAt': '2026-10-03', 'endAt': '2026-10-04', 'url': 'https://official.example.com/events/2',
        },
    ]
    current = {'rows': rows}
    monkeypatch.setattr(services, 'is_safe_public_url', lambda value: True)
    monkeypatch.setattr(services.httpx, 'get', lambda *args, **kwargs: FakeResponse(current['rows']))

    with SessionLocal() as db:
        first = services.sync_schedule_feeds(db, ['https://official.example.com/schedules.json'])
        assert first['imported'] == 2 and first['removed'] == 0 and not first['errors']
        imported = list(db.scalars(select(Content).where(Content.id.like('schedule-feed-%'))))
        assert len(imported) == 2
        program = next(item for item in imported if item.content_type == 'program')
        assert program.metadata_json['applicationUrl'].endswith('/apply/1')
        assert program.metadata_json['capacity'] == 30
        assert program.metadata_json['waitlistAvailable'] is True

        current['rows'] = rows[:1]
        second = services.sync_schedule_feeds(db, ['https://official.example.com/schedules.json'])
        assert second['imported'] == 1 and second['removed'] == 1
        remaining = list(db.scalars(select(Content).where(Content.id.like('schedule-feed-%'))))
        assert len(remaining) == 1 and remaining[0].title == 'Official Ocean Workshop'
