"""Browser integration check against the local preview profile (ports 5177/8087)."""
import json, os, time
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
ROOT = Path(__file__).resolve().parents[1]
OUT = Path(os.environ.get('ARENA_SCREENSHOTS', '/tmp/offbyone-screenshots')); OUT.mkdir(parents=True, exist_ok=True)
BASE = os.environ.get('ARENA_BASE', 'http://127.0.0.1:5177')
API = os.environ.get('ARENA_API', 'http://127.0.0.1:8087')
BANK = {p['slug']: p for p in json.loads((ROOT/'backend/src/main/resources/sql-problems.json').read_text())}

def enter(page, name):
    page.get_by_label('Guest name').fill(name)
    page.get_by_role('button', name='Enter', exact=True).click()
    expect(page.get_by_role('heading', name='Enter the arena. Outquery everyone.')).to_be_visible(timeout=30000)

def write(page, sql):
    editor = page.locator('.monaco-editor textarea').first
    editor.wait_for(state='attached', timeout=30000)
    editor.focus(); page.keyboard.press('Control+a'); page.keyboard.insert_text(sql)

def state(page):
    return page.evaluate("""async (base) => {const user=localStorage.getItem('username'); const id=localStorage.getItem('obo:match:'+user);return (await fetch(base+'/api/sql-arena/'+id,{headers:{Authorization:'Bearer '+localStorage.getItem('token')}})).json()}""", API)

with sync_playwright() as pw:
    chromium = os.environ.get('ARENA_CHROMIUM')
    browser = pw.chromium.launch(**({'executable_path':chromium} if chromium else {}), headless=True)
    a = browser.new_context(viewport={'width':1440,'height':1000}, reduced_motion='reduce')
    b = browser.new_context(viewport={'width':1280,'height':900}, reduced_motion='reduce')
    page=a.new_page(); other=b.new_page(); errors=[]
    page.on('pageerror', lambda e:errors.append(str(e))); other.on('pageerror',lambda e:errors.append(str(e)))
    suffix=str(int(time.time()))[-8:]; name='Query_'+suffix; rival='Rival_'+suffix
    page.goto(BASE+'/#/'); page.get_by_label('Guest name').wait_for()
    page.screenshot(path=str(OUT/'login-desktop.png'),full_page=True)
    page.evaluate("localStorage.setItem('token', 'stale-session'); localStorage.setItem('username', 'old-guest')")
    page.goto(BASE+'/#/sql?code=TESTCODE')
    expect(page.get_by_role('status')).to_contain_text('Your saved session is no longer valid',timeout=15000)
    assert page.evaluate("localStorage.getItem('token')") is None
    enter(page,name)
    expect(page.get_by_label('Invite code')).to_have_value('TESTCODE')
    expect(page.get_by_role('navigation',name='Main navigation')).to_be_visible()
    expect(page.get_by_role('link',name='Java Practice')).to_have_count(0)
    page.get_by_role('button',name='Switch to light theme').click()
    expect(page.locator('html')).to_have_attribute('data-theme','light')
    page.screenshot(path=str(OUT/'arena-light.png'),full_page=True)
    page.reload()
    expect(page.locator('html')).to_have_attribute('data-theme','light')
    page.get_by_role('link',name='My Progress').click()
    expect(page.locator('.progress-stats')).to_be_visible()
    page.screenshot(path=str(OUT/'profile-desktop.png'),full_page=True)
    page.get_by_role('link',name='SQL Arena').click()
    expect(page.locator('.challenge-row')).to_have_count(12)
    page.screenshot(path=str(OUT/'arena-desktop.png'),full_page=True)
    page.get_by_label('Search challenges').fill('second highest')
    page.get_by_role('button',name='Second Highest Salary',exact=False).click()
    write(page,'SELECT 88000')
    page.get_by_role('button',name='Run sample').click();expect(page.locator('.verdict-heading strong')).to_have_text('sample passed')
    page.get_by_role('button',name='Submit query').click();expect(page.locator('.verdict-heading strong')).to_have_text('wrong answer')
    write(page,BANK['sql-second-highest']['referenceQuery'])
    page.get_by_role('button',name='Submit query').click();expect(page.locator('.verdict-heading strong')).to_have_text('accepted')
    page.screenshot(path=str(OUT/'practice-light.png'),full_page=True)
    page.get_by_role('button',name='Switch to dark theme').click()
    page.screenshot(path=str(OUT/'practice-desktop.png'),full_page=True)
    page.get_by_role('button',name='All challenges').click()
    page.get_by_role('button',name='Deploy a duel').click()
    expect(page.locator('.invite-code')).to_be_visible()
    code=page.locator('.invite-code strong').inner_text()
    other.goto(BASE+'/#/sql?code='+code);enter(other,rival)
    expect(other.get_by_label('Invite code')).to_have_value(code)
    other.get_by_role('button',name='Join operation').click()
    expect(page.locator('.match-skill')).to_contain_text('DUEL LEVEL 5/10')
    page.get_by_label('Duel skill level').fill('3')
    page.get_by_role('button',name='Save level').click()
    expect(page.locator('.match-skill')).to_contain_text('DUEL LEVEL 4/10')
    page.get_by_role('button',name='Start the duel').click(timeout=15000)
    expect(page.get_by_role('heading',name='Every query counts.')).to_be_visible()
    expect(other.get_by_role('heading',name='Every query counts.')).to_be_visible(timeout=15000)
    write(page,'SELECT 123 AS draft')
    page.reload(); expect(page.get_by_role('heading',name='Every query counts.')).to_be_visible(timeout=15000)
    expect(page.locator('.monaco-editor')).to_contain_text('SELECT 123 AS draft',timeout=30000)
    page.screenshot(path=str(OUT/'duel-desktop.png'),full_page=True)
    page.get_by_role('button',name='Focus editor').click()
    expect(page.locator('.challenge-panel')).to_be_hidden()
    page.get_by_role('button',name='Show schema').click()
    expect(page.locator('.challenge-panel')).to_be_visible()
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Desktop overflow'
    for round_number in [1,2]:
        if round_number==2: expect(page.locator('.workspace-heading .arena-eyebrow')).to_contain_text('ROUND 2',timeout=20000)
        current=state(page);write(page,BANK[current['problem']['slug']]['referenceQuery'])
        page.get_by_role('button',name='Submit query').click()
        if round_number==1:expect(page.locator('.round-banner')).to_contain_text(name+' takes the round.')
    expect(page.get_by_role('heading',name='Well queried.')).to_be_visible(timeout=15000)
    expect(other.get_by_role('heading',name='The next one is yours.')).to_be_visible(timeout=15000)
    page.locator('.review-round summary').first.click();page.screenshot(path=str(OUT/'results-desktop.png'),full_page=True)
    page.get_by_role('button',name='Rematch',exact=False).click()
    other.get_by_role('button',name='Rematch',exact=False).click()
    expect(page.get_by_role('button',name='Start the duel')).to_be_enabled(timeout=15000)
    assert state(page)['id']==state(other)['id'], 'Rematch rooms differ'
    assert not errors, errors
    print('PASS: stale-session recovery with invite retained, practice, hidden cases, anonymous invite, two-player duel, reload/draft recovery, results, rematch, desktop layout and focus mode, browser errors')
    print('Screenshots:',OUT)
    browser.close()
