"""Independent CDP regressions for the Next app; run with `python tests/test_next.py`.

BASE_URL defaults to http://127.0.0.1:3000. CHROME_PATH may override Chrome.
Only Browser's CDP transport is shared with the legacy suite. No app globals,
React internals, localStorage seeding, or dependency on the port-8000 app.
"""

import asyncio
from contextlib import asynccontextmanager
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import urllib.request
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from browser_checks import Browser, SCREENSHOTS  # noqa: E402
import websockets  # noqa: E402 (already used by browser_checks)

BASE_URL = os.environ.get('BASE_URL', 'http://127.0.0.1:3000').rstrip('/')
DIALOG = '[role="dialog"], dialog[open]'
THEME_TOGGLE = '.theme-toggle'
VIEWPORTS = [(1440, 900), (1024, 768), (820, 1180), (390, 844), (320, 640)]
ROUTES = [
    ('/', 'Feeds', True, 1),
    ('/messages', 'Messages', False, 1),
    ('/messages/George%20Lobko', 'Messages', False, 1),
    ('/explore', 'Explore', False, 1),
    ('/friends/requests', 'Friends', False, 1),
    ('/notifications', 'Notifications', False, 1),
    ('/settings', 'Settings', False, 1),
    ('/profile/Bogdan%20Nikitin', 'Profile', True, 0),
    ('/profile/George%20Lobko', 'Profile', False, 1),
    ('/explore/joined', 'Explore', False, 1),
    ('/explore/UI%2FUX', 'Explore', False, 1),
    ('/topic/trail', 'Discussion', False, 1),
    ('/post/1', 'Post', False, 1),
    ('/stories', 'Stories', False, 0),
]


async def poll(browser, expression, label, timeout=5):
    """Wait for DOM/React state without hiding JS errors or timing out silently."""
    deadline = time.monotonic() + timeout
    last = None
    while True:
        try:
            last = await browser.evaluate(f'Boolean({expression})')
        except RuntimeError as exc:
            if 'Execution context was destroyed' not in str(exc) and 'Cannot find context' not in str(exc):
                raise
            last = False
        if last:
            return last
        if time.monotonic() >= deadline:
            raise AssertionError(f'{label}: timed out after {timeout}s; last={last!r}')
        await asyncio.sleep(.05)


async def check(browser, expression, label, timeout=5):
    await poll(browser, f'Boolean({expression})', label, timeout)
    print(f'  PASS {label}', flush=True)


async def viewport(browser, width, height):
    await browser.send('Emulation.setDeviceMetricsOverride', {
        'width': width, 'height': height, 'deviceScaleFactor': 1,
        'mobile': width <= 700,
    })
    await browser.send('Emulation.setTouchEmulationEnabled', {'enabled': width <= 700})
    await browser.evaluate('new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))')


async def click(browser, selector):
    await poll(browser, f'document.querySelector({json.dumps(selector)}) !== null',
               f'find click target {selector}')
    box = await browser.evaluate(f'''(async () => {{
        const el = document.querySelector({json.dumps(selector)});
        if (!el.getClientRects().length || getComputedStyle(el).visibility === 'hidden')
            throw new Error('Click target is hidden: ' + {json.dumps(selector)});
        if (el.disabled) throw new Error('Click target is disabled');
        if (el.classList.contains('skip-link')) el.focus();
        const wrapper=document.querySelector('[data-testid="screen-content"]');
        if(wrapper) {{
            for(let frame=0;frame<90;frame++) {{
                const style=getComputedStyle(wrapper);
                const y=style.transform==='none' ? 0 : new DOMMatrixReadOnly(style.transform).m42;
                if(Number(style.opacity)>=.999 && Math.abs(y)<.5) break;
                await new Promise(requestAnimationFrame);
            }}
        }}
        el.scrollIntoView({{block:'center',behavior:'instant'}});
        let previous, stable=0;
        for(let frame=0;frame<60;frame++) {{
            await new Promise(requestAnimationFrame);
            const rect=el.getBoundingClientRect().toJSON();
            stable=previous && ['x','y','width','height'].every(k=>Math.abs(rect[k]-previous[k])<.25) ? stable+1 : 0;
            if(stable>=2) return rect;
            previous=rect;
        }}
        throw new Error('Click target did not stop moving: '+{json.dumps(selector)});
    }})()''')
    x, y = box['x'] + box['width'] / 2, box['y'] + box['height'] / 2
    await browser.send('Input.dispatchMouseEvent', {'type': 'mouseMoved', 'x': x, 'y': y})
    point = None
    stable = False
    for _ in range(12):
        point = await browser.evaluate(f'''(async () => {{
            const el=document.querySelector({json.dumps(selector)});
            await new Promise(requestAnimationFrame);
            const rect=el.getBoundingClientRect();
            const x=rect.left+rect.width/2, y=rect.top+rect.height/2;
            const hit=document.elementFromPoint(x,y);
            const describe=node=>node ? node.tagName.toLowerCase()+(node.id?'#'+node.id:'')+'.'+[...node.classList].join('.') : 'none';
            return {{x,y,matches:hit===el||el.contains(hit),hit:describe(hit),
                rect:rect.toJSON(),scrollY,route:location.pathname}};
        }})()''')
        x, y = point['x'], point['y']
        if point['matches']:
            # Keep the point only if hover/layout has settled without moving
            # the hit target away during the pointer's actual approach.
            stable = await browser.evaluate(f'''(async () => {{
                const el=document.querySelector({json.dumps(selector)});
                const first=el.getBoundingClientRect();
                await new Promise(requestAnimationFrame);
                const last=el.getBoundingClientRect();
                const hit=document.elementFromPoint({x},{y});
                return hit===el || el.contains(hit) ?
                    ['x','y','width','height'].every(key=>Math.abs(first[key]-last[key])<.25) : false;
            }})()''')
            if stable:
                break
        await browser.send('Input.dispatchMouseEvent', {'type': 'mouseMoved', 'x': x, 'y': y})
    if not point or not point['matches'] or not stable:
        raise AssertionError(f"Pointer missed {selector}; center hit {point['hit'] if point else 'none'}; "
                             f"rect={point['rect'] if point else 'none'}; "
                             f"scrollY={point['scrollY'] if point else 'unknown'}; "
                             f"route={point['route'] if point else 'unknown'}")
    await browser.send('Input.dispatchMouseEvent', {'type': 'mousePressed', 'x': x, 'y': y, 'button': 'left', 'clickCount': 1})
    await browser.send('Input.dispatchMouseEvent', {'type': 'mouseReleased', 'x': x, 'y': y, 'button': 'left', 'clickCount': 1})
    await browser.evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))')


async def input_text(browser, selector, value):
    await poll(browser, f'document.querySelector({json.dumps(selector)}) !== null',
               f'find input {selector}')
    # Native setters bypass React's value tracker so the input event updates state.
    await browser.evaluate(f'''(() => {{
        const el = document.querySelector({json.dumps(selector)});
        const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype
            : el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
        Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, {json.dumps(value)});
        el.dispatchEvent(new Event('input', {{bubbles:true}}));
        el.dispatchEvent(new Event('change', {{bubbles:true}}));
    }})()''')


async def select_option(browser, selector, value):
    """Choose a native select option through its public input/change events."""
    await poll(browser, f'document.querySelector({json.dumps(selector)}) !== null',
               f'find select {selector}')
    await browser.evaluate(f'''(() => {{
        const el=document.querySelector({json.dumps(selector)});
        if(!(el instanceof HTMLSelectElement)) throw new Error('Expected a native select');
        Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value').set.call(el,{json.dumps(value)});
        el.dispatchEvent(new Event('input',{{bubbles:true}}));
        el.dispatchEvent(new Event('change',{{bubbles:true}}));
    }})()''')


async def select_option_text(browser, selector, label):
    value = await browser.evaluate(f'''(() => {{
        const el=document.querySelector({json.dumps(selector)});
        const option=[...el.options].find(item=>item.textContent.trim()==={json.dumps(label)});
        if(!option) throw new Error('No select option labeled {label}');
        return option.value;
    }})()''')
    await select_option(browser, selector, value)


async def submit(browser, selector):
    await browser.evaluate(f'document.querySelector({json.dumps(selector)}).requestSubmit()')


async def escape(browser):
    await browser.send('Input.dispatchKeyEvent', {
        'type': 'keyDown', 'key': 'Escape', 'code': 'Escape', 'windowsVirtualKeyCode': 27,
    })
    await browser.send('Input.dispatchKeyEvent', {'type': 'keyUp', 'key': 'Escape', 'code': 'Escape'})


async def close_dialog(browser):
    await browser.evaluate(f'''(() => {{
        const dialog = document.querySelector({json.dumps(DIALOG)});
        if (!dialog) throw new Error('No open dialog');
        const close = [...dialog.querySelectorAll('button')].find(b =>
            b.dataset.action === 'close-dialog' || b.getAttribute('aria-label') === 'Close' ||
            b.getAttribute('aria-label') === 'Close dialog' || b.textContent.trim() === 'Close');
        if (!close) throw new Error('No accessible dialog Close button');
        close.click();
    }})()''')
    await poll(browser, f'!document.querySelector({json.dumps(DIALOG)})', 'dialog closes')


async def click_dialog_button(browser, label):
    await poll(browser, f'''(() => {{
        const dialog=document.querySelector({json.dumps(DIALOG)});
        return Boolean(dialog && [...dialog.querySelectorAll('button')].some(button =>
            button.getClientRects().length && button.textContent.trim().toLowerCase().includes({json.dumps(label.lower())})));
    }})()''', f'find dialog button {label}')
    box = await browser.evaluate(f'''(() => {{
        const dialog=document.querySelector({json.dumps(DIALOG)});
        const button=[...dialog.querySelectorAll('button')].find(button =>
            button.getClientRects().length && button.textContent.trim().toLowerCase().includes({json.dumps(label.lower())}));
        if (!button) throw new Error('No dialog button matching {label}');
        button.scrollIntoView({{block:'center'}});
        return button.getBoundingClientRect().toJSON();
    }})()''')
    x, y = box['x'] + box['width'] / 2, box['y'] + box['height'] / 2
    await browser.send('Input.dispatchMouseEvent', {'type': 'mouseMoved', 'x': x, 'y': y})
    await browser.send('Input.dispatchMouseEvent', {'type': 'mousePressed', 'x': x, 'y': y, 'button': 'left', 'clickCount': 1})
    await browser.send('Input.dispatchMouseEvent', {'type': 'mouseReleased', 'x': x, 'y': y, 'button': 'left', 'clickCount': 1})
    await poll(browser, f'!document.querySelector({json.dumps(DIALOG)})', f'dialog closes after {label}')


async def ready(browser):
    await poll(browser, "document.readyState === 'complete' && document.querySelector('h1') && "
               "document.querySelector('.nav-item')", 'Next DOM ready', timeout=30)
    await poll(browser, "['light','dark'].includes(document.documentElement.dataset.theme)", 'theme hydrated')
    await browser.evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))')
    # A rendered SSR tree is not proof of hydration; exercise a real UI handler.
    has_composer = await browser.evaluate("Boolean(document.querySelector('#composerToggle')?.getClientRects().length)")
    if has_composer:
        await poll(browser, """(() => {
            const panel = document.querySelector('#composerPanel');
            if (panel && !panel.hidden && panel.getClientRects().length) return true;
            document.querySelector('#composerToggle')?.click(); return false;
        })()""", 'React hydration: plus opens composer', timeout=30)
        # Setup clicks must not depend on a pointer landing during the dock's
        # initial layout animation. The real pointer minimize path is covered
        # by the interaction groups; HTMLElement.click() still dispatches the
        # React handler exercised here.
        await browser.evaluate("document.querySelector('#minimizeComposer')?.click()")
        await poll(browser, "!document.querySelector('#composerPanel')?.getClientRects().length",
                   'composer minimized after hydration')
        await poll(browser, "Math.abs(document.querySelector('#shareDock').getBoundingClientRect().width-60)<1 && "
                   "Math.abs(document.querySelector('#shareDock').getBoundingClientRect().height-60)<1",
                   'composer collapse motion settles')
    else:
        before = await browser.evaluate('document.documentElement.dataset.theme')
        await poll(browser, f'''(() => {{
            if (document.documentElement.dataset.theme !== {json.dumps(before)}) return true;
            document.querySelector({json.dumps(THEME_TOGGLE)})?.click(); return false;
        }})()''', 'React hydration: theme toggle responds', timeout=30)
        if before in ('light', 'dark'):
            await theme(browser, before)
    await poll(browser, "document.fonts.status === 'loaded'", 'web fonts loaded', timeout=20)


async def theme(browser, value):
    if await browser.evaluate('document.documentElement.dataset.theme') != value:
        if await browser.evaluate("Boolean(document.querySelector('.theme-toggle')?.getClientRects().length)"):
            await click(browser, THEME_TOGGLE)
        else:
            # The app exposes its T shortcut when the desktop quick links are hidden.
            await browser.evaluate("document.querySelector('#mainContent').focus(); "
                                   "document.activeElement.dispatchEvent(new KeyboardEvent('keydown',{key:'t',bubbles:true}))")
    await poll(browser, f'document.documentElement.dataset.theme === {json.dumps(value)}',
               f'{value} theme applied')
    await browser.evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))')
    await poll(browser, "document.querySelector('.main-window')?.getAnimations()"
               ".every(animation => animation.playState !== 'running')",
               'theme background transition settles', timeout=2)


async def load(browser, path):
    """Direct navigation tests the actual Next route, including reload behavior."""
    previous = await browser.evaluate('location.href')
    previous_epoch = await browser.evaluate('performance.timeOrigin')
    target = BASE_URL + path
    if previous == target:
        await browser.send('Page.reload')
        await poll(browser, f'location.href === {json.dumps(target)} && '
                   f'performance.timeOrigin !== {json.dumps(previous_epoch)} && '
                   'document.readyState === "complete"',
                   f'reload {path} commits a new document', timeout=30)
    else:
        result = await browser.send('Page.navigate', {'url': target})
        assert not result.get('errorText'), result
        await poll(browser, f'location.href === {json.dumps(target)} && document.readyState === "complete"',
                   f'navigate {path}', timeout=30)
    await ready(browser)


async def go(browser, path):
    """Click a rendered Next link; deliberately never replace client state."""
    if await browser.evaluate('location.pathname') == path:
        return
    await poll(browser, f'''[...document.querySelectorAll('a[href]')].some(a =>
        a.getClientRects().length && new URL(a.href).pathname === {json.dumps(path)})''',
        f'UI link to {path}')
    await browser.evaluate(f'''[...document.querySelectorAll('a[href]')].find(a =>
        a.getClientRects().length && new URL(a.href).pathname === {json.dumps(path)}).click()''')
    await poll(browser, f'location.pathname === {json.dumps(path)} && Boolean(document.querySelector("h1"))',
               f'client route {path}')


async def composer(browser):
    if not await browser.evaluate("Boolean(document.querySelector('#composerPanel')?.getClientRects().length)"):
        await click(browser, '#composerToggle')
    await poll(browser, "Boolean(document.querySelector('#composerPanel')?.getClientRects().length)",
               'composer opens')


