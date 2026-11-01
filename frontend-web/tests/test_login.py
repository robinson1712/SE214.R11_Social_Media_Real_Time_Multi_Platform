"""Login UI checks. Start Next first, then run: python tests/test_login.py."""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_next import (BASE_URL, SCREENSHOTS, chrome, click, input_text,
                       load, poll, escape, theme, viewport)


async def open_login(browser):
    await browser.send('Page.navigate', {'url': BASE_URL + '/login'})
    await poll(browser, "document.querySelector('#login-email') && document.readyState === 'complete'",
               'login directly loads', timeout=30)
    await click(browser, '.login-demo-button')
    await poll(browser, "document.querySelector('#login-email').value === 'hello@vibesocial.demo'",
               'login hydrated')


async def check_password_transition(browser):
    await browser.evaluate("document.querySelector('#login-password').setSelectionRange(2, 5)")
    box = await browser.evaluate("document.querySelector('.login-eye-toggle').getBoundingClientRect().toJSON()")
    point = {'x': box['x'] + box['width'] / 2, 'y': box['y'] + box['height'] / 2}
    await browser.send('Input.dispatchMouseEvent', {'type': 'mouseMoved', **point})
    await browser.send('Input.dispatchMouseEvent', {'type': 'mousePressed', 'button': 'left', 'clickCount': 1, **point})
    await browser.evaluate('new Promise(requestAnimationFrame)')
    assert await browser.evaluate("document.activeElement.id === 'login-password' && document.querySelector('.login-world').classList.contains('is-surprised')"), 'pressing visibility toggle must not blur the password or reset the expression'
    await browser.send('Input.dispatchMouseEvent', {'type': 'mouseReleased', 'button': 'left', 'clickCount': 1, **point})
    frames = await browser.evaluate("""new Promise(resolve => {
        const frames = [], start = performance.now();
        function sample(now) {
            const eye = getComputedStyle(document.querySelector('.friend-eye'));
            frames.push({mouth: Number(getComputedStyle(document.querySelector('.friend-surprise-mouth')).opacity),
                eye: new DOMMatrixReadOnly(eye.transform).m22,
                pupil: Number(getComputedStyle(document.querySelector('.friend-pupil')).opacity)});
            if (now - start < 450) requestAnimationFrame(sample); else resolve(frames);
        }
        requestAnimationFrame(sample);
    })""")
    assert any(.05 < f['mouth'] < .95 for f in frames), 'surprised mouth fades gradually'
    assert any(.15 < f['eye'] < .95 for f in frames), 'eyes close gradually without changing face layout'
    assert any(.05 < f['pupil'] < .95 for f in frames), 'pupils fade instead of disappearing abruptly'
    assert frames[-1]['mouth'] == 0 and frames[-1]['eye'] < .15 and frames[-1]['pupil'] == 0
    assert await browser.evaluate("document.querySelector('#login-password').type === 'text' && document.activeElement.id === 'login-password'")
    assert await browser.evaluate("document.querySelector('#login-password').selectionStart === 2 && document.querySelector('#login-password').selectionEnd === 5"), 'visibility toggle preserves selection'
    await click(browser, '.login-eye-toggle')
    await poll(browser, "[...document.querySelectorAll('.friend-surprise-mouth')].every(el => getComputedStyle(el).opacity === '1')", 'hidden password restores surprised mouths')
    for _ in range(6):
        await browser.send('Input.dispatchMouseEvent', {'type': 'mousePressed', 'button': 'left', 'clickCount': 1, **point})
        await browser.send('Input.dispatchMouseEvent', {'type': 'mouseReleased', 'button': 'left', 'clickCount': 1, **point})
        await browser.evaluate('new Promise(requestAnimationFrame)')
    await poll(browser, "document.querySelector('#login-password').type === 'password' && getComputedStyle(document.querySelector('.friend-eye')).opacity === '1'", 'rapid toggles finish with open eyes and hidden password')
    await browser.send('Input.dispatchKeyEvent', {'type': 'keyDown', 'key': 'Tab', 'code': 'Tab'})
    await browser.send('Input.dispatchKeyEvent', {'type': 'keyUp', 'key': 'Tab', 'code': 'Tab'})
    assert await browser.evaluate("document.activeElement.classList.contains('login-eye-toggle') && document.querySelector('.login-world').classList.contains('is-surprised')"), 'tabbing within password controls does not reset expression'
    for visible in [True, False]:
        await browser.send('Input.dispatchKeyEvent', {'type': 'keyDown', 'key': ' ', 'code': 'Space'})
        await browser.send('Input.dispatchKeyEvent', {'type': 'keyUp', 'key': ' ', 'code': 'Space'})
        assert await browser.evaluate("document.querySelector('.login-eye-toggle').getAttribute('aria-pressed')") == str(visible).lower()
        assert await browser.evaluate("document.activeElement.classList.contains('login-eye-toggle')"), 'keyboard activation keeps toggle focus'


