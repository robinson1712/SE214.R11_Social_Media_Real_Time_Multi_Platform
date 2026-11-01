"""Connected screens regressions, invoked by test_backend_integration.py.

Uses actual forms, HTTP fixture mutations, and the existing CDP transport.
No React internals or browser session/storage seeding.
"""
import asyncio
import base64
import json
import os
from pathlib import Path
import tempfile
import urllib.request

from test_next import BASE_URL, check, click, escape, input_text, poll, submit

FIXTURE_URL = os.environ.get("FIXTURE_URL", "http://127.0.0.1:3399").rstrip("/")


def control(path, body=None):
    request = urllib.request.Request(
        FIXTURE_URL + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=5) as response:
        return json.load(response)


async def server_check(predicate, label, timeout=8):
    deadline = asyncio.get_running_loop().time() + timeout
    while True:
        state = control("/__test/state")
        if predicate(state):
            print("  PASS " + label, flush=True)
            return state
        if asyncio.get_running_loop().time() >= deadline:
            raise AssertionError(label + ": fixture state did not match")
        await asyncio.sleep(.1)


async def navigate(browser, route, selector):
    previous_document = await browser.evaluate("performance.timeOrigin")
    await browser.send("Page.navigate", {"url": BASE_URL + route})
    await poll(browser, f"performance.timeOrigin !== {json.dumps(previous_document)} && location.pathname === {json.dumps(route)} && document.querySelector({json.dumps(selector)}) !== null", "open " + route, timeout=30)