@asynccontextmanager
async def chrome(width=1440, height=900):
    executable = os.environ.get('CHROME_PATH') or str(
        Path(os.environ.get('PROGRAMFILES', r'C:\Program Files')) /
        'Google/Chrome/Application/chrome.exe')
    with tempfile.TemporaryDirectory(prefix='socialmedia-next-') as profile:
        process = subprocess.Popen([
            executable, '--headless', '--disable-gpu', '--force-device-scale-factor=1',
            '--autoplay-policy=no-user-gesture-required', '--remote-debugging-port=0',
            f'--user-data-dir={profile}', f'--window-size={width},{height}', 'about:blank',
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            port_file = Path(profile) / 'DevToolsActivePort'
            deadline = time.monotonic() + 15
            while not port_file.exists():
                if process.poll() is not None or time.monotonic() >= deadline:
                    raise RuntimeError('Next test Chrome did not start')
                await asyncio.sleep(.05)
            port = port_file.read_text().splitlines()[0]
            with urllib.request.urlopen(f'http://127.0.0.1:{port}/json', timeout=5) as response:
                page = next(t for t in json.load(response) if t.get('type') == 'page')
            async with websockets.connect(page['webSocketDebuggerUrl'], max_size=16 * 1024 * 1024) as socket:
                browser = Browser(socket)
                await browser.send('Runtime.enable')
                await browser.send('Page.enable')
                await viewport(browser, width, height)
                await load(browser, '/')
                try:
                    yield browser
                    assert not browser.errors, f'Uncaught browser exceptions: {browser.errors}'
                finally:
                    try:
                        await asyncio.wait_for(browser.send('Browser.close'), timeout=3)
                    except (websockets.ConnectionClosed, TimeoutError):
                        pass
        finally:
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)


async def navigation(browser):
    """Navigation/back, message text, friend state/search, profile and settings."""
    await check(browser, "document.querySelector('#shareDock').classList.contains('collapsed')",
                'composer initially collapsed')
    await composer(browser)
    await input_text(browser, '#shareTextInput', 'Draft stays here')
    await click(browser, '#minimizeComposer')
    await composer(browser)
    await check(browser, "document.querySelector('#shareTextInput').value === 'Draft stays here'",
                'draft survives minimize')
    await go(browser, '/messages')
    await check(browser, "!document.querySelector('#shareDock')?.getClientRects().length",
                'messages hides composer')
    await browser.evaluate('history.back()')
    await poll(browser, "location.pathname === '/'", 'browser Back returns to feed')
    await composer(browser)
    await check(browser, "document.querySelector('#shareTextInput').value === 'Draft stays here'",
                'draft survives client navigation and browser Back')
    await input_text(browser, '#shareTextInput', '')
    await click(browser, '#minimizeComposer')
    await click(browser, '.skip-link')
    await check(browser, "document.activeElement.id === 'mainContent'", 'skip link focuses main content')
    await go(browser, '/messages')
    await go(browser, '/messages/George%20Lobko')
    await check(browser, "document.querySelector('#messagesBadge')?.textContent === '4'",
                'opening conversation clears its unread badge')
    literal = '<img src=x onerror=alert(1)>'
    await input_text(browser, '[name=message]', literal)
    await submit(browser, 'form[data-form=message], .message-form')
    await check(browser, f"document.querySelector('.outgoing:last-child p')?.textContent === {json.dumps(literal)} && "
                "!document.querySelector('.chat-history p img') && document.querySelector('[name=message]').value === ''",
                'message sends literal text and resets input')
    await go(browser, '/friends')
    await go(browser, '/friends/requests')
    await click(browser, '[data-action=accept][data-value=Silena]')
    await check(browser, "document.querySelector('#friendsBadge')?.textContent === '2'", 'accept request decrements badge')
    await go(browser, '/friends/all')
    await check(browser, "document.querySelector('.person-list')?.textContent.includes('Silena')", 'accepted friend appears')
    await input_text(browser, '[data-search]', 'no-such-person')
    await check(browser, "Boolean(document.querySelector('[data-no-results]')?.getClientRects().length) && "
                "![...document.querySelectorAll('[data-search-item]')].some(e => e.getClientRects().length)",
                'people search shows no-results state')
    await go(browser, '/friends/suggestions')
    await click(browser, '[data-action=follow][data-value="Nick Shelburne"]')
    await go(browser, '/profile/Nick%20Shelburne')
    await check(browser, "document.querySelector('[data-action=follow]')?.textContent === 'Following' && "
                "!document.querySelector('#shareDock')?.getClientRects().length", 'follow state shared with profile')
    await click(browser, '[data-action=back], .back-link')
    await check(browser, "location.pathname === '/friends/suggestions'", 'profile Back restores its Friends source')
    await go(browser, '/')
    for selector, prefix in [('.user-chip[href="/profile/Silena"]', '/profile'),
                             ('.profile-header', '/profile'), ('.see-all-link', '/friends'),
                             ('.rec-card-tile[href="/explore/Music"]', '/explore/'), ('#post1 .author-avatar', '/profile')]:
        await click(browser, selector)
        await check(browser, f'location.pathname.startsWith({json.dumps(prefix)})', f'{selector} navigates')
        await go(browser, '/')
    await go(browser, '/settings')
    await input_text(browser, '[name=name]', 'Linh Tran')
    await input_text(browser, '[name=bio]', '<b>Designer</b>')
    await input_text(browser, '[name=theme]', 'dark')
    await input_text(browser, '[name=audience]', 'Friends')
    await submit(browser, 'form[data-form=settings], .settings-form')
    await check(browser, "document.documentElement.dataset.theme === 'dark' && "
                "document.querySelector('[data-settings-status]')?.textContent.toLowerCase().includes('saved')",
                'settings save applies theme and status')
    await go(browser, '/profile')
    await check(browser, "document.querySelector('.profile-summary > p')?.textContent === '<b>Designer</b>' && "
                "!document.querySelector('.profile-summary > p b')", 'profile bio stays literal')
    await go(browser, '/')
    await composer(browser)
    await input_text(browser, '#shareTextInput', 'After editing my profile')
    await click(browser, '#sendPostBtn')
    await check(browser, "document.querySelector('#feedStream .new-post .author-name')?.textContent === 'Linh Tran' && "
                "document.querySelector('#feedStream .new-post .post-time')?.textContent.includes('Friends')",
                'new post uses edited author and default audience')
    await load(browser, '/')
    await check(browser, "document.documentElement.dataset.theme === 'dark' && document.querySelector('.profile-name')?.textContent === 'Linh Tran' && document.querySelector('.new-post')?.textContent.includes('After editing my profile')", 'reload keeps theme, profile edits and published posts')


async def discussions(browser):
    """Explore categories, join state, new literal topic/reply and report dialog."""
    await go(browser, '/explore')
    await go(browser, '/explore/UI%2FUX')
    await check(browser, "Boolean(document.querySelector('.category-hero')) && document.querySelector('.section-heading')?.textContent.includes('UI/UX discussions') && "
                "decodeURIComponent(location.pathname).endsWith('/UI/UX')", 'encoded slash category stays one route segment')
    await click(browser, '[data-action=back], .back-link')
    await check(browser, "location.pathname === '/explore'", 'category Back returns to Explore')
    await go(browser, '/explore/Cooking')
    await click(browser, '[data-action=join]')
    await check(browser, "document.querySelector('[data-action=join]')?.textContent.includes('Joined')", 'join community')
    await go(browser, '/explore')
    await go(browser, '/explore/joined')
    await check(browser, "document.querySelector('.screen-content')?.textContent.includes('Coconut milk latte')",
                'joined community filters discussions')
    await click(browser, '[data-action=new-topic]')
    await input_text(browser, '[name=category]', 'Cooking')
    await input_text(browser, '[name=title]', '<b>A new discussion</b>')
    await input_text(browser, '[name=body]', '<img src=x onerror=alert(1)>')
    await submit(browser, 'form[data-form=topic]')
    await check(browser, "location.pathname.startsWith('/topic/') && "
                "document.querySelector('.discussion-detail h2')?.textContent === '<b>A new discussion</b>' && "
                "!document.querySelector('.discussion-detail h2 b, .discussion-body img')",
                'topic creation navigates and escapes title/body')
    await input_text(browser, '[name=reply]', '<script>reply</script>')
    await submit(browser, 'form[data-form=reply]')
    await check(browser, "document.querySelector('.reply-list')?.textContent.includes('<script>reply</script>') && "
                "!document.querySelector('.reply-list script')", 'discussion reply stays literal')
    await click(browser, '[data-action=back], .back-link')
    await check(browser, "location.pathname === '/explore/Cooking'", 'new topic Back returns to its selected category')
    await go(browser, '/')
    await click(browser, '#post1 .card-more-btn')
    await browser.evaluate("""(() => {
        const button = [...document.querySelectorAll('#dropdownMenu1 button, [role=menuitem]')]
            .find(b => /report/i.test(b.textContent));
        if (!button) throw new Error('No Report action'); button.click();
    })()""")
    await input_text(browser, '[name=reason]', 'Spam')
    await submit(browser, '[role=dialog] form, form[data-form=report]')
    await check(browser, "[...document.querySelectorAll('[role=status]')].some(e => "
                "/report.*(saved|recorded).*locally/i.test(e.textContent))",
                'report saves locally with feedback')
    if await browser.evaluate(f'Boolean(document.querySelector({json.dumps(DIALOG)}))'):
        await close_dialog(browser)


async def reactions_and_posts(browser):
    """Hover/focus/touch reactions, counts, literal post/comments, save and detail."""
    await browser.evaluate("document.querySelector('#likeBtn1').scrollIntoView({block:'center'})")
    box = await browser.evaluate("document.querySelector('#likeBtn1').getBoundingClientRect().toJSON()")
    await browser.send('Input.dispatchMouseEvent', {'type': 'mouseMoved', 'x': box['x'] + 10, 'y': box['y'] + 10})
    await check(browser, "Boolean(document.querySelector('#post1 .post-reaction-picker button[aria-label=Love]'))",
                'real pointer hover mounts reaction choices')
    before = int(await browser.evaluate("document.querySelector('#likeBtn1').dataset.likes"))
    selected = bool(await browser.evaluate("document.querySelector('#likeBtn1').dataset.reaction"))
    expected = before if selected else before + 1
    for key, label in [('haha', 'Haha'), ('sad', 'Sad'), ('wow', 'Wow'), ('angry', 'Angry'), ('love', 'Love')]:
        await browser.evaluate("document.activeElement?.blur(); document.querySelector('#likeBtn1').focus()")
        await click(browser, f'#post1 .post-reaction-picker button[aria-label={label}]')
        await check(browser, f"document.querySelector('#likeBtn1').dataset.reaction === '{key}' && "
                    f"Number(document.querySelector('#likeBtn1').dataset.likes) === {expected} && "
                    f"document.querySelector('#likeBtn1').getAttribute('aria-label').includes('{label} reaction selected')",
                    f'{label} replaces reaction without double count')
    await click(browser, '#likeBtn1')
    await check(browser, f"!document.querySelector('#likeBtn1').dataset.reaction && "
                f"Number(document.querySelector('#likeBtn1').dataset.likes) === {expected - 1}", 'remove reaction decrements once')
    await browser.send('Input.dispatchMouseEvent', {'type': 'mouseMoved', 'x': 1, 'y': 1})
    await browser.evaluate("document.querySelector('#likeBtn1').blur(); document.querySelector('#likeBtn1').focus()")
    await check(browser, "Boolean(document.querySelector('#post1 .post-reaction-picker button[aria-label=Wow]'))",
                'keyboard focus mounts reaction choices')
    await escape(browser)
    await viewport(browser, 390, 844)
    await browser.evaluate("document.querySelector('#likeBtn1').blur(); document.querySelector('#likeBtn1').scrollIntoView({block:'center'})")
    box = await browser.evaluate("document.querySelector('#likeBtn1').getBoundingClientRect().toJSON()")
    await browser.send('Input.dispatchTouchEvent', {'type': 'touchStart', 'touchPoints': [
        {'x': box['x'] + box['width'] / 2, 'y': box['y'] + box['height'] / 2}]})
    await browser.send('Input.dispatchTouchEvent', {'type': 'touchEnd', 'touchPoints': []})
    await check(browser, "Boolean(document.querySelector('#post1 .post-reaction-picker button[aria-label=Wow]'))",
                'real touch opens reaction choices')
    await click(browser, '#post1 .post-reaction-picker button[aria-label=Wow]')
    await check(browser, f"document.querySelector('#likeBtn1').dataset.reaction === 'wow' && "
                f"Number(document.querySelector('#likeBtn1').dataset.likes) === {expected}", 'touch selection increments once')
    await browser.capture(SCREENSHOTS / 'next_reactions_mobile.png')
    await viewport(browser, 1440, 900)
    await click(browser, '.filter-tab[data-filter=popular]')
    await check(browser, "document.querySelector('[data-filter=popular]').classList.contains('active')", 'popular feed filter')
    await click(browser, '.filter-tab[data-filter=recents]')
    await composer(browser)
    post_count = await browser.evaluate("document.querySelectorAll('#feedStream .new-post').length")
    await click(browser, '#sendPostBtn')
    await check(browser, f"document.querySelectorAll('#feedStream .new-post').length === {post_count}",
                'empty publish preserves existing post count')
    await click(browser, '#privacySelectBtn')
    await browser.evaluate("""(() => {
        const b = [...document.querySelectorAll('.privacy-opt')].find(b => /Friends/.test(b.textContent));
        if (!b) throw new Error('No Friends audience'); b.click();
    })()""")
    await input_text(browser, '#shareTextInput', '<b>Post text</b>')
    await click(browser, '#sendPostBtn')
    await check(browser, "document.querySelector('#feedStream .new-post :is([data-post-text],.post-text-content p)')?.textContent === '<b>Post text</b>' && "
                "!document.querySelector('#feedStream .new-post :is([data-post-text],.post-text-content p) b') && "
                "document.querySelector('#feedStream .new-post .post-time').textContent.includes('Friends')",
                'published post is literal and uses selected audience')
    post_id = await browser.evaluate("document.querySelector('#feedStream article.new-post').id")
    post = '#' + post_id
    await click(browser, post + ' .comment-btn')
    await check(browser, "Boolean(document.querySelector('[data-testid=comment-dialog]')) && "
                "!document.querySelector('[role=dialog] .comments-drawer')",
                'Comment opens a dialog instead of an inline drawer')
    await input_text(browser, '[data-testid=comment-dialog] .comment-input', '<img src=x onerror=alert(1)>')
    await click(browser, '[data-testid=comment-dialog] .comment-send-btn')
    await check(browser, "[...document.querySelectorAll('[data-testid=comment-dialog] [data-testid=comment-item]')].some(item => "
                "item.querySelector('[data-testid=comment-text]')?.textContent === '<img src=x onerror=alert(1)>' && "
                "!item.querySelector('[data-testid=comment-text] img, [data-testid=comment-text] script, [data-testid=comment-text] b')) && "
                "document.querySelector('[data-testid=comment-dialog] .comment-input').value === ''",
                'dialog comments stay literal and reset input')
    await escape(browser)
    await check(browser, f"!document.querySelector('[data-testid=comment-dialog]') && "
                f"document.activeElement === document.querySelector('{post} [data-testid=post-comment-trigger]')",
                'comment dialog Escape restores its trigger focus')
    await browser.evaluate(f"document.querySelector('{post} .like-btn').focus()")
    await click(browser, post + ' .post-reaction-picker button[aria-label=Haha]')
    await check(browser, f"document.querySelector('{post} .like-btn').dataset.reaction === 'haha' && "
                f"document.querySelector('{post} .like-btn').dataset.likes === '1'", 'new posts support reactions')
    await click(browser, '#minimizeComposer')
    await click(browser, '#post1 .card-more-btn')
    await browser.evaluate("""(() => {
        const b = [...document.querySelectorAll('#dropdownMenu1 button, [role=menuitem]')]
            .find(b => /save post/i.test(b.textContent));
        if (!b) throw new Error('No Save Post action'); b.click();
    })()""")
    await go(browser, '/profile')
    await click(browser, '[data-action=profile-tab][data-value=saved]')
    await check(browser, "Boolean(document.querySelector('#profilePosts #post1'))", 'saved post appears in own profile')
    await click(browser, '[data-action=profile-tab][data-value=posts]')
    await composer(browser)
    await input_text(browser, '#shareTextInput', 'A post from my profile')
    await click(browser, '#sendPostBtn')
    await check(browser, "document.querySelector('#profilePosts .new-post :is([data-post-text],.post-text-content p)')?.textContent === 'A post from my profile'",
                'own profile publishes posts')
    await go(browser, '/')
    await click(browser, f'{post} .post-time')
    await check(browser, f"location.pathname.startsWith('/post/') && Boolean(document.querySelector('{post}'))",
                'newly published post has a working detail route')
    await go(browser, '/')
    await go(browser, '/post/1')
    await check(browser, "Boolean(document.querySelector('#postDetail #post1, .screen-content #post1'))", 'post detail renders original post')
    await go(browser, '/')
    await check(browser, f"document.querySelector('{post} .like-btn')?.dataset.reaction === 'haha' && "
                "document.querySelector('#likeBtn1').dataset.reaction === 'wow'", 'post state survives route changes')
    await click(browser, f'{post} .card-more-btn')
    await browser.evaluate("""(() => {
        const b=[...document.querySelectorAll('[role=menuitem]')].find(b=>/delete post/i.test(b.textContent));
        if(!b) throw new Error('No Delete Post action'); b.click();
    })()""")
    await check(browser, f"Boolean(document.querySelector({json.dumps(DIALOG)})) && "
                "/^delete post$/i.test(document.querySelector('[role=dialog] [data-testid=delete-post-confirm]')?.textContent.trim() || '')",
                'delete asks for confirmation')
    await click_dialog_button(browser, 'Cancel')
    await check(browser, f"Boolean(document.querySelector('{post}')) && "
                f"document.activeElement === document.querySelector('{post} [aria-label=\"More options\"]')",
                'Cancel keeps post and restores More options focus')
    await click(browser, f'{post} .card-more-btn')
    await browser.evaluate("""(() => {
        const b=[...document.querySelectorAll('[role=menuitem]')].find(b=>/delete post/i.test(b.textContent));
        if(!b) throw new Error('No Delete Post action'); b.click();
    })()""")
    await escape(browser)
    await check(browser, f"Boolean(document.querySelector('{post}')) && "
                f"!document.querySelector({json.dumps(DIALOG)}) && "
                f"document.activeElement === document.querySelector('{post} [aria-label=\"More options\"]')",
                'Escape keeps post and restores More options focus')
    await viewport(browser, 390, 844)
    await click(browser, f'{post} .card-more-btn')
    await browser.evaluate("""(() => {
        const b=[...document.querySelectorAll('[role=menuitem]')].find(b=>/delete post/i.test(b.textContent));
        if(!b) throw new Error('No Delete Post action'); b.click();
    })()""")
    await check(browser, """(() => {
        const dialog=document.querySelector('[role=dialog]');
        const actions=[...dialog.querySelectorAll('button')].filter(button=>
            ['Cancel','Delete Post'].includes(button.textContent.trim()));
        return actions.length===2 && actions.every(button=>{
            const rect=button.getBoundingClientRect();
            return rect.width>=44 && rect.height>=44;
        });
    })()""", 'mobile delete dialog actions meet 44px targets')
    await click(browser, '[data-testid=delete-post-confirm]')
    await check(browser, f"!document.querySelector('{post}') && document.activeElement.id === 'mainContent'",
                'confirm deletes post and focuses main content')


async def post_interactions(browser):
    """Comment sorting/state, share-to-message snapshots, image navigation and story surfaces."""
    await load(browser, '/')
    await theme(browser, 'light')
    await check(browser, """(() => {
        const post=document.querySelector('#post1');
        const actions=[...post.querySelectorAll('[data-testid=post-react], [data-testid=post-comment-trigger], [data-testid=share-post-trigger]')];
        return actions.length===3 && actions.map(button=>button.querySelector('.btn-text')?.textContent.trim().split(' (')[0]).join('|')==='React|Comment|Share' &&
            !post.querySelector('[data-testid=post-views], .post-views, .view-count');
    })()""", 'post footer exposes React, Comment, Share without views')

    comment_order = "JSON.stringify([...document.querySelectorAll('[data-testid=comment-dialog] [data-testid=comment-list] > [data-testid=comment-item]')].map(item=>item.dataset.commentAuthor))"
    async def expect_comment_order(names, label):
        await check(browser, f"{comment_order} === {json.dumps(json.dumps(names, separators=(',', ':')))}", label)

    comment_payload = '<img src=x onerror=alert(1)>'
    await browser.evaluate("document.querySelector('#post1 [data-testid=post-comment-trigger]').focus()")
    await click(browser, '#post1 [data-testid=post-comment-trigger]')
    await check(browser, "Boolean(document.querySelector('[data-testid=comment-dialog][role=dialog][aria-labelledby]')) && "
                "!document.querySelector('#post1 .comments-drawer')",
                'Comment opens a dialog without inline comments')
    await check(browser, "document.querySelector('[data-testid=comment-dialog] [data-comment-id=post-1-comment-1] [data-testid=comment-text]')?.textContent === "
                "'That view is incredible. The light over the valley looks unreal.'",
                'seeded comments render as typed person and body records')
    await check(browser, "!document.querySelector('.comment-likes') && document.querySelectorAll('.comment-reply-btn').length === 3", 'Reply replaces comment likes')
    await expect_comment_order(['Silena', 'Olya', 'Davis'], 'Most relevant breaks equal reply counts by timestamp')
    await poll(browser, "(() => {const el=document.querySelector('[data-testid=comment-dialog]');const style=el&&getComputedStyle(el);return style?.opacity==='1'&&style.transform==='none';})()", 'comment dialog settles before capture')
    await browser.capture(SCREENSHOTS / 'next_comments_dialog.png')
    await input_text(browser, '[data-testid=comment-dialog] .comment-input', 'draft survives comment sorting')
    await select_option_text(browser, '[data-testid=comment-sort]', 'Newest')
    await expect_comment_order(['Silena', 'Olya', 'Davis'], 'Newest sorts by timestamp and preserves equal-time order')
    await check(browser, "document.querySelector('[data-testid=comment-dialog] .comment-input').value === 'draft survives comment sorting'",
                'comment draft survives sort change')
    await select_option_text(browser, '[data-testid=comment-sort]', 'All comments')
    await expect_comment_order(['Silena', 'Davis', 'Olya'], 'All comments retains original order')
    await escape(browser)
    await check(browser, "!document.querySelector('[data-testid=comment-dialog]') && "
                "document.activeElement === document.querySelector('#post1 [data-testid=post-comment-trigger]')",
                'Escape closes comments and restores trigger focus')
    await click(browser, '#post1 [data-testid=post-comment-trigger]')
    await check(browser, "document.querySelector('[data-testid=comment-dialog] .comment-input').value === 'draft survives comment sorting'",
                'comment draft survives closing and reopening')
    await select_option_text(browser, '[data-testid=comment-sort]', 'Most relevant')
    await input_text(browser, '[data-testid=comment-dialog] .comment-input', comment_payload)
    await click(browser, '[data-testid=comment-dialog] .comment-send-btn')
    await check(browser, f"[...document.querySelectorAll('[data-testid=comment-dialog] [data-testid=comment-item]')].some(item => "
                f"item.dataset.commentAuthor === 'Bogdan Nikitin' && item.querySelector('[data-testid=comment-text]')?.textContent === {json.dumps(comment_payload)} && "
                "!item.querySelector('[data-testid=comment-text] img, [data-testid=comment-text] script, [data-testid=comment-text] b')) && "
                "document.querySelector('[data-testid=comment-dialog] .comment-input').value === ''",
                'new comment is attributed and literal, then clears the form')
    parent_id = 'post-1-comment-2'
    for text in ['First reply <b>literal</b>', 'A reply to that reply', 'Third level reply', 'Fourth level reply']:
        await click(browser, f'[data-comment-id="{parent_id}"] > .comment-bubble .comment-reply-btn')
        await check(browser, "Boolean(document.querySelector('.comment-reply-context strong')) && document.activeElement.classList.contains('comment-input') && document.querySelector('.comment-send-btn').disabled", 'Reply identifies the target and focuses an empty composer')
        await input_text(browser, '.comment-input', text)
        await click(browser, '.comment-send-btn')
        await check(browser, f"document.querySelector('[data-parent-id=\"{parent_id}\"] .comment-text')?.textContent === {json.dumps(text)} && !document.querySelector('[data-parent-id=\"{parent_id}\"] .comment-text b') && !document.querySelector('.comment-reply-context')", 'reply is literal and nested under the selected comment')
        parent_id = await browser.evaluate(f"document.querySelector('[data-parent-id=\"{parent_id}\"]').dataset.commentId")
    await check(browser, "document.querySelector('[data-testid=comment-list] > [data-testid=comment-item]').dataset.commentId === 'post-1-comment-2' && document.querySelector('#post1 .comment-btn .btn-text').textContent.includes('(8)')", 'Most relevant prioritizes active threads and total count includes replies')
    await viewport(browser, 320, 640)
    await click(browser, f'[data-comment-id="{parent_id}"] > .comment-bubble .comment-reply-btn')
    await input_text(browser, '.comment-input', 'draft kept when cancelling reply')
    await click(browser, '[aria-label="Cancel reply"]')
    await check(browser, "!document.querySelector('.comment-reply-context') && document.querySelector('.comment-input').value === 'draft kept when cancelling reply' && document.activeElement.classList.contains('comment-input')", 'Cancel reply restores top-level composer without losing text')
    await check(browser, """(() => {
        const dialog=document.querySelector('[data-testid=comment-dialog]'), r=dialog.getBoundingClientRect();
        return r.left>=0 && r.right<=innerWidth+1 && dialog.scrollWidth<=dialog.clientWidth+1 &&
            [...dialog.querySelectorAll('.comment-bubble')].every(el=>el.getBoundingClientRect().width>150);
    })()""", 'deep reply threads fit 320px without progressively narrowing')
    await browser.capture(SCREENSHOTS / 'next_nested_replies_mobile.png')
    await input_text(browser, '.comment-input', '')
    await viewport(browser, 1440, 900)
    await escape(browser)

    await go(browser, '/settings')
    await input_text(browser, '[name=name]', 'Linh Tran')
    await submit(browser, 'form[data-form=settings], .settings-form')
    await check(browser, "document.querySelector('[data-settings-status]')?.textContent.toLowerCase().includes('saved')",
                'profile rename is saved')
    await go(browser, '/')
    await click(browser, '#post1 [data-testid=post-comment-trigger]')
    await check(browser, f"[...document.querySelectorAll('[data-testid=comment-dialog] [data-testid=comment-item]')].some(item => "
                f"item.dataset.commentAuthor === 'Linh Tran' && item.querySelector('[data-testid=comment-text]')?.textContent === {json.dumps(comment_payload)})",
                'renaming self updates the comment author without changing its body')
    await check(browser, "[...document.querySelectorAll('[data-parent-id]')].length === 4 && [...document.querySelectorAll('[data-parent-id]')].every(el=>el.dataset.commentAuthor==='Linh Tran')", 'profile rename also updates all nested reply authors')
    await escape(browser)
    await click(browser, '#post1 .post-time')
    await check(browser, "location.pathname === '/post/1' && !document.querySelector('[data-testid=comment-dialog]') && "
                "!document.querySelector('#post1 .comments-drawer')",
                'post detail does not open comments automatically')
    await click(browser, '[data-testid=screen-content] [data-testid=post-comment-trigger]')
    await check(browser, f"[...document.querySelectorAll('[data-testid=comment-dialog] [data-testid=comment-item]')].some(item => "
                f"item.dataset.commentAuthor === 'Linh Tran' && item.querySelector('[data-testid=comment-text]')?.textContent === {json.dumps(comment_payload)})",
                'comment state is shared between Feed and Post detail')
    await check(browser, "document.querySelectorAll('[data-parent-id]').length === 4", 'nested replies are shared with Post detail')
    await escape(browser)
    await click(browser, '[data-testid=detail-back-link]')

    first_image = '#post1 .post-media'
    for width, height in [(320, 640), (1440, 900)]:
        await viewport(browser, width, height)
        await check(browser, """(() => {
            const row=document.querySelector('#post1 .post-photo-row'), items=[...row.querySelectorAll('.post-photo-thumbnail')];
            const rects=items.map(item=>item.getBoundingClientRect());
            return items.length===3 && rects.every(r=>Math.abs(r.top-rects[0].top)<1 && r.height===160 && r.width===200) &&
                items.every(item=> {
                    const image=item.querySelector('img');
                    return getComputedStyle(image).objectFit==='scale-down' && image.clientWidth===200 && image.clientHeight===160;
                }) &&
                document.documentElement.scrollWidth<=innerWidth;
        })()""", f'photos form one row of uncropped fixed-size thumbnails at {width}px')
        if width == 320:
            await browser.evaluate("document.querySelector('#post1 .post-photo-row .post-photo-thumbnail:last-child').focus()")
            await check(browser, """(() => {
                const row=document.querySelector('#post1 .post-photo-row'), last=row.lastElementChild.getBoundingClientRect(), r=row.getBoundingClientRect();
                return row.scrollLeft>0 && last.left>=r.left && last.right<=r.right+1;
            })()""", 'keyboard focus scrolls the last thumbnail into view on mobile')
            await browser.capture(SCREENSHOTS / 'next_photo_row_mobile.png')
    await browser.evaluate(f'document.querySelector({json.dumps(first_image)}).focus()')
    await click(browser, first_image)
    await check(browser, "document.querySelector('[role=dialog] .attachment-viewer img')?.alt === 'Valley view with campers' && "
                "document.querySelector('[data-testid=media-source-post]')?.getAttribute('href') === '/post/1'",
                'image preview keeps its source post context')
    await check(browser, "document.querySelector('[data-testid=media-previous-image]')?.disabled && "
                "!document.querySelector('[data-testid=media-next-image]')?.disabled",
                'first image has disabled Previous and enabled Next controls')
    await click(browser, '[data-testid=media-next-image]')
    await check(browser, "document.querySelector('[role=dialog] .attachment-viewer img')?.alt === 'Purple alpine flower' && "
                "document.activeElement === document.querySelector('[data-testid=media-next-image]')",
                'Next selects the adjacent image from the same post and keeps focus stable')
    await browser.send('Input.dispatchKeyEvent', {'type':'keyDown','key':'ArrowRight','code':'ArrowRight','windowsVirtualKeyCode':39})
    await browser.send('Input.dispatchKeyEvent', {'type':'keyUp','key':'ArrowRight','code':'ArrowRight','windowsVirtualKeyCode':39})
    await check(browser, "document.querySelector('[role=dialog] .attachment-viewer img')?.alt === 'Majestic snowy mountain peak' && "
                "document.querySelector('[data-testid=media-next-image]')?.disabled",
                'ArrowRight reaches the last image without wrapping')
    await browser.send('Input.dispatchKeyEvent', {'type':'keyDown','key':'ArrowRight','code':'ArrowRight','windowsVirtualKeyCode':39})
    await browser.send('Input.dispatchKeyEvent', {'type':'keyUp','key':'ArrowRight','code':'ArrowRight','windowsVirtualKeyCode':39})
    await check(browser, "document.querySelector('[role=dialog] .attachment-viewer img')?.alt === 'Majestic snowy mountain peak'",
                'ArrowRight stays bounded at the last image')
    await click(browser, '[data-testid=media-previous-image]')
    await check(browser, "document.querySelector('[role=dialog] .attachment-viewer img')?.alt === 'Purple alpine flower'",
                'Previous returns to the prior image')
    await browser.send('Input.dispatchKeyEvent', {'type':'keyDown','key':'ArrowLeft','code':'ArrowLeft','windowsVirtualKeyCode':37})
    await browser.send('Input.dispatchKeyEvent', {'type':'keyUp','key':'ArrowLeft','code':'ArrowLeft','windowsVirtualKeyCode':37})
    await check(browser, "document.querySelector('[role=dialog] .attachment-viewer img')?.alt === 'Valley view with campers' && "
                "document.querySelector('[data-testid=media-previous-image]')?.disabled",
                'ArrowLeft returns to the first image without wrapping')
    await escape(browser)
    await check(browser, f"!document.querySelector('[role=dialog]') && document.activeElement === document.querySelector({json.dumps(first_image)})",
                'image viewer Escape restores focus to the original thumbnail')

    await go(browser, '/stories')
    await click(browser, '[data-story=anatoly]')
    await poll(browser, "document.querySelector('#storyViewerName')?.textContent === 'Anatoly'", 'open story for surface check')
    await poll(browser, "document.querySelector('#storyViewerImage')?.complete && document.querySelector('#storyViewerImage')?.naturalWidth > 0", 'story image loads')
    await browser.evaluate("document.querySelector('.story-reply-input').focus()")
    await poll(browser, "(() => {const dialog=document.querySelector('.story-dialog'),image=document.querySelector('#storyViewerImage');return dialog&&image&&Number(getComputedStyle(dialog).opacity)>.999&&Number(getComputedStyle(image).opacity)>.999&&[...dialog.getAnimations(),...image.getAnimations()].every(animation=>animation.playState!=='running');})()", 'desktop story settles before capture')
    await check(browser, r"""(() => {
        const alpha=color=>{
            if(color==='transparent') return 0;
            const match=color.match(/rgba?\([^)]*[,/]\s*([\d.]+)\s*\)$/);
            return match ? Number(match[1]) : 1;
        };
        const dialog=document.querySelector('.story-dialog'), stage=document.querySelector('.story-viewer-stage');
        const box=document.querySelector('.story-media-box');
        const image=document.querySelector('#storyViewerImage'), bar=document.querySelector('.story-reply-bar');
        const input=document.querySelector('.story-reply-input');
        if(!dialog||!stage||!box||!image||!bar||!input) return false;
        const a=box.getBoundingClientRect(), b=image.getBoundingClientRect();
        const dialogStyle=getComputedStyle(dialog), stageStyle=getComputedStyle(stage);
        const barStyle=getComputedStyle(bar), inputStyle=getComputedStyle(input);
        return dialogStyle.borderTopWidth==='0px' && alpha(dialogStyle.backgroundColor)<.05 &&
            alpha(stageStyle.backgroundColor)<.05 && stageStyle.boxShadow==='none' &&
            alpha(barStyle.backgroundColor)<.05 && barStyle.paddingTop==='0px' &&
            getComputedStyle(image).objectFit==='cover' && Math.abs(a.width-b.width)<1 && Math.abs(a.height-b.height)<1 &&
            alpha(inputStyle.backgroundColor)>.05 && alpha(inputStyle.backgroundColor)<1 && inputStyle.color==='rgb(255, 255, 255)';
    })()""", 'story image fills a clear stage and reply controls have no opaque frame')
    await browser.capture(SCREENSHOTS / 'next_story_desktop_light.png')
    await escape(browser)
    await viewport(browser, 390, 844)
    await theme(browser, 'dark')
    await go(browser, '/stories')
    await click(browser, '[data-story=anatoly]')
    await poll(browser, "document.querySelector('#storyViewerName')?.textContent === 'Anatoly'", 'open mobile story for surface capture')
    await poll(browser, "document.querySelector('#storyViewerImage')?.complete && document.querySelector('#storyViewerImage')?.naturalWidth > 0", 'mobile story image loads')
    await browser.evaluate("document.querySelector('.story-reply-input').focus()")
    await poll(browser, "(() => {const dialog=document.querySelector('.story-dialog'),image=document.querySelector('#storyViewerImage');return dialog&&image&&Number(getComputedStyle(dialog).opacity)>.999&&Number(getComputedStyle(image).opacity)>.999&&[...dialog.getAnimations(),...image.getAnimations()].every(animation=>animation.playState!=='running');})()", 'mobile story settles before capture')
    await browser.capture(SCREENSHOTS / 'next_story_mobile_dark.png')
    await escape(browser)

    await go(browser, '/')
    await viewport(browser, 1440, 900)
    await theme(browser, 'light')
    await composer(browser)
    await input_text(browser, '#shareTextInput', 'Shared snapshot body')
    await click(browser, '#sendPostBtn')
    post_id = await browser.evaluate("document.querySelector('#feedStream article.new-post')?.id")
    assert post_id, 'UI-created post is required for share regressions'
    post = '#' + post_id
    share_trigger = post + ' [data-testid=share-post-trigger]'
    expected_href = '/post/' + post_id.removeprefix('post')

    await click(browser, share_trigger)
    await check(browser, "Boolean(document.querySelector('.share-post-popover')) && "
                "document.querySelector('[data-testid=share-post-submit]')?.disabled",
                'Share opens a recipient picker with Send disabled until selection')
    await poll(browser, "(() => {const el=document.querySelector('.share-post-popover');const style=el&&getComputedStyle(el);return style?.opacity==='1'&&style.transform==='none';})()", 'share picker settles before capture')
    await browser.capture(SCREENSHOTS / 'next_share_picker.png')
    await click(browser, '.share-post-popover [data-share-recipient="George Lobko"]')
    await click(browser, '[data-testid=share-post-cancel]')
    await check(browser, "location.pathname === '/' && !document.querySelector('.share-post-popover') && "
                "!document.querySelector('[data-testid=share-flight]')",
                'Cancel closes the picker without sending or navigating')
    await click(browser, '[data-share-target=messages]')
    await check(browser, "location.pathname === '/messages'", 'Messages opens after canceled share')
    await go(browser, '/messages/George%20Lobko')
    await check(browser, "!document.querySelector('[data-testid=shared-post-message]')",
                'Cancel does not send a shared-post message')
    await go(browser, '/')
    await click(browser, share_trigger)
    await click(browser, '.share-post-popover [data-share-recipient="Olya"]')
    await click(browser, 'h1')
    await check(browser, "location.pathname === '/' && !document.querySelector('.share-post-popover') && "
                "!document.querySelector('[data-testid=share-flight]')",
                'outside click cancels sharing without navigating')

    async def start_share_flight_sample():
        await browser.evaluate("""(() => {
            const submit=document.querySelector('[data-testid="share-post-submit"]');
            const target=[...document.querySelectorAll('[data-share-target="messages"]')]
                .find(node=>node.getClientRects().length && getComputedStyle(node).visibility!=='hidden');
            if(!submit||!target) throw new Error('Share flight endpoints are not visible');
            const center=el=>{const r=el.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2};};
            const sample={start:center(submit),end:center(target),frames:[],done:false};
            globalThis.__postShareFlight=sample;
            const started=performance.now();
            const frame=now=>{
                const plane=document.querySelector('[data-testid="share-flight"]');
                if(plane){const p=center(plane);sample.frames.push({x:p.x,y:p.y});}
                if(now-started>=950){sample.done=true;return;}
                requestAnimationFrame(frame);
            };
            requestAnimationFrame(frame);
        })()""")

    async def assert_share_flight(label):
        await poll(browser, 'globalThis.__postShareFlight?.done===true', f'{label} sample completes', timeout=2)
        await check(browser, """(() => {
            const sample=globalThis.__postShareFlight, first=sample?.frames[0], last=sample?.frames.at(-1);
            const distance=(a,b)=>Math.hypot(a.x-b.x,a.y-b.y);
            return sample && sample.frames.length>=5 && first && last &&
                distance(first,sample.start)<35 && distance(last,sample.end)<35 &&
                sample.frames.some(frame=>distance(frame,first)>80) &&
                !document.querySelector('[data-testid=share-flight]');
        })()""", label)

    await click(browser, share_trigger)
    await click(browser, '.share-post-popover [data-share-recipient="George Lobko"]')
    await click(browser, '.share-post-popover [data-share-recipient="Olya"]')
    await start_share_flight_sample()
    await click(browser, '[data-testid=share-post-submit]')
    await check(browser, "location.pathname === '/' && !document.querySelector('.share-post-popover')",
                'successful share stays on the source route')
    await assert_share_flight('desktop paper plane travels to Messages and clears')
    await click(browser, '[data-share-target=messages]')
    await check(browser, "location.pathname === '/messages'", 'Messages navigation remains usable after the flight')
    await go(browser, '/messages/George%20Lobko')
    await check(browser, f"document.querySelectorAll('[data-testid=shared-post-message]').length === 1 && "
                f"document.querySelector('[data-testid=shared-post-message]')?.textContent.includes('Linh Tran') && "
                f"document.querySelector('[data-testid=shared-post-message]')?.textContent.includes('Shared snapshot body') && "
                f"document.querySelector('[data-testid=shared-post-link]')?.getAttribute('href') === {json.dumps(expected_href)}",
                'selected friend receives exactly one linked author/text snapshot')
    await click(browser, '[data-testid=shared-post-link]')
    await check(browser, f"location.pathname === {json.dumps(expected_href)} && "
                "document.querySelector('[data-testid=screen-content]')?.textContent.includes('Shared snapshot body')",
                'shared-post link opens the source post')
    await click(browser, '[data-testid=detail-back-link]')
    await go(browser, '/messages/Olya')
    await check(browser, "document.querySelectorAll('[data-testid=shared-post-message]').length === 1 && "
                "document.querySelector('[data-testid=shared-post-link]')?.getAttribute('href') === "
                + json.dumps(expected_href), 'each selected friend receives exactly one message')

    await viewport(browser, 390, 844)
    await go(browser, '/')
    await click(browser, share_trigger)
    await click(browser, '.share-post-popover [data-share-recipient="Vitaliy Boyko"]')
    await start_share_flight_sample()
    await click(browser, '[data-testid=share-post-submit]')
    await assert_share_flight('mobile paper plane travels to Messages and clears')
    await click(browser, '[data-share-target=messages]')
    await check(browser, "location.pathname === '/messages'", 'mobile Messages navigation remains usable')
    await go(browser, '/messages/Vitaliy%20Boyko')
    await check(browser, "document.querySelectorAll('[data-testid=shared-post-message]').length === 1 && "
                "document.querySelector('[data-testid=shared-post-link]')?.getAttribute('href') === "
                + json.dumps(expected_href), 'mobile share delivers one linked snapshot')

    await viewport(browser, 1440, 900)
    await go(browser, '/')
    await click(browser, post + ' [aria-label="More options"]')
    await browser.evaluate("[...document.querySelectorAll('[role=menuitem]')].find(item=>/delete post/i.test(item.textContent))?.click()")
    await click(browser, '[data-testid=delete-post-confirm]')
    await check(browser, f"!document.querySelector('{post}')", 'source post can be deleted after sharing')
    await click(browser, '[data-share-target=messages]')
    await go(browser, '/messages/George%20Lobko')
    await check(browser, "document.querySelector('[data-testid=shared-post-message]')?.textContent.includes('Shared snapshot body') && "
                "document.querySelector('[data-testid=shared-post-link]')?.getAttribute('href') === "
                + json.dumps(expected_href), 'shared snapshot remains after source deletion')
    await click(browser, '[data-testid=shared-post-link]')
    await check(browser, f"location.pathname === {json.dumps(expected_href)} && "
                "document.querySelector('.empty-state')?.textContent.includes('Post not found')",
                'stale shared link reaches the post-not-found recovery state')


async def locations_and_emoji(browser):
    """2000ms resettable accent-insensitive location search; 745 emoji and caret."""
    await viewport(browser, 390, 844)
    await composer(browser)
    await click(browser, '#locationToolBtn')
    await check(browser, "document.querySelectorAll('.location-option').length === 16", 'location catalog has 16 places')
    await input_text(browser, '#locationSearch', 'Hanoi')
    await asyncio.sleep(1.1)  # Timer under test: type again before the 2000ms deadline.
    await input_text(browser, '#locationSearch', 'Đà Nẵng')
    await asyncio.sleep(1.1)  # Past the first deadline, before the reset deadline.
    await check(browser, "document.querySelectorAll('.location-option').length === 16 && "
                "document.querySelector('#locationResults').getAttribute('aria-busy') === 'true'",
                'typing resets 2000ms debounce', timeout=.2)
    await check(browser, "document.querySelectorAll('.location-option').length === 1 && "
                "document.querySelector('.location-option strong')?.textContent === 'Da Nang' && "
                "document.querySelector('#locationResults').getAttribute('aria-busy') !== 'true'",
                'accented query resolves after latest debounce', timeout=2)
    await browser.capture(SCREENSHOTS / 'next_location_mobile.png')
    await input_text(browser, '#locationSearch', 'no such city')
    await check(browser, "!document.querySelector('.location-option') && "
                "document.querySelector('#locationStatus')?.textContent.includes('No places')",
                'location no-results feedback', timeout=3)
    await input_text(browser, '#locationSearch', 'saigon')
    await poll(browser, "document.querySelector('.location-option strong')?.textContent === 'Ho Chi Minh City'",
               'location alias matches', timeout=3)
    await click(browser, '.location-option')
    await check(browser, f"!document.querySelector({json.dumps(DIALOG)}) && "
                "document.querySelector('[data-type=location]')?.textContent.includes('Ho Chi Minh City')",
                'location selection attaches to draft')
    await click(browser, '[data-type=location] .preview-remove-btn')
    await check(browser, "!document.querySelector('[data-type=location]')", 'location removal clears draft')
    await click(browser, '#locationToolBtn')
    await input_text(browser, '#locationSearch', 'Hanoi')
    await close_dialog(browser)
    await click(browser, '#locationToolBtn')
    await check(browser, "document.querySelector('#locationSearch').value === '' && "
                "document.querySelectorAll('.location-option').length === 16", 'reopened location picker resets query')
    await asyncio.sleep(2.1)  # Timer under test: cancelled search must not mutate reopened picker.
    await check(browser, "document.querySelectorAll('.location-option').length === 16", 'closing picker cancels pending search')
    await close_dialog(browser)
    await input_text(browser, '#shareTextInput', 'AB')
    await browser.evaluate("document.querySelector('#shareTextInput').focus(); document.querySelector('#shareTextInput').setSelectionRange(1,1)")
    await click(browser, '#emojiPickerBtn')
    await check(browser, "document.querySelectorAll('.emoji-categories button').length === 9 && "
                "parseInt(document.querySelector('.emoji-heading span')?.textContent) === 745", 'nine groups contain 745 emoji')
    await input_text(browser, '.emoji-search', 'pizza')
    await check(browser, "document.querySelectorAll('.emoji-option').length === 1 && "
                "document.querySelector('.emoji-option').dataset.value === '🍕'", 'emoji search spans groups')
    await click(browser, '.emoji-option')
    await check(browser, "document.querySelector('#shareTextInput').value === 'A🍕B' && "
                "!document.querySelector('#emojiMenu')?.getClientRects().length", 'emoji inserts at saved caret')
    await click(browser, '#emojiPickerBtn')
    await input_text(browser, '.emoji-search', 'not-a-real-emoji-xyz')
    await check(browser, "!document.querySelector('.emoji-option') && document.querySelector('.emoji-empty')",
                'emoji search no-results state')
    await click(browser, '[data-action=emoji-group][data-value=faces]')
    await check(browser, "document.querySelector('.emoji-search').value === '' && document.querySelector('.emoji-option')",
                'emoji category clears search')
    await escape(browser)
    for value in ('light', 'dark'):
        await theme(browser, value)
        await viewport(browser, 320, 640)
        await click(browser, '#emojiPickerBtn')
        await check(browser, "(() => {const r=document.querySelector('#emojiMenu').getBoundingClientRect(); "
                    "return r.top>=0 && r.left>=0 && r.right<=innerWidth+1 && r.bottom<=innerHeight+1 && "
                    "document.documentElement.scrollWidth<=innerWidth;})()", f'emoji palette fits 320px {value}')
        await browser.capture(SCREENSHOTS / f'next_emoji_320_{value}.png')
        await browser.evaluate("document.querySelector('.emoji-search').focus()")
        await escape(browser)
    await check(browser, "!document.querySelector('#emojiMenu')?.getClientRects().length && "
                "document.activeElement.id === 'emojiPickerBtn'", 'emoji Escape restores trigger focus')


async def attach(browser, selector, file_expression):
    """Feed actual File bytes through the public input/change boundary."""
    await browser.evaluate(f'''(async () => {{
        const file = await ({file_expression});
        const transfer = new DataTransfer(); transfer.items.add(file);
        const input = document.querySelector({json.dumps(selector)});
        input.files = transfer.files; input.dispatchEvent(new Event('change', {{bubbles:true}}));
    }})()''')


async def attachments(browser):
    """Real PNG/WebM decoding, controls/playback/publish, documents and validation."""
    await viewport(browser, 390, 844)
    await composer(browser)
    await attach(browser, '#imageAttachmentInput', '''new Promise(resolve => {
        const canvas = document.createElement('canvas'); canvas.width=80; canvas.height=60;
        const ctx=canvas.getContext('2d'); ctx.fillStyle='#ae8ad8'; ctx.fillRect(0,0,80,60);
        canvas.toBlob(blob => resolve(new File([blob], 'next-preview.png', {type:'image/png'})), 'image/png');
    })''')
    await click(browser, '[data-type=image] .preview-open-btn')
    await check(browser, "document.querySelector('.attachment-viewer img')?.naturalWidth === 80",
                'PNG preview decodes actual 80px image')
    await browser.capture(SCREENSHOTS / 'next_image_preview_mobile.png')
    await close_dialog(browser)
    await input_text(browser, '#shareTextInput', 'PNG publish')
    await click(browser, '#sendPostBtn')
    await check(browser, "document.querySelector('#feedStream .new-post .post-media img')?.naturalWidth === 80 && "
                "!document.querySelector('#attachmentPreviewBar')?.children.length", 'PNG publishes and clears preview')
    await check(browser, """(() => {
        const image=document.querySelector('#feedStream .new-post .post-media img');
        const rect=image?.getBoundingClientRect();
        return rect?.height===160 && rect.width===200 && getComputedStyle(image).objectFit==='scale-down' &&
            image.naturalWidth===80 && image.naturalHeight===60;
    })()""", 'small uploaded image stays original size inside a fixed thumbnail without cropping or upscaling')
    await attach(browser, '#imageAttachmentInput', '''(async () => {
        const canvas=document.createElement('canvas'); canvas.width=160; canvas.height=90;
        const ctx=canvas.getContext('2d'), stream=canvas.captureStream(15), chunks=[];
        const recorder=new MediaRecorder(stream, {mimeType:'video/webm'});
        recorder.ondataavailable=e=>chunks.push(e.data);
        const done=new Promise(resolve=>recorder.onstop=resolve);
        recorder.start();
        const draw=setInterval(()=>{ctx.fillStyle='#705993';ctx.fillRect(0,0,160,90);
            ctx.fillStyle='white';ctx.fillText('Next video',20,45);},50);
        try {
            await new Promise(resolve=>setTimeout(resolve,450)); recorder.stop(); await done;
            return new File(chunks,'next-preview.webm',{type:'video/webm'});
        } finally {clearInterval(draw);stream.getTracks().forEach(track=>track.stop());}
    })()''')
    await click(browser, '[data-type=image] .preview-open-btn')
    await check(browser, "document.querySelector('.attachment-viewer video')?.videoWidth === 160 && "
                "document.querySelector('.attachment-viewer video').controls && "
                "!document.querySelector('.attachment-viewer video').error", 'WebM preview decodes and exposes controls')
    await browser.evaluate("""(async () => {
        const v=document.querySelector('.attachment-viewer video'); v.muted=true; v.loop=true;
        await v.play();
    })()""")
    await check(browser, "!document.querySelector('.attachment-viewer video').paused && "
                "document.querySelector('.attachment-viewer video').currentTime > 0", 'WebM preview actually plays')
    # Keep a DOM reference only to inspect cleanup after shadcn unmounts the dialog.
    await browser.evaluate("void (globalThis.__nextTestVideo = document.querySelector('.attachment-viewer video'))")
    await close_dialog(browser)
    await check(browser, 'globalThis.__nextTestVideo.paused', 'closing WebM preview pauses playback')
    await browser.evaluate('delete globalThis.__nextTestVideo')
    await click(browser, '#locationToolBtn')
    await input_text(browser, '#locationSearch', 'saigon')
    await poll(browser, "document.querySelector('.location-option strong')?.textContent === 'Ho Chi Minh City'",
               'publish location', timeout=3)
    await click(browser, '.location-option')
    await input_text(browser, '#shareTextInput', 'WebM and location')
    await check(browser, """(() => {
        const button=document.querySelector('#sendPostBtn');
        if(!button) return false;
        const rect=button.getBoundingClientRect();
        const hit=document.elementFromPoint(rect.left+rect.width/2,rect.top+rect.height/2);
        return rect.width>0 && rect.height>0 && (hit===button || button.contains(hit));
    })()""", 'mobile Send button is the actual pointer hit target')
    posts_before = await browser.evaluate("document.querySelectorAll('#feedStream .new-post').length")
    await click(browser, '#sendPostBtn')
    await check(browser, f"document.querySelectorAll('#feedStream .new-post').length === {posts_before+1} && "
                "!document.querySelector('#attachmentPreviewBar')?.children.length && "
                "document.querySelector('#shareTextInput')?.value === ''",
                'WebM/location publish creates a post and clears the draft')
    await check(browser, """(() => {
        const card=[...document.querySelectorAll('#feedStream .new-post')]
            .find(item=>item.querySelector('video'));
        const video=card?.querySelector('video');
        return video?.videoWidth === 160 && video.controls && !video.error &&
            card.querySelector('.post-location')?.textContent.includes('Ho Chi Minh City');
    })()""", 'published WebM keeps controls and the selected location')
    await browser.capture(SCREENSHOTS / 'next_video_published_mobile.png')
    filename = '<b>notes</b>.txt'
    file_js = f"new File(['local file contents'], {json.dumps(filename)}, {{type:'text/plain'}})"
    await attach(browser, '#fileAttachmentInput', file_js)
    await check(browser, "document.querySelector('[data-type=file]')?.textContent.includes('<b>notes</b>.txt') && "
                "!document.querySelector('[data-type=file] b')", 'document filename is literal in preview')
    await click(browser, '[data-type=file] .preview-remove-btn')
    await check(browser, "document.querySelector('#fileAttachmentInput').value === '' && "
                "!document.querySelector('[data-type=file]')", 'document removal resets input')
    await attach(browser, '#fileAttachmentInput', file_js)
    await input_text(browser, '#shareTextInput', '')
    await click(browser, '#sendPostBtn')
    await check(browser, "document.querySelector('#feedStream .new-post :is(.post-file-link,.post-file)')?.download === '<b>notes</b>.txt' && "
                "document.querySelector('#feedStream .new-post :is(.post-file-link,.post-file)').href.startsWith('blob:') && "
                "!document.querySelector('#feedStream .new-post :is(.post-file-link,.post-file) b') && "
                "!document.querySelector('#attachmentPreviewBar')?.children.length", 'attachment-only document post preserves download')
    await attach(browser, '#imageAttachmentInput', "new File(['invalid'], 'invalid.txt', {type:'text/plain'})")
    await check(browser, "document.querySelector('#imageAttachmentInput').value === '' && "
                "!document.querySelector('[data-type=image]')", 'media input rejects non-media file')
    for width, height in [(240, 720), (960, 240), (512, 512)]:
        await attach(browser, '#imageAttachmentInput', f'''new Promise(resolve => {{
            const canvas=document.createElement('canvas'); canvas.width={width}; canvas.height={height};
            const ctx=canvas.getContext('2d'); ctx.fillStyle='#ae8ad8'; ctx.fillRect(0,0,{width},{height});
            ctx.fillStyle='#f4ce83'; ctx.fillRect(0,0,{width},20); ctx.fillRect(0,{height}-20,{width},20);
            canvas.toBlob(blob=>resolve(new File([blob], 'ratio-{width}-{height}.png', {{type:'image/png'}})), 'image/png');
        }})''')
        await input_text(browser, '#shareTextInput', f'Original ratio {width}x{height}')
        await click(browser, '#sendPostBtn')
    for width, height in [(320, 640), (390, 844), (1440, 900)]:
        await viewport(browser, width, height)
        await check(browser, """(() => {
            const images=[...document.querySelectorAll('#feedStream .new-post .post-media img')].slice(0,3);
            return images.length===3 && images.every(image=> {
                const r=image.getBoundingClientRect();
                return image.naturalWidth>0 && getComputedStyle(image).objectFit==='scale-down' && r.width===200 && r.height===160 &&
                    image.parentElement.clientHeight>=r.height-1 && r.left>=0 && r.right<=innerWidth+1;
            }) && document.documentElement.scrollWidth<=innerWidth;
        })()""", f'portrait, landscape and square uploads use uncropped fixed thumbnails at {width}px')
    await browser.capture(SCREENSHOTS / 'next_thumbnail_uploads.png')
    await click(browser, '#feedStream .new-post .post-time')
    await check(browser, "location.pathname.startsWith('/post/') && document.querySelector('[data-testid=screen-content] .post-media img')?.naturalWidth === 512", 'uploaded image remains available in Post detail')


async def motion_and_lifecycle(browser):
    """Motion/reduced motion, file size boundaries, URL ownership and copy fallback."""
    await browser.send('Emulation.setEmulatedMedia', {'features':[{'name':'prefers-reduced-motion','value':'no-preference'}]})
    await load(browser, '/')
    await poll(browser, "Math.abs(document.querySelector('#shareDock').getBoundingClientRect().width-60)<1", 'initial morph finished')
    await click(browser, '#composerToggle')
    await check(browser, "(() => {const w=document.querySelector('#shareDock').getBoundingClientRect().width;return w>60 && w<559;})()", 'composer morph has intermediate geometry', timeout=.2)
    await check(browser, "Math.abs(document.querySelector('#shareDock').getBoundingClientRect().width-560)<1", 'composer morph reaches expanded geometry')
    await input_text(browser, '#shareTextInput', 'Motion draft')
    await click(browser, '#minimizeComposer')
    await check(browser, "Math.abs(document.querySelector('#shareDock').getBoundingClientRect().width-60)<1", 'composer morph returns to circle')
    await browser.send('Emulation.setEmulatedMedia', {'features':[{'name':'prefers-reduced-motion','value':'reduce'}]})
    await load(browser, '/')
    await click(browser, '#composerToggle')
    await check(browser, "(() => {const w=document.querySelector('#shareDock').getBoundingClientRect().width;return w>60 && w<559;})()",
                'reduced-motion preference keeps composer animated', timeout=.2)
    await check(browser, "Math.abs(document.querySelector('#shareDock').getBoundingClientRect().width-560)<1",
                'composer reaches expanded geometry with reduced-motion emulation')
    for kind, size in [('image/png', 10*1024*1024+1), ('video/webm', 100*1024*1024+1)]:
        await attach(browser, '#imageAttachmentInput', f"Object.defineProperty(new File(['small fixture'], 'oversize', {{type:{json.dumps(kind)}}}), 'size', {{value:{size}}})")
        await check(browser, "!document.querySelector('.preview-pill') && document.querySelector('.toast')?.textContent.includes('under 10 MB')", f'reject {kind} over size limit')
    await browser.evaluate("""(() => {
        const revoke=URL.revokeObjectURL.bind(URL); globalThis.__revoked=[];
        URL.revokeObjectURL=url=>{globalThis.__revoked.push(url);revoke(url);};
    })()""")
    await attach(browser, '#fileAttachmentInput', "new File(['local'], 'ownership.txt', {type:'text/plain'})")
    url = await browser.evaluate("document.querySelector('.preview-open-btn').href")
    await click(browser, '.preview-remove-btn')
    await check(browser, f"globalThis.__revoked.includes({json.dumps(url)})", 'removing draft revokes object URL')
    await attach(browser, '#fileAttachmentInput', "new File(['local'], 'published.txt', {type:'text/plain'})")
    url = await browser.evaluate("document.querySelector('.preview-open-btn').href")
    await click(browser, '#sendPostBtn')
    await check(browser, f"!globalThis.__revoked.includes({json.dumps(url)}) && document.querySelector('.new-post .post-file')?.href === {json.dumps(url)}", 'publishing transfers URL ownership to post')
    await click(browser, '#minimizeComposer')
    await click(browser, '.new-post .card-more-btn')
    await click(browser, '[role=menuitem]:last-child')
    await check(browser, f"Boolean(document.querySelector({json.dumps(DIALOG)})) && "
                f"!globalThis.__revoked.includes({json.dumps(url)})", 'delete confirmation retains attachment URL')
    await click_dialog_button(browser, 'Cancel')
    await check(browser, f"document.querySelector('.new-post .post-file')?.href === {json.dumps(url)} && "
                f"!globalThis.__revoked.includes({json.dumps(url)}) && "
                "document.activeElement === document.querySelector('.new-post [aria-label=\"More options\"]')",
                'cancel preserves post, attachment URL and More options focus')
    await click(browser, '.new-post .card-more-btn')
    await click(browser, '[role=menuitem]:last-child')
    await click(browser, '[data-testid=delete-post-confirm]')
    await check(browser, f"globalThis.__revoked.includes({json.dumps(url)}) && !document.querySelector('.new-post')",
                'confirm deletes post and releases attachment URL')
    await check(browser, "document.activeElement.id === 'mainContent'", 'confirmed deletion restores focus to main content')
    await browser.evaluate("Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:()=>Promise.reject(new DOMException('Blocked','NotAllowedError'))}})")
    await click(browser, '#post1 .card-more-btn')
    await click(browser, '[role=menuitem]:first-child')
    await check(browser, "document.querySelector('[aria-label=\"Post link\"]')?.readOnly && document.querySelector('[aria-label=\"Post link\"]').value.endsWith('/post/1')", 'clipboard failure provides selectable direct link')
    await close_dialog(browser)

    await viewport(browser, 320, 640)
    await browser.evaluate("document.querySelector('#likeBtn1').scrollIntoView({block:'center'}); document.querySelector('#likeBtn1').focus()")
    await check(browser, "(() => {const r=document.querySelector('#post1 .post-reaction-picker')?.getBoundingClientRect(),like=document.querySelector('#likeBtn1').getBoundingClientRect();return r && r.left>=0 && r.right<=innerWidth && r.bottom<=like.top;})()", 'reaction palette stays above Like within 320px')


async def stories(browser):
    """Stories: early close, focus/pause, 5000ms completion, heart and message reply."""
    await go(browser, '/friends')
    await go(browser, '/profile/George%20Lobko')
    await click(browser, '[data-action=profile-tab][data-value=photos]')
    await browser.evaluate("document.querySelector('.media-tile').focus()")
    await click(browser, '.media-tile')
    await check(browser, f"Boolean(document.querySelector({json.dumps(DIALOG)})) && "
                "document.activeElement.tagName === 'BUTTON'", 'media lightbox opens with focus inside dialog')
    await escape(browser)
    await check(browser, f"!document.querySelector({json.dumps(DIALOG)}) && "
                "document.activeElement.classList.contains('media-tile')", 'lightbox Escape restores tile focus')
    await go(browser, '/stories')
    story = '[data-story=anatoly]'
    await browser.evaluate(f'document.querySelector({json.dumps(story)}).focus()')
    await click(browser, story)
    await poll(browser, "document.querySelector('#storyViewerName')?.textContent === 'Anatoly'", 'Anatoly story opens')
    await close_dialog(browser)
    await check(browser, "document.querySelector('[data-story=anatoly]').classList.contains('story-unseen')",
                'early close keeps story unseen')
    await click(browser, story)
    await browser.send('Input.dispatchKeyEvent', {'type':'keyDown','key':'ArrowRight','code':'ArrowRight','windowsVirtualKeyCode':39})
    await check(browser, "document.querySelector('#storyViewerName')?.textContent === 'Lolita Earns'", 'arrow key advances story')
    await browser.send('Input.dispatchKeyEvent', {'type':'keyDown','key':'ArrowLeft','code':'ArrowLeft','windowsVirtualKeyCode':37})
    await check(browser, "document.querySelector('#storyViewerName')?.textContent === 'Anatoly' && document.querySelector('[data-story=anatoly]').classList.contains('story-unseen')", 'previous story and manual skip preserve unseen state')
    await close_dialog(browser)
    await viewport(browser, 390, 844)
    await click(browser, story)
    box = await browser.evaluate("document.querySelector('#storyViewerImage').getBoundingClientRect().toJSON()")
    x, y = box['x'] + box['width']/2, box['y'] + box['height']/2
    await browser.send('Input.dispatchTouchEvent', {'type':'touchStart','touchPoints':[{'x':x+60,'y':y}]})
    await browser.send('Input.dispatchTouchEvent', {'type':'touchMove','touchPoints':[{'x':x-50,'y':y}]})
    await browser.evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))')
    await browser.send('Input.dispatchTouchEvent', {'type':'touchMove','touchPoints':[{'x':x-80,'y':y}]})
    await browser.send('Input.dispatchTouchEvent', {'type':'touchEnd','touchPoints':[]})
    await check(browser, "document.querySelector('#storyViewerName')?.textContent === 'Lolita Earns'", 'touch swipe advances story')
    await close_dialog(browser)
    await viewport(browser, 1440, 900)
    await click(browser, story)
    await click(browser, '.story-heart-btn')
    await check(browser, "document.querySelector('.story-heart-btn').getAttribute('aria-pressed') === 'true'",
                'story heart toggles on')
    await close_dialog(browser)
    await click(browser, story)
    await check(browser, "document.querySelector('.story-heart-btn').getAttribute('aria-pressed') === 'true'",
                'story heart survives reopening')
    await click(browser, '.story-heart-btn')
    await check(browser, "document.querySelector('.story-heart-btn').getAttribute('aria-pressed') === 'false'",
                'story heart toggles off')
    await browser.evaluate("document.querySelector('.story-reply-input').focus()")
    # Next animates scaleX; the legacy viewer animated width. Inspect the visible bar.
    progress = await browser.evaluate("getComputedStyle(document.querySelector('.story-progress-fill')).transform")
    await asyncio.sleep(5.2)  # Timer under test: focused reply pauses past a whole 5000ms story.
    await check(browser, f"document.querySelector('#storyViewerName')?.textContent === 'Anatoly' && "
                f"getComputedStyle(document.querySelector('.story-progress-fill')).transform === {json.dumps(progress)} && "
                "document.querySelector('[data-story=anatoly]').classList.contains('story-unseen')",
                'reply focus pauses progress and completion beyond 5000ms')
    await input_text(browser, '.story-reply-input', '<b>See you on the trail</b>')
    await click(browser, '.story-send-btn')
    await check(browser, "document.querySelector('.story-reply-input').value === ''", 'story reply sends and clears')
    await close_dialog(browser)
    await go(browser, '/messages')
    await go(browser, '/messages/Anatoly')
    await check(browser, "document.querySelector('.chat-history')?.textContent.includes('<b>See you on the trail</b>') && "
                "!document.querySelector('.chat-history p b')", 'story reply appears as literal conversation message')
    await go(browser, '/stories')
    await click(browser, story)
    await poll(browser, "document.querySelector('#storyViewerName')?.textContent === 'Anatoly'", 'reopen incomplete story')
    await browser.evaluate("document.querySelector('.story-reply-input').blur(); "
                           "document.querySelector('.story-heart-btn').focus()")
    started = time.monotonic()
    await asyncio.sleep(4.0)  # Timer under test: completion must not happen early.
    await check(browser, "document.querySelector('#storyViewerName')?.textContent === 'Anatoly' && "
                "document.querySelector('[data-story=anatoly]').classList.contains('story-unseen')",
                'story remains unseen before 5000ms', timeout=.2)
    await poll(browser, "document.querySelector('#storyViewerName')?.textContent === 'Lolita Earns'",
               '5000ms story automatically advances', timeout=2.5)
    elapsed = time.monotonic() - started
    assert 4.7 <= elapsed <= 6.5, f'Story completion timing: {elapsed:.2f}s'
    await check(browser, "!document.querySelector('[data-story=anatoly]').classList.contains('story-unseen') && "
                "document.querySelector('[data-story=lolita]').classList.contains('story-unseen')",
                'only completed story loses unseen outline')
    await poll(browser, f'!document.querySelector({json.dumps(DIALOG)})', 'last story closes after completion', timeout=6)
    await check(browser, "!document.querySelector('[data-story=lolita]').classList.contains('story-unseen')",
                'final story is marked seen')
    await browser.capture(SCREENSHOTS / 'next_stories_completed_desktop.png')