async def run():
    async with chrome() as browser:
        await browser.send('Emulation.setEmulatedMedia', {'features': [{'name': 'prefers-reduced-motion', 'value': 'no-preference'}]})
        await click(browser, 'a[href="/login"]')
        await poll(browser, "location.pathname === '/login' && document.querySelector('#login-title')",
                   'Sign in opens standalone login')
        assert not await browser.evaluate("Boolean(document.querySelector('.sidebar-left, #shareDock'))")
        assert await browser.evaluate("document.activeElement.id === 'mainContent'")
        assert await browser.evaluate("""new Set([...document.querySelectorAll('.friend-smile')].map(el => {
            const s=getComputedStyle(el);
            return [s.width,s.height,s.borderTopWidth,s.borderBottomWidth,s.borderRadius,s.backgroundColor].join('|');
        })).size === 4"""), 'each character has a distinct resting expression'
        await browser.capture(SCREENSHOTS / 'login_emotions.png')

        await click(browser, '.login-submit')
        assert await browser.evaluate("!document.querySelector('form').checkValidity() && location.pathname === '/login'")
        await input_text(browser, '#login-email', 'not-an-email')
        assert await browser.evaluate("document.querySelector('#login-email').validity.typeMismatch")

        await browser.evaluate("document.querySelector('#login-password').focus()")
        await poll(browser, "document.querySelector('.login-world').classList.contains('is-surprised') && !document.querySelector('.login-world').classList.contains('is-private')",
                   'password focus opens surprised mouths and keeps eyes open')
        await poll(browser, "[...document.querySelectorAll('.friend-surprise-mouth')].every(el => getComputedStyle(el).opacity === '1')", 'surprised mouths finish opening')
        await input_text(browser, '#login-password', 'incorrect-password')
        await check_password_transition(browser)
        await input_text(browser, '#login-email', 'someone@example.com')
        await click(browser, '#login-email')
        assert await browser.evaluate("!document.querySelector('.login-world').classList.contains('is-private') && !document.querySelector('.login-world').classList.contains('is-surprised')")
        await click(browser, '.login-eye-toggle')
        assert await browser.evaluate("document.querySelector('#login-password').type === 'text'")
        assert await browser.evaluate("document.querySelector('.login-world').classList.contains('is-private')")
        await poll(browser, "!document.querySelector('.login-world').classList.contains('is-surprised') && getComputedStyle(document.querySelector('.friend-pupil')).opacity === '0'", 'pupils fade while password is visible')
        await click(browser, '#login-password')
        assert await browser.evaluate("document.querySelector('.login-world').classList.contains('is-private') && !document.querySelector('.login-world').classList.contains('is-surprised')")
        await click(browser, '.login-eye-toggle')
        assert await browser.evaluate("document.querySelector('#login-password').type === 'password'")
        await poll(browser, "!document.querySelector('.login-world').classList.contains('is-private') && getComputedStyle(document.querySelector('.friend-pupil')).opacity === '1'", 'pupils return while password is hidden')
        await click(browser, '#login-password')
        assert await browser.evaluate("document.querySelector('.login-world').classList.contains('is-surprised')")
        await browser.capture(SCREENSHOTS / 'login_surprised.png')
        await click(browser, '.login-submit')
        await poll(browser, "Boolean(document.querySelector('#login-error'))", 'wrong demo credentials rejected')
        assert await browser.evaluate("location.pathname === '/login' && document.activeElement.id === 'login-password'")

        await click(browser, '.login-help')
        await poll(browser, "Boolean(document.querySelector('[role=dialog]'))", 'help dialog opens')
        await escape(browser)
        await poll(browser, "!document.querySelector('[role=dialog]')", 'Escape closes help')
        assert await browser.evaluate("document.activeElement.classList.contains('login-help')")
        await click(browser, '.login-help')
        await click(browser, '.login-help-dialog button[data-slot=button]')
        await poll(browser, "!document.querySelector('[role=dialog]') && document.querySelector('#login-email').value === 'hello@vibesocial.demo'",
                   'help fills demo credentials')
        assert await browser.evaluate("!document.querySelector('#login-error')")

        await input_text(browser, '#login-password', '')
        await browser.evaluate("document.querySelector('#login-email').focus()")
        await poll(browser, "!document.querySelector('.login-world').classList.contains('is-private')", 'eyes reopen')
        await click(browser, '.vibe-picker button:nth-child(2)')
        assert await browser.evaluate("document.querySelector('.login-world').classList.contains('vibe-1') && document.querySelectorAll('.vibe-picker [aria-pressed=true]').length === 1")
        await browser.send('Input.dispatchKeyEvent', {'type': 'keyDown', 'key': 'Tab', 'code': 'Tab'})
        await browser.send('Input.dispatchKeyEvent', {'type': 'keyUp', 'key': 'Tab', 'code': 'Tab'})
        await browser.send('Input.dispatchKeyEvent', {'type': 'keyDown', 'key': ' ', 'code': 'Space'})
        await browser.send('Input.dispatchKeyEvent', {'type': 'keyUp', 'key': ' ', 'code': 'Space'})
        assert await browser.evaluate("document.querySelector('.vibe-picker button:nth-child(3)').getAttribute('aria-pressed') === 'true'")
        await click(browser, '.vibe-picker button:first-child')

        await browser.evaluate('scrollTo(0,0)')
        await browser.send('Input.dispatchMouseEvent', {'type': 'mouseMoved', 'x': 400, 'y': 400})
        await browser.capture(SCREENSHOTS / 'login_initial.png')
        await poll(browser, "Math.abs(new DOMMatrixReadOnly(getComputedStyle(document.querySelector('.friend-pupil')).transform).m41) > .1", 'eyes follow pointer')
        await browser.send('Input.dispatchMouseEvent', {'type': 'mouseMoved', 'x': 1100, 'y': 400})
        await poll(browser, "new DOMMatrixReadOnly(getComputedStyle(document.querySelector('.friend-pupil')).transform).m41 > 1", 'eyes also follow pointer over form')
        await browser.send('Input.dispatchMouseEvent', {'type': 'mouseMoved', 'x': 60, 'y': 450})
        await poll(browser, "new DOMMatrixReadOnly(getComputedStyle(document.querySelector('.friend-pupil')).transform).m41 < -4", 'eyes visibly turn left toward pointer')

        for width, height in [(1440, 900), (1024, 768), (820, 1180), (390, 844), (320, 640)]:
            await viewport(browser, width, height)
            assert await browser.evaluate("document.documentElement.scrollWidth <= innerWidth"), f'overflow at {width}'
            assert await browser.evaluate("[...document.querySelectorAll('input, .login-submit, .login-eye-toggle')].every(el => {const r=el.getBoundingClientRect(); return r.width>0 && r.left>=0 && r.right<=innerWidth})")
            await browser.evaluate('scrollTo(0,0)')
            await browser.capture(SCREENSHOTS / f'login_{width}.png')
            print(f'PASS responsive login {width}x{height}', flush=True)

        await viewport(browser, 1440, 900)
        await click(browser, '.login-demo-button')
        await click(browser, '.login-submit')
        await poll(browser, "location.pathname === '/' && Boolean(document.querySelector('.nav-menu'))",
                   'valid demo credentials open feed')
        assert await browser.evaluate("Object.keys(localStorage).every(k => !/password|token|auth|session/i.test(k))")
        await theme(browser, 'dark')
        await open_login(browser)
        assert await browser.evaluate("getComputedStyle(document.querySelector('.login-page')).backgroundColor === 'rgb(255, 255, 255)'")
        assert await browser.evaluate("getComputedStyle(document.querySelector('#login-email')).color === 'rgb(48, 49, 58)'")

        await browser.send('Emulation.setEmulatedMedia', {'features': [{'name': 'prefers-reduced-motion', 'value': 'reduce'}]})
        await open_login(browser)
        await input_text(browser, '#login-password', '')
        await browser.evaluate("document.querySelector('#login-email').focus()")
        await browser.send('Input.dispatchMouseEvent', {'type': 'mouseMoved', 'x': 400, 'y': 400})
        await poll(browser, "Math.abs(new DOMMatrixReadOnly(getComputedStyle(document.querySelector('.friend-pupil')).transform).m41) > .1", 'gaze tracking is always enabled with reduced motion')
        assert await browser.evaluate("!document.querySelector('.gaze-toggle') && getComputedStyle(document.querySelector('.vibe-friend')).transitionDuration === '0s'")
        assert await browser.evaluate("getComputedStyle(document.querySelector('.friend-eye')).transitionProperty === 'opacity' && getComputedStyle(document.querySelector('.friend-eye')).transform === 'none'"), 'reduced motion uses fades without scaling'
        await click(browser, '#login-password')
        assert await browser.evaluate("document.querySelector('.login-world').classList.contains('is-surprised') && !document.querySelector('.login-world').classList.contains('is-private')")
        await click(browser, '.login-eye-toggle')
        assert await browser.evaluate("document.querySelector('.login-world').classList.contains('is-private') && !document.querySelector('.login-world').classList.contains('is-surprised')")
        await click(browser, '.login-eye-toggle')
        assert await browser.evaluate("!document.querySelector('.login-world').classList.contains('is-private')")
        await click(browser, '.login-guest-link a')
        await poll(browser, "location.pathname === '/explore' && Boolean(document.querySelector('.nav-menu'))", 'guest link opens Explore')
        await load(browser, '/')
        assert await browser.evaluate("document.documentElement.dataset.theme === 'dark'")
        await viewport(browser, 320, 640)
        await click(browser, 'a[href="/login"]')
        await poll(browser, "location.pathname === '/login'", 'mobile Sign in opens login')
        await click(browser, '#login-password')
        box = await browser.evaluate("document.querySelector('.login-eye-toggle').getBoundingClientRect().toJSON()")
        for visible in [True, False]:
            await browser.send('Input.dispatchTouchEvent', {'type': 'touchStart', 'touchPoints': [{'x': box['x'] + box['width'] / 2, 'y': box['y'] + box['height'] / 2}]})
            await browser.send('Input.dispatchTouchEvent', {'type': 'touchEnd', 'touchPoints': []})
            await poll(browser, f"document.querySelector('.login-eye-toggle').getAttribute('aria-pressed') === '{str(visible).lower()}'", 'touch toggles visibility')
            assert await browser.evaluate("document.activeElement.id === 'login-password'"), 'touch toggle keeps input focused'
        print('PASS validation, privacy, demo flow, keyboard, help, direct loading, theme isolation and reduced motion', flush=True)


if __name__ == '__main__':
    asyncio.run(run())
