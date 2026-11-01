"""Two real browser profiles exercise HTTP history and STOMP delivery against the fixture.

Accounts log in through the UI. No session seeding, React internals, or live Java claim.
"""
import asyncio
import json

from run_connected import chrome, control
from test_connected_screens import navigate, server_check
from test_next import check, click, input_text, poll, submit

FORM = '[data-form="message"]'
INPUT = FORM + ' [name="message"]'


async def login(browser, email, partner):
    await navigate(browser, "/login", "#login-email")
    await click(browser, '[aria-label="Hiện mật khẩu"]')
    await check(browser, 'document.querySelector("#login-password").type === "text"', "login is hydrated: " + email)
    await input_text(browser, "#login-email", email)
    await input_text(browser, "#login-password", "Passw0rd!test")
    await submit(browser, ".login-form")
    await poll(browser, "location.pathname === '/' && document.querySelector('.nav-item')", "login " + email, timeout=30)
    await navigate(browser, "/messages/" + partner, INPUT)
    await poll(browser, f'!document.querySelector({json.dumps(INPUT)}).disabled', "conversation ready", timeout=15)


def bubble_count(text, outgoing=None):
    selector = ".chat-history .message-bubble" + (".outgoing" if outgoing is True else ":not(.outgoing)" if outgoing is False else "")
    return f'[...document.querySelectorAll({json.dumps(selector)})].filter(el => el.querySelector("p")?.textContent === {json.dumps(text)}).length'


async def delivered(sender, recipient, text):
    await check(sender, bubble_count(text, True) + " === 1", "sender echo appears exactly once")
    await check(recipient, bubble_count(text, False) + " === 1", "recipient gets exactly one live message")
    await check(sender, f'document.querySelector({json.dumps(INPUT)}).value === ""', "draft clears only after sender echo")
    state = await server_check(lambda state: len([m for m in state["messages"] if m["content"] == text]) == 1, "one message persisted on server")
    return next(m for m in state["messages"] if m["content"] == text)


async def run(browser):
    async with chrome() as friend:
        await login(browser, "son@example.test", "user-2")
        await login(friend, "friend@example.test", "user-1")
        await server_check(lambda state: all(any(e.get("actor") == actor and e.get("command") == "SUBSCRIBE" and e.get("destination") == "/user/queue/messages" for e in state["wsEvents"]) for actor in ("user-1", "user-2")), "both authenticated STOMP subscriptions are active")
        await check(browser, bubble_count("Tin nhắn từ backend") + " === 1", "initial history loads from backend")

        text = "Tin realtime từ tài khoản Sơn"
        await input_text(browser, INPUT, text)
        await submit(browser, FORM)
        message = await delivered(browser, friend, text)
        await server_check(lambda state: any(r["method"] == "STOMP" and r["path"] == "/app/chat.send" and r["status"] == 200 and r["body"].get("conversationId") == "conversation-1" and r["body"].get("content") == text for r in state["requests"]), "SEND uses backend conversation ID")

        # Re-deliver the same persisted ID over actual sockets, as reconnects may do.
        replay = control("/__test/replay-message", {"id": message["id"]})
        assert replay["data"]["replayed"] == message["id"]
        await asyncio.sleep(.25)
        await check(browser, bubble_count(text, True) + " === 1", "sender deduplicates repeated MESSAGE ID")
        await check(friend, bubble_count(text, False) + " === 1", "recipient deduplicates repeated MESSAGE ID")

        # Keep the conversation selected while a new message arrives: no reload or reopening.
        await server_check(lambda state: any(m["id"] == message["id"] and "user-2" in m["readBy"] for m in state["messages"]), "incoming message is marked read while selected")
        await check(friend, '!document.querySelector(".conversation.selected .count-badge")', "selected conversation has no unread badge")

        reply = "Phản hồi realtime của bạn"
        await input_text(friend, INPUT, reply)
        await submit(friend, FORM)
        response = await delivered(friend, browser, reply)
        await server_check(lambda state: any(m["id"] == response["id"] and "user-1" in m["readBy"] for m in state["messages"]), "reverse delivery marks read for selected sender")

        # Full document reload restores the authenticated session and requests saved history.
        before = sum(r["path"] == "/api/chat/conversations/conversation-1/messages" for r in control("/__test/state")["requests"])
        previous_document = await browser.evaluate("performance.timeOrigin")
        await browser.send("Page.reload")
        await poll(browser, f'performance.timeOrigin !== {json.dumps(previous_document)} && document.querySelector({json.dumps(INPUT)}) !== null', "chat after reload", timeout=30)
        await check(browser, bubble_count(text, True) + " === 1", "reloaded history keeps one sender message")
        await check(browser, bubble_count(reply, False) + " === 1", "reloaded history keeps one recipient message")
        await server_check(lambda state: sum(r["path"] == "/api/chat/conversations/conversation-1/messages" for r in state["requests"]) > before, "reload reads history through HTTP")

        failed_text = "Bản nháp giữ lại khi STOMP bị từ chối"
        connections = sum(e.get("event") == "connected" and e.get("actor") == "user-1" and e.get("endpoint") == "/ws/websocket" for e in control("/__test/state")["wsEvents"])
        control("/__test/fail-next", {"path": "/app/chat.send", "method": "STOMP", "message": "Chat send failed"})
        await input_text(browser, INPUT, failed_text)
        await submit(browser, FORM)
        await server_check(lambda state: any(r["method"] == "STOMP" and r["status"] == 500 and r["body"].get("content") == failed_text for r in state["requests"]), "fixture rejects actual SEND")
        await check(browser, f'document.querySelector({json.dumps(INPUT)}).value === {json.dumps(failed_text)} && !document.querySelector({json.dumps(INPUT)}).disabled', "failed SEND keeps editable draft", timeout=15)
        await check(browser, 'Boolean(document.querySelector(".messenger + [role=alert]"))', "send failure is visible in chat")
        state = control("/__test/state")
        assert not any(m["content"] == failed_text for m in state["messages"]), "Rejected SEND must not persist a message"
        await check(browser, bubble_count(failed_text) + " === 0", "failed SEND does not claim delivery")
        await check(friend, bubble_count(failed_text) + " === 0", "recipient does not see rejected SEND")

        await server_check(lambda state: sum(e.get("event") == "connected" and e.get("actor") == "user-1" and e.get("endpoint") == "/ws/websocket" for e in state["wsEvents"]) > connections, "sender reconnects after STOMP ERROR", timeout=15)
        await submit(browser, FORM)
        await delivered(browser, friend, failed_text)
        await asyncio.sleep(.25)
        await check(browser, bubble_count(failed_text) + " === 1", "retry has no duplicate sender bubble")
        await check(friend, bubble_count(failed_text) + " === 1", "retry has no duplicate recipient bubble")