async def redirects_and_missing(browser):
    """Legacy hash redirects, encoded categories and real unknown-route responses."""
    await browser.send('Page.navigate', {'url': BASE_URL + '/#forums'})
    await check(browser, "location.pathname === '/explore' && !location.hash && "
                "document.querySelector('h1')?.textContent === 'Explore'", 'legacy #forums redirects to real Explore route', timeout=30)
    await ready(browser)
    await load(browser, '/explore/UI%2FUX')
    await check(browser, "Boolean(document.querySelector('.category-hero')) && document.querySelector('.section-heading')?.textContent.includes('UI/UX discussions') && "
                "location.pathname === '/explore/UI%2FUX'", 'encoded slash category survives direct load')
    await load(browser, '/post/missing')
    await check(browser, "document.querySelector('.empty-state')?.textContent.includes('Post not found')", 'missing post feedback')
    await load(browser, '/topic/missing')
    await check(browser, "document.querySelector('.empty-state')?.textContent.includes('Discussion not found')", 'missing discussion feedback')
    # Next may render its own 404 outside AppShell, so do not require shell hydration here.
    await browser.send('Page.navigate', {'url': BASE_URL + '/unknown-next-regression-route'})
    await check(browser, "location.pathname === '/unknown-next-regression-route' && "
                "document.readyState === 'complete' && /page not found|404|could not be found/i.test(document.body.textContent)",
                'unknown real route shows not-found state', timeout=30)