async def run(browser):
    await navigate(browser, "/login", "#login-email")
    await click(browser, '[aria-label="Hiện mật khẩu"]')
    await check(browser, 'document.querySelector("#login-password").type === "text"', "login controls hydrated")
    await input_text(browser, "#login-email", "son@example.test")
    await input_text(browser, "#login-password", "Passw0rd!test")
    await submit(browser, ".login-form")
    await poll(browser, "location.pathname === '/' && document.querySelector('.nav-item')", "real account login", timeout=30)

    await navigate(browser, "/settings", '[data-form="settings"]')
    previous_document = await browser.evaluate("performance.timeOrigin")
    await browser.send("Page.reload")
    await check(browser, f"performance.timeOrigin !== {json.dumps(previous_document)} && " + "document.querySelector('[name=\"name\"]')?.value === 'Thiên Sơn' && document.querySelector('[name=\"bio\"]')?.value === 'Hồ sơ từ backend' && document.querySelector('[name=\"location\"]')?.value === 'TP. Hồ Chí Minh'", "direct settings reload hydrates existing profile fields", timeout=30)
    await input_text(browser, '[name="name"]', "Thiên Sơn đã cập nhật")
    control("/__test/fail-next", {"path": "/api/users/me", "method": "PUT", "message": "Profile save failed"})
    await submit(browser, '[data-form="settings"]')
    await check(browser, "document.querySelector('[data-form=\"settings\"] [role=\"alert\"]')?.textContent.includes('Profile save failed')", "profile API failure is visible")
    await check(browser, "document.querySelector('[name=\"name\"]').value === 'Thiên Sơn đã cập nhật'", "profile draft survives failed save")
    assert next(p for p in control("/__test/state")["profiles"] if p["id"] == "user-1")["fullName"] == "Thiên Sơn"
    await submit(browser, '[data-form="settings"]')
    await server_check(lambda state: next(p for p in state["profiles"] if p["id"] == "user-1")["fullName"] == "Thiên Sơn đã cập nhật", "profile saved to backend")
    await server_check(lambda state: next(p for p in state["profiles"] if p["id"] == "user-1")["bio"] == "Hồ sơ từ backend" and next(p for p in state["profiles"] if p["id"] == "user-1")["location"] == "TP. Hồ Chí Minh", "editing name preserves existing bio and location")

    await navigate(browser, "/profile/user-2", ".profile-heading")
    await check(browser, "document.querySelector('.profile-heading h2').textContent === 'Bạn Kiểm Thử'", "profile resolves backend ID")
    await check(browser, "Boolean(document.querySelector('.profile-heading a[href=\"/messages/user-2\"]'))", "profile message link uses user ID")
    await server_check(lambda state: any(r["path"] == "/api/posts/author/user-2" for r in state["requests"]), "deep profile loads author posts")

    # The Java message DTO carries shared posts as URL text, without sharedPost metadata.
    await navigate(browser, "/messages/user-2", '[data-form="message"]')
    await check(browser, "[...document.querySelectorAll('.message-bubble')].some(el => el.textContent.includes('Tin nhắn từ backend'))", "chat history loaded from backend")
    chat_texts = [BASE_URL + "/post/post-1", "javascript:alert('chat-link')", "https://example.test/post/post-1", '<img src=x onerror="alert(1)">']
    for text in chat_texts:
        await poll(browser, "!document.querySelector('[name=\"message\"]').disabled", "message sender ready")
        await input_text(browser, '[name="message"]', text)
        await submit(browser, '[data-form="message"]')
        await check(browser, "document.querySelector('[name=\"message\"]').value === ''", "message confirmed by STOMP echo")
        await server_check(lambda state, text=text: any(m["content"] == text for m in state["messages"]), "message text persisted by backend")
    await navigate(browser, "/messages/user-2", '[data-form="message"]')
    await check(browser, "Boolean(document.querySelector('.chat-history a[href=\"/post/post-1\"]'))", "shared post URL becomes clickable after history reload")
    await check(browser, "document.querySelectorAll('.chat-history a').length === 1 && !document.querySelector('.chat-history img') && " + json.dumps(chat_texts[1:]) + ".every(text => [...document.querySelectorAll('.message-bubble p')].some(el => el.textContent === text))", "external, javascript and HTML messages remain escaped plain text")
    await click(browser, '.chat-history a[href="/post/post-1"]')
    await check(browser, "location.pathname === '/post/post-1' && document.querySelector('#postpost-1 [data-post-text]')?.textContent.trim() === 'Bài viết từ backend'", "shared message opens loaded post detail", timeout=30)

    await navigate(browser, "/friends/requests", '[data-action="accept"][data-value="user-3"]')
    await click(browser, '[data-action="accept"][data-value="user-3"]')
    await server_check(lambda state: any(r["path"] == "/api/users/friend-requests/friendship-1/accept" and r["method"] == "PUT" and 200 <= r["status"] < 300 for r in state["requests"]), "accept request uses server friendship ID")

    await navigate(browser, "/explore/group-1", ".category-hero")
    await check(browser, "document.querySelector('.category-hero h2').textContent === 'Nhóm kiểm thử'", "group title comes from backend")
    await poll(browser, "document.querySelector('[data-action=\"new-topic\"]')?.disabled === false", "group history finished loading before opening topic form")
    await click(browser, '[data-action="new-topic"]')
    await input_text(browser, '[data-form="topic"] [name="title"]', "Thảo luận kiểm thử")
    await input_text(browser, '[data-form="topic"] [name="body"]', "Nội dung giữ lại khi lỗi")
    control("/__test/fail-next", {"path": "/api/posts", "method": "POST", "message": "Discussion save failed"})
    await submit(browser, '[data-form="topic"]')
    await check(browser, "document.querySelector('[data-form=\"topic\"] [role=\"alert\"]')?.textContent.includes('Discussion save failed')", "discussion API failure stays in form")
    await check(browser, "document.querySelector('[data-form=\"topic\"] [name=\"body\"]').value === 'Nội dung giữ lại khi lỗi'", "discussion body survives failure")
    await submit(browser, '[data-form="topic"]')
    await poll(browser, "location.pathname.startsWith('/topic/') && !document.querySelector('[data-form=\"topic\"]')", "discussion closes only after backend success")
    await server_check(lambda state: any(r["path"] == "/api/posts" and r["method"] == "POST" and 200 <= r["status"] < 300 and r["body"].get("groupId") == "group-1" and "Thảo luận kiểm thử" in r["body"].get("content", "") for r in state["requests"]), "discussion maps to real group post")

    await navigate(browser, "/topic/topic-1", '[data-form="reply"]')
    await input_text(browser, '[name="reply"]', "Phản hồi giữ khi lỗi")
    control("/__test/fail-next", {"path": "/api/comments", "method": "POST", "message": "Reply failed"})
    await submit(browser, '[data-form="reply"]')
    await check(browser, "document.querySelector('.discussion-detail [role=\"alert\"]')?.textContent.includes('Reply failed')", "reply error is visible")
    await check(browser, "document.querySelector('[name=\"reply\"]').value === 'Phản hồi giữ khi lỗi'", "reply draft survives failure")
    await submit(browser, '[data-form="reply"]')
    await check(browser, "document.querySelector('[name=\"reply\"]').value === ''", "reply clears after backend success")
    await server_check(lambda state: any(r["path"] == "/api/comments" and r["method"] == "POST" and 200 <= r["status"] < 300 and r["body"].get("targetId") == "topic-1" for r in state["requests"]), "topic reply uses post comment API")

    await navigate(browser, "/notifications", '[data-notification="notification-1"]')
    await click(browser, "[data-notifications-read-all]")
    await server_check(lambda state: any(r["method"] == "PUT" and r["path"] in ("/api/notifications/read-all", "/api/notifications/notification-1/read") and 200 <= r["status"] < 300 for r in state["requests"]), "notification read persists server ID")

    await navigate(browser, "/stories", '[data-story="story-1"]')
    await click(browser, '[data-story="story-1"]')
    await input_text(browser, '[aria-label="Reply to story"]', "Trả lời story thật")
    await server_check(lambda state: any(r["path"] == "/api/stories/story-1/view" and r["method"] == "POST" for r in state["requests"]), "story view calls backend")
    await click(browser, '[aria-label="Like Story"]')
    await server_check(lambda state: any(r["path"] == "/api/reactions" and r["method"] == "PUT" and r["body"].get("targetType") == "STORY" and r["body"].get("targetId") == "story-1" for r in state["requests"]), "story reaction uses server story ID")
    await click(browser, '[aria-label="Send story reply"]')
    await server_check(lambda state: any(r["method"] == "STOMP" and r["path"] == "/app/chat.send" and r["body"].get("storyReplyId") == "story-1" for r in state["requests"]), "story reply sends STOMP metadata")
    await check(browser, "document.querySelector('[aria-label=\"Reply to story\"]').value === ''", "story reply clears after sender echo")
    await escape(browser)

    # Native file input: upload a tiny real PNG, then assert media URL reaches story API.
    with tempfile.TemporaryDirectory(prefix="connected-story-") as folder:
        image = Path(folder) / "story.png"
        image.write_bytes(base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aNioAAAAASUVORK5CYII="))
        document = await browser.send("DOM.getDocument")
        selected = await browser.send("DOM.querySelector", {"nodeId": document["root"]["nodeId"], "selector": 'input[aria-label="Story image or video"]'})
        control("/__test/fail-next", {"path": "/api/stories", "method": "POST", "message": "Story save failed"})
        await browser.send("DOM.setFileInputFiles", {"nodeId": selected["nodeId"], "files": [str(image)]})
        await check(browser, "[...document.querySelectorAll('[role=alert]')].some(el => el.textContent.includes('Story save failed'))", "story upload error is visible")
        await check(browser, "document.querySelector('input[aria-label=\"Story image or video\"]').files.length === 1", "story file survives failed publish")
        await click(browser, '[data-action="retry-story"]')
        await server_check(lambda state: any(r["method"] == "POST" and r["path"] == "/api/stories" and 200 <= r["status"] < 300 and r["body"].get("mediaType") == "IMAGE" and not r["body"].get("mediaUrl", "").startswith("blob:") for r in state["requests"]), "story creation uses uploaded media URL")
