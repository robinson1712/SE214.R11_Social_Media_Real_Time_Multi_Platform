"""Real HTTP fixture regressions. Imported by the connected-browser runner."""
import asyncio
import json
import os
import urllib.request

from test_next import BASE_URL, click, input_text, poll, escape

FIXTURE = os.environ.get("FIXTURE_URL", "http://127.0.0.1:3399")


def control(path, body=None, token=None):
    request = urllib.request.Request(FIXTURE + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json", **({"Authorization": f"Bearer {token}"} if token else {})})
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.load(response)


def fail(path, method="POST"):
    control("/__test/fail-next", {"path": path, "method": method,
        "status": 500, "message": f"Fixture: {method} {path} failed"})


async def menu(browser, post_id, label):
    await click(browser, f'#post{post_id} .card-more-btn')
    await poll(browser, "document.querySelector('[role=menuitem]')", "post menu opens")
    await browser.evaluate(f"[...document.querySelectorAll('[role=menuitem]')].find(el => el.textContent.trim() === {json.dumps(label)}).click()")


async def run(browser):
    await browser.send("Page.navigate", {"url": BASE_URL + "/login"})
    await poll(browser, "document.querySelector('#login-email')", "login form loads", 30)
    await input_text(browser, "#login-email", "son@example.test")
    await input_text(browser, "#login-password", "Passw0rd!test")
    fail("/api/auth/login")
    await click(browser, ".login-submit")
    await poll(browser, "document.querySelector('#login-error')", "HTTP login error is shown")
    assert await browser.evaluate("document.querySelector('#login-password').value === 'Passw0rd!test' && location.pathname === '/login'")
    await click(browser, ".login-submit")
    await poll(browser, "document.querySelector('#postpost-1')", "server feed after login", 30)
    assert await browser.evaluate("document.querySelector('#postpost-1 [data-post-text]').textContent.trim() === 'Bài viết từ backend'")
    assert await browser.evaluate("document.querySelector('#postpost-1 .author-name').getAttribute('href') === '/profile/user-2'")
    print("PASS authenticated server feed, ID route and preserved failed-login form", flush=True)
    other_session = control("/api/auth/login", {"email": "friend@example.test", "password": "Passw0rd!test"})["data"]
    control("/api/comments", {"targetType": "POST", "targetId": "post-2", "targetOwnerId": "user-1", "content": "Comment by another author"}, other_session["accessToken"])
    await click(browser, '#postpost-2 [data-testid="post-comment-trigger"]')
    await poll(browser, "[...document.querySelectorAll('.comment-text')].some(el => el.textContent === 'Comment by another author')", "other author's comment loads")
    assert await browser.evaluate("!document.querySelector('[data-comment-author=\"Bạn Kiểm Thử\"] [data-action=delete-comment]')"), "post owner cannot delete another author's comment"
    await escape(browser)

    await click(browser, "#composerToggle")
    await input_text(browser, "#shareTextInput", "Fixture post draft")
    fail("/api/posts")
    await click(browser, "#sendPostBtn")
    await poll(browser, "document.querySelector('.toast')?.textContent.includes('Fixture: POST /api/posts failed') && !document.querySelector('#sendPostBtn').disabled", "post error is visible")
    assert await browser.evaluate("document.querySelector('#shareTextInput').value === 'Fixture post draft'")
    await click(browser, "#sendPostBtn")
    await poll(browser, "document.querySelector('#shareTextInput').value === '' && [...document.querySelectorAll('[data-post-text]')].some(el => el.textContent.trim() === 'Fixture post draft')", "retry publishes draft")
    print("PASS create-post HTTP failure preserves draft, successful retry clears it", flush=True)

    await click(browser, '#postpost-1 [data-testid="post-comment-trigger"]')
    await poll(browser, "document.querySelector('.comment-input') && !document.querySelector('.comment-input').disabled", "comments load")
    await input_text(browser, ".comment-input", "Fixture comment draft")
    fail("/api/comments")
    await click(browser, ".comment-send-btn")
    await poll(browser, "document.querySelector('[role=dialog] [role=alert]')", "comment error is visible")
    assert await browser.evaluate("document.querySelector('.comment-input').value === 'Fixture comment draft'")
    await click(browser, ".comment-send-btn")
    await poll(browser, "document.querySelector('.comment-input').value === '' && [...document.querySelectorAll('.comment-text')].some(el => el.textContent === 'Fixture comment draft')", "retry posts comment")
    await input_text(browser, ".comment-input", "Committed comment despite refresh failure")
    fail("/api/comments", "GET")
    await click(browser, ".comment-send-btn")
    await poll(browser, "document.querySelector('.comment-input').value === '' || (!document.querySelector('.comment-send-btn').disabled && document.querySelector('[role=dialog] [role=alert]'))", "committed comment submission settles after refresh failure")
    for _ in range(100):
        state = control("/__test/state")
        if any(item["method"] == "GET" and item["path"] == "/api/comments" and item["status"] == 500 for item in state["requests"]):
            break
        await asyncio.sleep(.05)
    writes = [item for item in state["requests"] if item["method"] == "POST" and item["path"] == "/api/comments" and item["body"]["content"] == "Committed comment despite refresh failure"]
    assert len(writes) == 1 and writes[0]["status"] == 201, "comment must be committed exactly once"
    assert any(item["method"] == "GET" and item["path"] == "/api/comments" and item["status"] == 500 for item in state["requests"]), "follow-up comments GET must exercise refresh failure"
    assert await browser.evaluate("document.querySelector('.comment-input').value === ''"), "committed comment must clear the draft even when follow-up GET fails; retaining it invites a duplicate retry"
    assert await browser.evaluate("[...document.querySelectorAll('.comment-text')].filter(el => el.textContent === 'Committed comment despite refresh failure').length === 1"), "POST response must remain visible after refresh failure"
    assert not await browser.evaluate("Boolean(document.querySelector('[role=dialog] [role=alert]'))"), "a committed comment must not be reported as a failed save"
    await escape(browser)
    print("PASS comment HTTP failure/retry, committed-write refresh failure and author-only delete control", flush=True)

    before = int(await browser.evaluate("document.querySelector('#postpost-1 [data-testid=post-react]').dataset.likes"))
    await click(browser, "#postpost-1 [data-testid=post-react]")
    await poll(browser, "document.querySelector('#postpost-1 [data-testid=post-react]').dataset.reaction === 'love'", "server reaction saved")
    assert int(await browser.evaluate("document.querySelector('#postpost-1 [data-testid=post-react]').dataset.likes")) == before + 1
    await click(browser, "#postpost-1 [data-testid=post-react]")
    await poll(browser, "document.querySelector('#postpost-1 [data-testid=post-react]').dataset.reaction === ''", "same reaction removes it")
    assert int(await browser.evaluate("document.querySelector('#postpost-1 [data-testid=post-react]').dataset.likes")) == before
    fail("/api/reactions/summary", "GET")
    await click(browser, "#postpost-1 [data-testid=post-react]")
    await poll(browser, "document.querySelector('#postpost-1 [data-testid=post-react]').dataset.reaction === 'love' || document.querySelector('.toast')?.textContent.includes('Fixture: GET /api/reactions/summary failed')", "committed reaction settles after summary failure")
    for _ in range(100):
        state = control("/__test/state")
        if any(item["method"] == "GET" and item["path"] == "/api/reactions/summary" and item["status"] == 500 for item in state["requests"]):
            break
        await asyncio.sleep(.05)
    assert len([item for item in state["reactions"] if item["targetType"] == "POST" and item["targetId"] == "post-1" and item["userId"] == "user-1"]) == 1, "successful reaction must exist once on server"
    assert any(item["method"] == "GET" and item["path"] == "/api/reactions/summary" and item["status"] == 500 for item in state["requests"]), "summary GET failure must be exercised"
    assert await browser.evaluate("document.querySelector('#postpost-1 [data-testid=post-react]').dataset.reaction === 'love'"), "successful reaction PUT must remain selected when summary GET fails"
    assert int(await browser.evaluate("document.querySelector('#postpost-1 [data-testid=post-react]').dataset.likes")) == before + 1
    assert not await browser.evaluate("document.querySelector('.toast')?.textContent.includes('Fixture: GET /api/reactions/summary failed')"), "a committed reaction must not be reported as a failed save"
    await menu(browser, "post-1", "Save Post")
    await poll(browser, "!document.querySelector('[role=menu]') && !document.querySelector('#postpost-1 .card-more-btn').disabled", "save finishes and menu closes")
    await menu(browser, "post-1", "Report")
    await input_text(browser, 'select[name="reason"]', "SPAM")
    await input_text(browser, 'textarea[name="details"]', "Fixture report")
    await click(browser, '[role="dialog"] button[type="submit"]')
    await poll(browser, "!document.querySelector('[role=dialog]')", "successful report closes")
    print("PASS reaction server counts, reaction removal, saved/report requests", flush=True)

    await menu(browser, "post-2", "Edit Post")
    await input_text(browser, '[aria-label="Edit post text"]', "Fixture edited post")
    fail("/api/posts/post-2", "PUT")
    await click(browser, '[data-testid="save-post-edit"]')
    await poll(browser, "document.querySelector('[role=dialog] [role=alert]')", "edit error is visible")
    assert await browser.evaluate("document.querySelector('[aria-label=\"Edit post text\"]').value === 'Fixture edited post'")
    await click(browser, '[data-testid="save-post-edit"]')
    await poll(browser, "!document.querySelector('[role=dialog]') && document.querySelector('#postpost-2 [data-post-text]').textContent === 'Fixture edited post'", "edit succeeds after retry")
    await menu(browser, "post-2", "Delete Post")
    await click(browser, '[data-testid="delete-post-confirm"]')
    await poll(browser, "!document.querySelector('#postpost-2')", "server delete removes post")

    await browser.send("Page.navigate", {"url": BASE_URL + "/post/post-1"})
    await poll(browser, "document.querySelector('#postpost-1')", "deep link loads through API", 30)
    requests = control("/__test/state")["requests"]
    expected = [("POST", "/api/posts"), ("POST", "/api/comments"),
        ("PUT", "/api/reactions"), ("DELETE", "/api/reactions"),
        ("PUT", "/api/saved"), ("POST", "/api/moderation/reports"),
        ("PUT", "/api/posts/post-2"), ("DELETE", "/api/posts/post-2"),
        ("GET", "/api/posts/post-1")]
    for method, path in expected:
        assert any(item["method"] == method and item["path"] == path and 200 <= item["status"] < 300 for item in requests), (method, path)
    report = next(item for item in reversed(requests) if item["path"] == "/api/moderation/reports")
    assert report["body"]["targetId"] == "post-1" and report["body"]["reason"] == "SPAM"
    print("PASS edit failure/retry, delete and direct post fetch; HTTP request audit", flush=True)