async def ui_ux(browser):
    """Route motion/state, source-aware Back, accurate stats, detail comments and filters."""
    async def start_screen_motion_sample():
        await poll(browser, """(() => {
            const wrapper=document.querySelector('[data-testid="screen-content"]');
            if(!wrapper) return false;
            const style=getComputedStyle(wrapper);
            const y=style.transform==='none' ? 0 : new DOMMatrixReadOnly(style.transform).m42;
            return Number(style.opacity)>=.999 && Math.abs(y)<.5;
        })()""", 'route settles before motion sample')
        await browser.evaluate("""(() => {
            const element=document.querySelector('[data-testid="screen-content"]');
            if(!element) throw new Error('Persistent route wrapper is missing');
            const sample={element,frames:[],done:false};
            globalThis.__uiUXMotionSample=sample;
            const started=performance.now();
            const frame=now=>{
                const style=getComputedStyle(element);
                const y=style.transform==='none' ? 0 : new DOMMatrixReadOnly(style.transform).m42;
                sample.frames.push({opacity:Number(style.opacity),y});
                if(now-started>=450){sample.done=true;return;}
                requestAnimationFrame(frame);
            };
            requestAnimationFrame(frame);
        })()""")

    async def check_screen_motion_sample(label):
        await poll(browser, "globalThis.__uiUXMotionSample?.done === true",
                   f'{label} frame sample completes', timeout=1.5)
        await check(browser, """(() => {
            const sample=globalThis.__uiUXMotionSample;
            const last=sample?.frames.at(-1);
            return sample && sample.element===window.__uiUXScreen && sample.frames.length>=5 &&
                sample.frames.some(frame=>frame.opacity<0.995 || Math.abs(frame.y)>0.5) &&
                last.opacity>=0.999 && Math.abs(last.y)<0.5;
        })()""", label)

    await composer(browser)
    await input_text(browser, '#shareTextInput', 'Popular regression post')
    await click(browser, '#sendPostBtn')
    await poll(browser, "document.querySelector('#feedStream article.new-post')", 'published regression post')
    post_id = await browser.evaluate("document.querySelector('#feedStream article.new-post').id")
    post = '#' + post_id

    await click(browser, '.filter-tab[data-filter=popular]')
    popular_order = """(() => {
        const cards=[...document.querySelectorAll('#dynamicPostsFeed article')];
        const scores=cards.map(card => {
            const like=card.querySelector('.like-btn');
            return Number(like?.dataset.likes || 0) + Number(!!like?.dataset.reaction);
        });
        const own=cards.findIndex(card => card.id === __POST_ID__);
        return cards.length === 3 && own === cards.length-1 &&
            scores.every((score,index) => index === 0 || scores[index-1] >= score);
    })()""".replace('__POST_ID__', json.dumps(post_id))
    await check(browser, popular_order, 'Popular keeps the new own post in computed score order')
    await click(browser, '.filter-tab[data-filter=recents]')

    await composer(browser)
    await input_text(browser, '#shareTextInput', 'Route transition draft')
    await browser.evaluate("""(() => {
        const rect=element=>element?.getBoundingClientRect().toJSON();
        window.__uiUXStaticShell={
            left:document.querySelector('.sidebar-left'),
            right:document.querySelector('.sidebar-right'),
            dock:document.querySelector('#shareDock'),
            rects:[rect(document.querySelector('.sidebar-left')),
                rect(document.querySelector('.sidebar-right')),rect(document.querySelector('#shareDock'))],
        };
        window.__uiUXScreen=document.querySelector('[data-testid="screen-content"]');
    })()""")
    await start_screen_motion_sample()
    await click(browser, '.quick-nav a[href="/profile"]')
    await check(browser, "location.pathname === '/profile'", 'self profile opens for route motion')
    await check_screen_motion_sample('central route wrapper animates in place')
    await check(browser, """(() => {
        const screen=document.querySelector('[data-testid=screen-content]');
        return screen && screen === window.__uiUXScreen;
    })()""", 'central route wrapper keeps its DOM identity')
    await check(browser, """(() => {
        const old=window.__uiUXStaticShell;
        const nodes=[document.querySelector('.sidebar-left'),document.querySelector('.sidebar-right'),document.querySelector('#shareDock')];
        const rects=nodes.map(element=>element?.getBoundingClientRect().toJSON());
        return old && old.left === nodes[0] && old.right === nodes[1] && old.dock === nodes[2] &&
            rects.every((rect,index)=>rect && ['x','y','width','height'].every(key=>Math.abs(rect[key]-old.rects[index][key])<=1));
    })()""", 'rails and composer stay stable during route motion')
    await check(browser, "document.querySelector('#shareTextInput')?.value === 'Route transition draft'",
                'composer draft survives route motion')
    await go(browser, '/')
    await check(browser, "document.querySelector('#shareTextInput')?.value === 'Route transition draft'",
                'composer draft survives return navigation')
    await browser.send('Emulation.setEmulatedMedia', {'features':[{'name':'prefers-reduced-motion','value':'reduce'}]})
    await start_screen_motion_sample()
    await click(browser, '.quick-nav a[href="/profile"]')
    await check(browser, "location.pathname === '/profile'", 'self profile opens with reduced motion')
    await check_screen_motion_sample('central route motion remains active with reduced-motion emulation')
    await go(browser, '/')
    await check(browser, "document.querySelector('#shareTextInput')?.value === 'Route transition draft'",
                'draft survives reduced-motion route changes')
    await browser.send('Emulation.setEmulatedMedia', {'features':[{'name':'prefers-reduced-motion','value':'no-preference'}]})

    await click(browser, '#post1 .author-avatar')
    await check(browser, "location.pathname === '/profile/George%20Lobko'", 'Feed author profile opens')
    await check(browser, """(() => {
        const stats=document.querySelector('.profile-stats');
        return stats && !stats.querySelector('a') && /1\\s+posts?/i.test(stats.textContent) &&
            /3\\s+photos?/i.test(stats.textContent);
    })()""", 'other profile shows its own post and photo counts')
    await click(browser, '#profilePosts #post1 .post-time')
    await check(browser, "location.pathname === '/post/1'", 'profile post opens its detail')
    await check(browser, "new URL(document.querySelector('[data-testid=detail-back-link]').href).pathname === '/profile/George%20Lobko'",
                'post detail returns to its source profile')
    await click(browser, '[data-testid=detail-back-link]')
    await check(browser, "location.pathname === '/profile/George%20Lobko'", 'Back restores George profile')
    await click(browser, '[data-testid=detail-back-link]')
    await check(browser, "location.pathname === '/'", 'profile Back restores the feed source')

    # Exercise browser history, not just the app's contextual Back link. When a
    # user returns to an earlier route and then opens the same profile from a
    # post, contextual Back must escape to the retained Feed ancestor instead
    # of cycling between that profile and Post 1.
    await click(browser, '#post1 .author-avatar')
    await check(browser, "location.pathname === '/profile/George%20Lobko'", 'history cycle starts from George profile')
    await click(browser, '#profilePosts #post1 .post-time')
    await check(browser, "location.pathname === '/post/1'", 'history cycle opens Post 1')
    await browser.evaluate('history.back()')
    await check(browser, "location.pathname === '/profile/George%20Lobko'", 'browser Back returns to profile')
    await browser.evaluate('history.back()')
    await check(browser, "location.pathname === '/'", 'browser Back returns to Feed')
    await browser.evaluate('history.forward()')
    await check(browser, "location.pathname === '/profile/George%20Lobko'", 'browser Forward restores profile')
    await browser.evaluate('history.forward()')
    await check(browser, "location.pathname === '/post/1'", 'browser Forward restores Post 1')
    await click(browser, '#post1 .author-avatar')
    await check(browser, "location.pathname === '/profile/George%20Lobko'", 'Post 1 author opens George profile again')
    await click(browser, '[data-testid=detail-back-link]')
    await check(browser, "location.pathname === '/'", 'contextual Back escapes the browser-history cycle to Feed')

    await click(browser, '#post1 .post-time')
    await click(browser, '#post1 .author-avatar')
    await check(browser, "location.pathname === '/profile/George%20Lobko'", 'post author opens George profile')
    await click(browser, '#profilePosts #post1 .post-time')
    await check(browser, "location.pathname === '/post/1'", 'same post opens from its author profile')
    await click(browser, '[data-testid=detail-back-link]')
    await check(browser, "location.pathname === '/' || location.pathname === '/profile/George%20Lobko'",
                'repeat-post Back skips cyclic destinations')
    if await browser.evaluate("location.pathname === '/profile/George%20Lobko'"):
        await click(browser, '[data-testid=detail-back-link]')
    await check(browser, "location.pathname === '/'", 'repeated post route returns to a non-cyclic ancestor')

    # A top-level Feed visit has no contextual Back parent. It must break the
    # older Feed → George ancestry before a later Post → George detail chain.
    await click(browser, '#post1 .author-avatar')
    await check(browser, "location.pathname === '/profile/George%20Lobko'", 'boundary chain opens George profile')
    await click(browser, 'a.nav-item[data-tab="feed"]')
    await check(browser, "location.pathname === '/'", 'global Feed navigation resets the detail chain')
    await click(browser, '#post1 .post-time')
    await check(browser, "location.pathname === '/post/1'", 'boundary chain opens Post 1 from Feed')
    await click(browser, '#post1 .author-avatar')
    await check(browser, "location.pathname === '/profile/George%20Lobko'", 'Post 1 author opens George again')
    await click(browser, '[data-testid=detail-back-link]')
    await check(browser, "location.pathname === '/post/1'", 'George Back returns to Post 1 after Feed boundary')
    await click(browser, '[data-testid=detail-back-link]')
    await check(browser, "location.pathname === '/'", 'Post 1 Back stops at Feed boundary')

    await click(browser, '#post1 .post-media')
    await check(browser, """[...document.querySelectorAll('[role=dialog] [data-testid=media-source-post]')]
                .some(link => new URL(link.href).pathname === '/post/1')""",
                'feed attachment preview exposes its source post')
    await click(browser, '[data-testid=media-source-post]')
    await check(browser, "location.pathname === '/post/1'", 'feed preview source link opens the related post')
    await click(browser, '[data-testid=detail-back-link]')
    await check(browser, "location.pathname === '/'", 'feed preview source returns to the feed')

    await go(browser, '/profile')
    await check(browser, """(() => {
        const stats=document.querySelector('.profile-stats');
        return stats?.querySelector('a[href="/friends/all"]')?.textContent.includes('3') &&
            stats?.querySelector('a[href="/friends/following"]')?.textContent.includes('0');
    })()""", 'self profile keeps accurate actionable relationship counts')
    await go(browser, '/friends')
    await go(browser, '/friends/suggestions')
    await click(browser, 'a[aria-label="View Nick Shelburne profile"]')
    await check(browser, "location.pathname === '/profile/Nick%20Shelburne'", 'Friends profile opens')
    await click(browser, '[data-testid=detail-back-link]')
    await check(browser, "location.pathname === '/friends/suggestions'", 'Friends profile Back returns to its list')

    await go(browser, '/explore')
    await go(browser, '/explore/UI%2FUX')
    await click(browser, 'a[href="/topic/details"]')
    await check(browser, "location.pathname === '/topic/details'", 'category topic opens')
    await click(browser, '[data-testid=detail-back-link]')
    await check(browser, "location.pathname === '/explore/UI%2FUX'", 'topic Back returns to its category')

    await go(browser, '/friends')
    await go(browser, '/profile/George%20Lobko')
    await click(browser, '[data-action=profile-tab][data-value=photos]')
    await click(browser, '.media-tile')
    await check(browser, """Boolean([...document.querySelectorAll('[role=dialog] [data-testid=media-source-post]')]
                .some(link => new URL(link.href).pathname === '/post/1'))""",
                'media viewer exposes its source post')
    await click(browser, '[data-testid=media-source-post]')
    await check(browser, "location.pathname === '/post/1'", 'media source link opens related post')
    await click(browser, '[data-testid=detail-back-link]')
    await check(browser, "location.pathname === '/profile/George%20Lobko'", 'related post Back returns to the profile gallery')

    await go(browser, '/')
    await click(browser, post + ' .post-time')
    await check(browser, """(() => {
        const detail=document.querySelector('[data-testid=screen-content]');
        const card=detail?.querySelector('.new-post');
        return card?.querySelector('[data-testid=post-comment-trigger]') &&
            !document.querySelector('[data-testid=comment-dialog]') &&
            !card.querySelector('.comments-drawer');
    })()""", 'post detail keeps comments closed until requested')
    await click(browser, '[data-testid=screen-content] [data-testid=post-comment-trigger]')
    await check(browser, "Boolean(document.querySelector('[data-testid=comment-dialog]')) && "
                "!document.querySelector('[data-testid=comment-dialog] [data-testid=comment-item]') && "
                "document.querySelector('[data-testid=comment-dialog] .comment-send-btn')?.disabled",
                'new post detail opens an empty comment dialog with submit disabled')
    await escape(browser)
    await check(browser, "document.activeElement === document.querySelector('[data-testid=screen-content] [data-testid=post-comment-trigger]')",
                'detail comment dialog Escape restores its trigger focus')
    await click(browser, '[data-testid=detail-back-link]')
    await check(browser, f"location.pathname === '/' && !document.querySelector('[data-testid=comment-dialog]') && "
                f"!document.querySelector('{post} .comments-drawer')",
                'feed post comments remain closed')

    await load(browser, '/post/1')
    await click(browser, '[data-testid=detail-back-link]')
    await check(browser, "location.pathname === '/'", 'direct post load has a Feed fallback')


