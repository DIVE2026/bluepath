from __future__ import annotations

import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
TEST_DB = Path('/tmp/bluepath_test_follow_graph.db')
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


def auth_header(token: str) -> dict[str, str]:
    return {'Authorization': f'Bearer {token}'}


def register(client: TestClient, nickname: str) -> tuple[dict[str, str], str]:
    response = client.post('/api/v1/auth/register', json={
        'email': f'{nickname.lower()}@follow.example.com',
        'password': 'FollowPassword123!',
        'guardianEmail': None,
        'nickname': nickname,
    })
    assert response.status_code == 200, response.text
    body = response.json()
    assert body['userId'], 'auth response must expose the signed-in user id'
    return auth_header(body['accessToken']), body['userId']


def write_post(client: TestClient, headers: dict[str, str], title: str) -> str:
    response = client.post('/api/v1/community/posts', headers=headers, json={
        'category': 'free',
        'title': title,
        'body': f'{title} body text',
        'tags': [],
    })
    assert response.status_code == 200, response.text
    return response.json()['id']


def test_public_profile_reports_counts_and_viewer_follow_state() -> None:
    with TestClient(app) as client:
        author_headers, author_id = register(client, 'ProfileAuthor')
        viewer_headers, _ = register(client, 'ProfileViewer')
        write_post(client, author_headers, '항해사 준비 후기')

        seen = client.get(f'/api/v1/community/users/{author_id}', headers=viewer_headers)
        assert seen.status_code == 200, seen.text
        assert seen.json()['postCount'] == 1
        assert seen.json()['isMe'] is False
        assert seen.json()['isBlocked'] is False
        assert seen.json()['profile']['isFollowing'] is False

        client.post(f'/api/v1/community/users/{author_id}/follow', headers=viewer_headers)
        followed = client.get(f'/api/v1/community/users/{author_id}', headers=viewer_headers)
        assert followed.json()['profile']['isFollowing'] is True
        assert followed.json()['profile']['followerCount'] == 1

        own = client.get(f'/api/v1/community/users/{author_id}', headers=author_headers)
        assert own.json()['isMe'] is True

        missing = client.get('/api/v1/community/users/does-not-exist', headers=viewer_headers)
        assert missing.status_code == 404


def test_follower_and_following_lists_page_both_directions() -> None:
    with TestClient(app) as client:
        star_headers, star_id = register(client, 'ListStar')
        fan_one_headers, fan_one_id = register(client, 'ListFanOne')
        fan_two_headers, fan_two_id = register(client, 'ListFanTwo')

        for headers in (fan_one_headers, fan_two_headers):
            assert client.post(f'/api/v1/community/users/{star_id}/follow', headers=headers).status_code == 200

        followers = client.get(f'/api/v1/community/users/{star_id}/followers', headers=star_headers)
        assert followers.status_code == 200, followers.text
        assert followers.json()['total'] == 2
        assert followers.json()['hasMore'] is False
        assert {item['userId'] for item in followers.json()['users']} == {fan_one_id, fan_two_id}

        following = client.get(f'/api/v1/community/users/{fan_one_id}/following', headers=fan_one_headers)
        assert following.json()['total'] == 1
        assert following.json()['users'][0]['userId'] == star_id
        assert following.json()['users'][0]['isFollowing'] is True

        first_page = client.get(
            f'/api/v1/community/users/{star_id}/followers?limit=1&offset=0', headers=star_headers)
        assert len(first_page.json()['users']) == 1
        assert first_page.json()['total'] == 2
        assert first_page.json()['hasMore'] is True

        # 팔로우를 해제하면 목록에서도 즉시 빠져야 합니다.
        client.post(f'/api/v1/community/users/{star_id}/follow', headers=fan_two_headers)
        assert client.get(
            f'/api/v1/community/users/{star_id}/followers', headers=star_headers).json()['total'] == 1


def test_follow_lists_hide_blocked_accounts() -> None:
    with TestClient(app) as client:
        star_headers, star_id = register(client, 'BlockStar')
        rude_headers, rude_id = register(client, 'BlockRude')

        assert client.post(f'/api/v1/community/users/{star_id}/follow', headers=rude_headers).status_code == 200
        assert client.get(
            f'/api/v1/community/users/{star_id}/followers', headers=star_headers).json()['total'] == 1

        assert client.post(f'/api/v1/community/users/{rude_id}/block', headers=star_headers).status_code == 200
        hidden = client.get(f'/api/v1/community/users/{star_id}/followers', headers=star_headers)
        assert hidden.json()['total'] == 0

        profile = client.get(f'/api/v1/community/users/{rude_id}', headers=star_headers)
        assert profile.status_code == 200
        assert profile.json()['isBlocked'] is True


def test_feed_scope_and_author_filters() -> None:
    with TestClient(app) as client:
        followed_headers, followed_id = register(client, 'FeedFollowed')
        stranger_headers, stranger_id = register(client, 'FeedStranger')
        reader_headers, _ = register(client, 'FeedReader')

        write_post(client, followed_headers, '팔로우한 사람의 글')
        write_post(client, stranger_headers, '모르는 사람의 글')

        everything = client.get('/api/v1/community/posts?category=free', headers=reader_headers)
        assert everything.status_code == 200, everything.text
        assert {item['title'] for item in everything.json()} >= {'팔로우한 사람의 글', '모르는 사람의 글'}

        empty_scope = client.get(
            '/api/v1/community/posts?category=free&scope=following', headers=reader_headers)
        assert empty_scope.json() == []

        client.post(f'/api/v1/community/users/{followed_id}/follow', headers=reader_headers)
        scoped = client.get(
            '/api/v1/community/posts?category=free&scope=following', headers=reader_headers)
        assert [item['title'] for item in scoped.json()] == ['팔로우한 사람의 글']

        meta = client.get(
            '/api/v1/community/feed-meta?category=free&scope=following', headers=reader_headers)
        assert meta.status_code == 200, meta.text
        assert meta.json()['postCount'] == 1

        by_author = client.get(
            f'/api/v1/community/posts?category=all&authorId={stranger_id}', headers=reader_headers)
        assert [item['title'] for item in by_author.json()] == ['모르는 사람의 글']