async def layout_state(browser):
    return await browser.evaluate("""(() => {
        const visible=e=>Boolean(e?.getClientRects().length) && getComputedStyle(e).visibility!=='hidden';
        const dock=document.querySelector('#shareDock'), feed=document.querySelector('#mainContent');
        const d=dock?.getBoundingClientRect(), f=feed?.getBoundingClientRect();
        const nav=document.querySelector('#navMenu')?.getBoundingClientRect();
        return {
            overflow:document.documentElement.scrollWidth>innerWidth,
            heading:document.querySelector('h1')?.textContent,
            title:document.title,
            theme:document.documentElement.dataset.theme,
            activeNav:document.querySelectorAll('.nav-item[aria-current=page]').length,
            dockVisible:visible(dock), dock:d?.toJSON(), feed:f?.toJSON(), nav:nav?.toJSON(),
            dockPosition:dock?getComputedStyle(dock).position:null,
            postActions:[...document.querySelectorAll('.post-actions-footer')].filter(visible).flatMap(footer=>{
                const card=footer.closest('article'), cardRect=card?.getBoundingClientRect().toJSON();
                return [...footer.querySelectorAll('[data-testid="post-react"], [data-testid="post-comment-trigger"], [data-testid="share-post-trigger"]')]
                    .filter(visible).map(button=>({name:button.dataset.testid, rect:button.getBoundingClientRect().toJSON(), card:cardRect}));
            }),
            brokenImages:[...document.querySelectorAll('img[src]')].filter(i=>visible(i) &&
                i.getAttribute('src') && i.complete && !i.naturalWidth).map(i=>i.src),
            rails:[...document.querySelectorAll('.sidebar-left,.sidebar-right')].filter(visible).map(rail=>{
                rail.scrollTop=100; const r=rail.getBoundingClientRect();
                return {scrollTop:rail.scrollTop, clientHeight:rail.clientHeight, scrollHeight:rail.scrollHeight,
                    bottom:r.bottom, children:[...rail.children].filter(visible).map(c=>c.getBoundingClientRect().bottom)};
            }),
        };
    })()""")


def assert_layout(state, width, height, dock_expected, nav_count, heading, value):
    assert not state['overflow'], state
    assert not state['brokenImages'], state
    assert state['heading'] == heading, state
    assert state['title'] == heading + ' · VibeSocial', state
    assert state['theme'] == value, state
    assert state['activeNav'] == nav_count, state
    assert state['dockVisible'] == dock_expected, state
    if heading in ('Feeds', 'Post'):
        for action in state['postActions']:
            rect, card = action['rect'], action['card']
            assert card and rect['left'] >= max(0, card['left']) - 1 and \
                rect['right'] <= min(width, card['right']) + 1 and \
                rect['top'] >= card['top'] - 1 and rect['bottom'] <= card['bottom'] + 1, action
            if width <= 700:
                assert rect['height'] >= 44, action
    for rail in state['rails'] if width > 700 else []:
        assert rail['scrollTop'] == 0 and rail['scrollHeight'] <= rail['clientHeight'] + 1, rail
        assert rail['bottom'] <= height + 1, rail
        assert all(bottom <= rail['bottom'] + 1 for bottom in rail['children']), rail
    if dock_expected:
        d, f = state['dock'], state['feed']
        assert state['dockPosition'] == 'fixed', state
        assert 0 <= d['left'] < d['right'] <= width + 1, state
        assert abs(d['bottom'] - (height - (72 if width <= 700 else 24))) <= 1, state
        assert abs((d['left'] + d['right'] - f['left'] - f['right']) / 2) <= 1, state
        assert abs(d['width'] - 60) <= 1 and abs(d['height'] - 60) <= 1, state
        if width <= 700:
            assert state['nav'] and abs(state['nav']['bottom'] - height) <= 1, state
            assert d['bottom'] <= state['nav']['top'], state


async def matrix(browser):
    """14 real routes × 5 viewports × 2 UI-selected themes; 140 screenshots."""
    failures = []
    passed = 0
    for value in ('light', 'dark'):
        for width, height in VIEWPORTS:
            async with chrome(width, height) as browser:
                for path, heading, dock_expected, nav_count in ROUTES:
                    label = f'{value} {width}x{height} {path}'
                    slug = path.strip('/').replace('/', '_').replace('%', '') or 'feed'
                    try:
                        await load(browser, path)
                        await theme(browser, value)
                        await poll(browser, "[...document.images].filter(i => {const r=i.getBoundingClientRect(); "
                                   "return r.width && r.height && r.top<innerHeight && r.bottom>0;}).every(i=>i.complete)",
                                   f'visible images loaded: {label}', timeout=10)
                        if dock_expected:
                            await poll(browser, "Math.abs(document.querySelector('#shareDock').getBoundingClientRect().width - 60) <= 1",
                                       'composer collapse animation finishes')
                        state = await layout_state(browser)
                        assert_layout(state, width, height, dock_expected, nav_count, heading, value)
                        if path == '/messages/George%20Lobko':
                            await check(browser, "document.querySelector('.message-form button').getBoundingClientRect().height < 48",
                                        'message send control compact')
                        if dock_expected:
                            top = state['dock']['top']
                            await browser.evaluate('scrollTo(0, document.documentElement.scrollHeight)')
                            await browser.evaluate('new Promise(r=>requestAnimationFrame(r))')
                            await check(browser, f"Math.abs(document.querySelector('#shareDock').getBoundingClientRect().top - {top}) <= 1",
                                        'composer stays fixed while scrolling')
                            await browser.evaluate('scrollTo(0,0)')
                        await browser.capture(SCREENSHOTS / f'next_{slug}_{value}_{width}.png')
                        passed += 1
                        print(f'  PASS matrix {label}', flush=True)
                    except Exception as exc:
                        failures.append(f'{label}: {exc}')
                        print(f'  FAIL matrix {label}: {exc}', flush=True)
                        await browser.capture(SCREENSHOTS / f'next_FAIL_{slug}_{value}_{width}.png')
    print(f'  Matrix: {passed}/140 passed', flush=True)
    assert not failures, '\n'.join(failures)
    assert passed == 140
    async with chrome() as browser:
        await load(browser, '/')
        await theme(browser, 'light')
        for width, height in [(1440, 900), (1260, 640), (1260, 500), (1024, 768)]:
            await viewport(browser, width, height)
            state = await layout_state(browser)
            assert_layout(state, width, height, True, 1, 'Feeds', 'light')
            await browser.capture(SCREENSHOTS / f'next_short_rails_{width}_{height}.png')
            print(f'  PASS desktop rails fit without scrolling {width}x{height}', flush=True)


async def persistence(browser):
    """Published media, edits, interactions and unfinished drafts survive reload."""
    await composer(browser)
    await attach(browser, '#imageAttachmentInput', '''new Promise(resolve => {
        const canvas=document.createElement('canvas');canvas.width=80;canvas.height=60;
        canvas.toBlob(blob=>resolve(new File([blob],'persistent.png',{type:'image/png'})));
    })''')
    await input_text(browser, '#shareTextInput', 'A lasting moment')
    await click(browser, '#sendPostBtn')
    post_id = await browser.evaluate("document.querySelector('.new-post').id")
    await input_text(browser, '#shareTextInput', 'An unfinished thought')
    await attach(browser, '#fileAttachmentInput', "new File(['draft bytes'],'draft.txt',{type:'text/plain'})")
    await click(browser, '#post1 .card-more-btn')
    await click(browser, '[role=menuitem]:nth-child(2)')
    await click(browser, '#likeBtn1')
    await go(browser, '/settings')
    await input_text(browser, '[name=bio]', 'My saved bio')
    await submit(browser, '[data-form=settings]')
    await go(browser, '/friends/suggestions')
    await click(browser, '[data-action=follow][data-value="Nick Shelburne"]')
    await load(browser, '/')
    await check(browser, f"document.getElementById({json.dumps(post_id)})?.textContent.includes('A lasting moment')",
                'published post survives reload')
    await check(browser, f"document.querySelector('#{post_id} img[alt=\"persistent.png\"]')?.naturalWidth===80",
                'uploaded image is restored from stored bytes')
    await check(browser, f"!document.querySelector('#{post_id} .post-time').textContent.includes('Just now')",
                'persisted post keeps its actual publishing date')
    await check(browser, "document.querySelector('#likeBtn1').dataset.reaction===''",
                'reaction removal survives reload')
    await composer(browser)
    await check(browser, "document.querySelector('#shareTextInput').value==='An unfinished thought'",
                'unfinished composer survives reload')
    await check(browser, "document.querySelector('#attachmentPreviewBar').textContent.includes('draft.txt')",
                'unfinished file attachment survives reload')
    await go(browser, '/profile')
    await check(browser, "document.querySelector('.profile-summary').textContent.includes('My saved bio')",
                'profile edits survive reload')
    await click(browser, '[data-action=profile-tab][data-value=saved]')
    await check(browser, "document.querySelector('#profilePosts #post1')!==null", 'saved post survives reload')
    await go(browser, '/friends/following')
    await check(browser, "document.querySelector('.person-list').textContent.includes('Nick Shelburne')",
                'following survives reload')
    await go(browser, '/messages')
    await go(browser, '/messages/George%20Lobko')
    await input_text(browser, '[name=message]', 'A durable message')
    await submit(browser, '[data-form=message]')
    await load(browser, '/messages/George%20Lobko')
    await check(browser, "document.querySelector('.chat-history').textContent.includes('A durable message')",
                'sent message survives reload')


async def message_drafts(browser):
    """A draft stays with its recipient and is never sent to another conversation."""
    await go(browser, '/messages')
    await go(browser, '/messages/George%20Lobko')
    await input_text(browser, '[name=message]', 'Only for George')
    await go(browser, '/messages/Olya')
    await check(browser, "document.querySelector('[name=message]').value===''",
                'new recipient has a separate draft')
    await input_text(browser, '[name=message]', 'Only for Olya')
    await go(browser, '/messages/George%20Lobko')
    await check(browser, "document.querySelector('[name=message]').value==='Only for George'",
                'returning restores the correct recipient draft')
    await load(browser, '/messages/George%20Lobko')
    await check(browser, "document.querySelector('[name=message]').value==='Only for George'",
                'message draft survives reload')
    await submit(browser, '[data-form=message]')
    await go(browser, '/messages/Olya')
    await check(browser, "document.querySelector('[name=message]').value==='Only for Olya' && "
                "!document.querySelector('.chat-history').textContent.includes('Only for George')",
                'sending clears only its own draft and reaches only its recipient')


async def content_controls(browser):
    """Owners can edit posts and comments, cancel deletion, and remove whole threads."""
    await composer(browser)
    await input_text(browser, '#shareTextInput', 'Before editing')
    await click(browser, '#sendPostBtn')
    post_id = await browser.evaluate("document.querySelector('.new-post').id")
    await click(browser, f'#{post_id} .card-more-btn')
    await poll(browser, "[...document.querySelectorAll('[role=menuitem]')].some(e=>e.textContent==='Edit Post')",
               'own post offers editing')
    await browser.evaluate("[...document.querySelectorAll('[role=menuitem]')].find(e=>e.textContent==='Edit Post').click()")
    await input_text(browser, '[aria-label="Edit post text"]', '<b>After editing</b>')
    await click(browser, '[data-testid=save-post-edit]')
    await check(browser, f"document.querySelector('#{post_id} [data-post-text]').textContent==='<b>After editing</b>' && "
                f"!document.querySelector('#{post_id} [data-post-text] b')", 'post edit stays literal')
    await load(browser, '/')
    await check(browser, f"document.querySelector('#{post_id} [data-post-text]')?.textContent==='<b>After editing</b>'",
                'edited post survives reload')
    await click(browser, f'#{post_id} .comment-btn')
    await input_text(browser, '.comment-input', 'Parent comment')
    await submit(browser, '.comment-input-wrap')
    comment_id = await browser.evaluate("document.querySelector('[data-testid=comment-item]').dataset.commentId")
    await click(browser, f'[data-comment-id="{comment_id}"] [data-action=edit-comment]')
    await input_text(browser, '.comment-input', 'Edited parent')
    await submit(browser, '.comment-input-wrap')
    await check(browser, "document.querySelector('[data-testid=comment-text]').textContent==='Edited parent'",
                'own comment can be edited')
    await close_dialog(browser)
    await load(browser, '/')
    await click(browser, f'#{post_id} .comment-btn')
    await check(browser, "document.querySelector('[data-testid=comment-text]').textContent==='Edited parent'",
                'edited comment survives reload')
    await click(browser, f'[data-comment-id="{comment_id}"] .comment-reply-btn')
    await input_text(browser, '.comment-input', 'Child reply')
    await submit(browser, '.comment-input-wrap')
    await click(browser, f'[data-comment-id="{comment_id}"] [data-action=delete-comment]')
    await click(browser, '[data-testid=cancel-comment-delete]')
    await check(browser, "document.querySelectorAll('[data-testid=comment-item]').length===2",
                'cancel preserves comment thread')
    await check(browser, "document.activeElement.dataset.action==='delete-comment'",
                'cancel restores focus to its delete control')
    await click(browser, f'[data-comment-id="{comment_id}"] [data-action=delete-comment]')
    await click(browser, '[data-testid=confirm-comment-delete]')
    await check(browser, "document.querySelectorAll('[data-testid=comment-item]').length===0",
                'deleting parent removes its replies without orphaning them')
    await close_dialog(browser)
    await load(browser, '/')
    await click(browser, f'#{post_id} .comment-btn')
    await check(browser, "document.querySelectorAll('[data-testid=comment-item]').length===0",
                'comment deletion survives reload')
    await viewport(browser, 320, 640)
    await input_text(browser, '.comment-input', 'Mobile comment controls')
    await submit(browser, '.comment-input-wrap')
    await check(browser, """[...document.querySelectorAll('.comment-bubble button')].every(el=>{
        const r=el.getBoundingClientRect();return r.height>=44&&r.left>=0&&r.right<=innerWidth;
    })""", 'mobile comment controls fit and have accessible touch targets')
    await browser.capture(SCREENSHOTS / 'next_content_controls_mobile.png')
    await close_dialog(browser)
    await click(browser, f'#{post_id} .card-more-btn')
    await click(browser, '[role=menuitem]:last-child')
    await click(browser, '[data-testid=delete-post-confirm]')
    await load(browser, '/post/' + post_id[4:])
    await check(browser, "document.querySelector('.empty-state h2')?.textContent==='Post not found'",
                'deleted post stays deleted after reload and direct navigation')


async def storage_safety(browser):
    """A stale writer or unreadable database never silently destroys saved work."""
    await go(browser, '/settings')
    await poll(browser, "document.querySelector('[data-settings-status]').textContent.includes('Saved on this browser')",
               'initial browser snapshot committed')
    await browser.evaluate('''(async () => {
        const db=await new Promise((resolve,reject)=>{const r=indexedDB.open('vibesocial',1);r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
        await new Promise((resolve,reject)=>{const tx=db.transaction('social','readwrite'),store=tx.objectStore('social'),r=store.get('state');
            r.onsuccess=()=>{const value=r.result;value.revision=(value.revision||0)+1;value.data.people[0].bio='Saved by another tab';store.put(value,'state');};
            tx.oncomplete=resolve;tx.onabort=()=>reject(tx.error);});db.close();
    })()''')
    await input_text(browser, '[name=bio]', 'Stale tab edit')
    await submit(browser, '[data-form=settings]')
    await check(browser, "document.querySelector('.storage-warning')?.textContent.includes('another tab')",
                'stale writer shows a conflict instead of overwriting')
    await load(browser, '/settings')
    await check(browser, "document.querySelector('[name=bio]').value==='Saved by another tab'",
                'latest saved work remains intact')
    await browser.evaluate('''(async () => {
        const db=await new Promise(resolve=>{const r=indexedDB.open('vibesocial',1);r.onsuccess=()=>resolve(r.result);});
        await new Promise(resolve=>{const tx=db.transaction('social','readwrite');tx.objectStore('social').put({version:999,marker:'keep me'},'state');tx.oncomplete=resolve;});db.close();
    })()''')
    await load(browser, '/')
    await check(browser, "document.querySelector('.storage-warning')?.textContent.includes('kept') && document.querySelector('#post1')",
                'unreadable saved data has a visible fallback')
    await check(browser, '''(async () => {
        const db=await new Promise(resolve=>{const r=indexedDB.open('vibesocial',1);r.onsuccess=()=>resolve(r.result);});
        const value=await new Promise(resolve=>{const r=db.transaction('social').objectStore('social').get('state');r.onsuccess=()=>resolve(r.result);});db.close();return value.marker==='keep me';
    })()''', 'unreadable data is preserved for recovery')


async def storage_failures(browser):
    """Storage errors stay visible, preserve memory, and recover without uncaught exceptions."""
    await go(browser, '/settings')
    await poll(browser, "document.querySelector('[data-settings-status]').textContent.includes('Saved on this browser')",
               'browser data committed before failure fixture')
    await browser.evaluate("""(() => {
        globalThis.__realPut=IDBObjectStore.prototype.put;
        IDBObjectStore.prototype.put=function(){throw new DOMException('Storage full','QuotaExceededError');};
    })()""")
    await go(browser, '/')
    await composer(browser)
    await input_text(browser, '#shareTextInput', 'Kept through storage failure')
    await click(browser, '#sendPostBtn')
    await check(browser, "document.querySelector('.storage-warning')?.textContent.includes('could not be saved') && "
                "document.querySelector('.new-post').textContent.includes('Kept through storage failure')",
                'quota failure warns while retaining the current post')
    await browser.evaluate('IDBObjectStore.prototype.put=globalThis.__realPut;delete globalThis.__realPut')
    await input_text(browser, '#shareTextInput', 'Save retry')
    await check(browser, "!document.querySelector('.storage-warning')", 'a later successful save clears the storage warning')
    await load(browser, '/')
    await check(browser, "document.querySelector('.new-post').textContent.includes('Kept through storage failure')",
                'work is durable after storage recovers')
    async with chrome() as blocked:
        await blocked.send('Page.addScriptToEvaluateOnNewDocument', {'source':
            "indexedDB.open=()=>{throw new DOMException('Blocked','SecurityError')};"})
        await load(blocked, '/')
        await check(blocked, "document.querySelector('.storage-warning')?.textContent.includes('unavailable') && document.querySelector('#post1')",
                    'blocked browser storage leaves the app usable with an explicit warning')


async def notifications(browser):
    """Notification navigation, reading, source actions, persistence and old-data migration."""
    await go(browser, '/notifications')
    await check(browser, "document.querySelector('[data-tab=notifications][aria-current=page]') && "
                "!document.querySelector('nav a[href^=\"/media\"]') && "
                "document.querySelector('#notificationsBadge')?.textContent.trim() === '6' && "
                "document.querySelectorAll('[data-notification]').length === 6",
                'Notifications replaces Media with six existing incoming activities')
    await poll(browser, "[...document.querySelectorAll('.nav-item')].every(el => "
               "el.getAnimations().every(animation => animation.playState !== 'running'))",
               'notification navigation colors settle')
    await browser.capture(SCREENSHOTS / 'next_notifications_desktop.png')
    await click(browser, '[data-notification-filter=unread]')
    await check(browser, "document.querySelectorAll('[data-notification][data-unread=true]').length === 6",
                'Unread filter starts with six notifications')
    await click(browser, '[data-notification][href="/messages/George%20Lobko"]')
    await check(browser, "location.pathname === '/messages/George%20Lobko' && "
                "document.querySelector('#notificationsBadge')?.textContent.trim() === '5' && "
                "document.querySelector('#messagesBadge')?.textContent.trim() === '4'",
                'opening a message notification opens and reads its conversation')
    await go(browser, '/notifications')
    await check(browser, "document.querySelector('[data-notification][href=\"/messages/George%20Lobko\"]')?.dataset.unread === 'false'",
                'read message remains in activity history')
    await click(browser, '[data-notification="request:Davis"]')
    await check(browser, "location.pathname === '/friends/requests' && "
                "document.querySelector('#notificationsBadge')?.textContent.trim() === '4'",
                'request notification opens the pending requests')
    await click(browser, '[data-action=accept][data-value=Davis]')
    await go(browser, '/notifications')
    await check(browser, "!document.querySelector('[data-notification=\"request:Davis\"]') && "
                "document.querySelectorAll('[data-notification]').length === 5",
                'accepted request disappears from pending activity')
    await go(browser, '/messages')
    await go(browser, '/messages/Olya')
    await go(browser, '/notifications')
    await check(browser, "document.querySelector('#notificationsBadge')?.textContent.trim() === '3'",
                'reading Messages directly also clears its notification')
    await click(browser, '[data-notifications-read-all]')
    await check(browser, "!document.querySelector('#notificationsBadge') && "
                "document.querySelector('[data-notifications-read-all]').disabled",
                'mark all reads all activity and removes badge')
    await click(browser, '[data-notification-filter=unread]')
    await check(browser, "!document.querySelector('[data-notification]') && "
                "document.querySelector('.empty-state')?.textContent.includes('All caught up')",
                'empty Unread filter gives clear feedback')
    await load(browser, '/notifications')
    await check(browser, "!document.querySelector('#notificationsBadge') && "
                "document.querySelectorAll('[data-notification][data-unread=false]').length === 5",
                'read states and pending request changes survive reload')
    await viewport(browser, 320, 640)
    await theme(browser, 'dark')
    await check(browser, "document.documentElement.scrollWidth <= innerWidth && "
                "[...document.querySelectorAll('[data-notification]')].every(el => el.getBoundingClientRect().height >= 44)",
                'mobile dark activity rows fit and meet touch target size')
    await browser.capture(SCREENSHOTS / 'next_notifications_mobile.png')
    await go(browser, '/')
    await composer(browser)
    await input_text(browser, '#shareTextInput', 'Draft retained through notification migration')
    await go(browser, '/settings')
    await poll(browser, "document.querySelector('[data-settings-status]').textContent.includes('Saved on this browser')",
               'notification state saved before migration fixture')
    # An older supported snapshot has no notificationRead field.
    await browser.evaluate('''(async () => {
        const db=await new Promise(resolve=>{const r=indexedDB.open('vibesocial',1);r.onsuccess=()=>resolve(r.result);});
        await new Promise((resolve,reject)=>{
            const tx=db.transaction('social','readwrite'),store=tx.objectStore('social'),r=store.get('state');
            r.onsuccess=()=>{const value=r.result;delete value.data.notificationRead;store.put(value,'state');};
            tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);
        });db.close();
    })()''')
    await load(browser, '/')
    await composer(browser)
    await check(browser, "!document.querySelector('.storage-warning') && "
                "document.querySelector('#shareTextInput').value === 'Draft retained through notification migration' && "
                "document.querySelector('#notificationsBadge')?.textContent.trim() === '3'",
                'older saved data gains notifications without losing drafts or read conversations')
    await go(browser, '/friends')
    await check(browser, "document.querySelector('[data-testid=screen-content]').textContent.includes('Davis')",
                'friend changes survive old-snapshot migration')
    for old, target in [('/media', '/notifications'), ('/media/photos', '/notifications'),
                        ('/media/videos', '/notifications'), ('/media/yours', '/notifications'),
                        ('/media/stories', '/stories')]:
        await browser.send('Page.navigate', {'url': BASE_URL + old})
        await poll(browser, f'location.pathname === {json.dumps(target)} && document.readyState === "complete"',
                   f'{old} redirect commits', timeout=30)
        await ready(browser)
        await check(browser, f'location.pathname === {json.dumps(target)}', f'{old} redirects to {target}')


async def run():
    assert len(ROUTES) == 14 and len(VIEWPORTS) == 5
    assert len({route[0] for route in ROUTES}) == 14
    assert urlsplit(BASE_URL).scheme in ('http', 'https'), 'BASE_URL must be HTTP(S)'
    results = []
    print(f'Next browser regressions: {BASE_URL}', flush=True)
    tests = [navigation, discussions, reactions_and_posts, post_interactions, locations_and_emoji,
             attachments, motion_and_lifecycle, stories, redirects_and_missing, ui_ux, matrix,
             persistence, message_drafts, content_controls, storage_safety, storage_failures, notifications]
    selected = sys.argv[1:]
    if selected:
        assert set(selected) <= {test.__name__ for test in tests}, 'Unknown test group'
        tests = [test for test in tests if test.__name__ in selected]
    for test in tests:
        print(f'RUN {test.__name__}: {test.__doc__}', flush=True)
        start = time.monotonic()
        try:
            async with chrome() as browser:
                try:
                    await test(browser)
                except Exception:
                    await browser.capture(SCREENSHOTS / f'next_FAIL_{test.__name__}.png')
                    raise
            result = {'test': test.__name__, 'status': 'PASS'}
        except Exception as exc:
            result = {'test': test.__name__, 'status': 'FAIL', 'error': str(exc)}
        result['seconds'] = round(time.monotonic() - start, 2)
        results.append(result)
        print(f"{result['status']} {test.__name__} ({result['seconds']}s) {result.get('error', '')}", flush=True)
    report = Path(__file__).with_name('next_results.json')
    report.write_text(json.dumps({'base_url': BASE_URL, 'results': results}, indent=2), encoding='utf-8')
    failures = [r for r in results if r['status'] == 'FAIL']
    print(f'{len(results) - len(failures)}/{len(results)} groups passed; results: {report}', flush=True)
    if failures:
        raise SystemExit(1)


if __name__ == '__main__':
    asyncio.run(run())
