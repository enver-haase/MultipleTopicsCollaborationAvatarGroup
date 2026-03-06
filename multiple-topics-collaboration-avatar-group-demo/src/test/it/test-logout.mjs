import { chromium } from 'playwright';

const APP_URL = process.env.APP_URL || 'http://localhost:8080/';
const USERS = [
  { username: 'alice',   password: 'alice' },
  { username: 'bob',     password: 'bob' },
  { username: 'charlie', password: 'charlie' },
];

// Vaadin LoginForm login: fill the form and submit
async function loginUser(page, { username, password }) {
  await page.goto(APP_URL);
  // Wait for Vaadin login form
  await page.waitForSelector('vaadin-login-form', { timeout: 15000 });
  await page.fill('vaadin-login-form input[name="username"]', username);
  await page.fill('vaadin-login-form input[name="password"]', password);
  await page.locator('vaadin-login-form vaadin-button[slot="submit"]').click();
  // Wait for the Vaadin app to load (the RetroChat header)
  await page.waitForSelector('vaadin-horizontal-layout', { timeout: 30000 });
  console.log(`  [${username}] logged in`);
}

// Inspect avatars in all avatar groups, showing names
async function countAvatars(page, username) {
  // Small delay for push updates to propagate
  await page.waitForTimeout(1500);
  const groups = page.locator('vaadin-avatar-group');
  const count = await groups.count();
  const results = [];
  for (let i = 0; i < count; i++) {
    const avatars = groups.nth(i).locator('vaadin-avatar:visible');
    const n = await avatars.count();
    const names = [];
    for (let j = 0; j < n; j++) {
      const name = await avatars.nth(j).getAttribute('name');
      const abbr = await avatars.nth(j).getAttribute('abbr');
      names.push(name || abbr || '?');
    }
    results.push(`${n}[${names.join(',')}]`);
  }
  console.log(`  [${username}] groups(${count}): ${results.join(' | ')}`);
}

// Click a Disable/Enable button for a specific panel (0-based index)
async function togglePanel(page, panelIndex, username) {
  const buttons = page.locator('vaadin-button');
  const allButtons = await buttons.all();
  // Toggle buttons are after the Logout button, one per panel
  // Layout: Logout, then 4 toggle buttons (Disable/Enable)
  const toggleBtn = allButtons[1 + panelIndex];
  const text = await toggleBtn.textContent();
  await toggleBtn.click();
  console.log(`  [${username}] clicked "${text.trim()}" on panel ${panelIndex}`);
}

// Click the Logout button
async function logout(page, username) {
  const logoutBtn = page.locator('vaadin-button', { hasText: 'Logout' });
  await logoutBtn.click();
  // Wait for redirect to Vaadin login form
  await page.waitForSelector('vaadin-login-form', { timeout: 15000 });
  console.log(`  [${username}] logged out (login screen shown)`);
}

async function run() {
  const headless = process.env.CI === 'true' || process.env.HEADLESS === 'true';
  const browser = await chromium.launch({ headless, slowMo: headless ? 0 : 200 });

  // Create 3 separate browser contexts (isolated sessions)
  const contexts = [];
  const pages = [];
  for (const user of USERS) {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    contexts.push(ctx);
    pages.push(page);
  }

  // --- Step 1: Log in all three users ---
  // With PresenceManager, users are automatically present in all topics on login.
  console.log('\n=== Step 1: Logging in all three users ===');
  for (let i = 0; i < USERS.length; i++) {
    await loginUser(pages[i], USERS[i]);
  }

  // Wait for collaboration push updates to propagate
  await pages[0].waitForTimeout(5000);

  // --- Step 2: Check avatar counts - all users present everywhere ---
  console.log('\n=== Step 2: Avatar counts (all 3 users active, auto-presence) ===');
  console.log('  Expected: global=3, panel0=3, panel1=3, panel2=3, panel3=3');
  for (let i = 0; i < USERS.length; i++) {
    await countAvatars(pages[i], USERS[i].username);
  }

  // --- Step 3: alice disables panel 0 (PET2001-talk) ---
  console.log('\n=== Step 3: alice disables panel 0 ===');
  await togglePanel(pages[0], 0, USERS[0].username);

  await pages[0].waitForTimeout(3000);

  console.log('  Expected for alice: global=3(minus panel0), panel0=2, others=3');
  await countAvatars(pages[0], USERS[0].username);
  await countAvatars(pages[1], USERS[1].username);

  // --- Step 4: alice re-enables panel 0 ---
  console.log('\n=== Step 4: alice re-enables panel 0 ===');
  await togglePanel(pages[0], 0, USERS[0].username);

  await pages[0].waitForTimeout(3000);

  console.log('  Expected: back to global=3, panel0=3 for everyone');
  await countAvatars(pages[0], USERS[0].username);

  // --- Step 5: alice disables all four panels ---
  console.log('\n=== Step 5: alice disables all panels (leaves all chats) ===');
  for (let i = 0; i < 4; i++) {
    await togglePanel(pages[0], i, USERS[0].username);
  }

  await pages[0].waitForTimeout(3000);

  console.log('  Expected for alice: global=0 (no topics), all panels=0 (disconnected)');
  console.log('  Expected for others: global=2, all panels=2 (alice gone)');
  await countAvatars(pages[0], USERS[0].username);
  await countAvatars(pages[1], USERS[1].username);

  // --- Step 6: alice re-enables all panels ---
  console.log('\n=== Step 6: alice re-enables all panels ===');
  for (let i = 0; i < 4; i++) {
    await togglePanel(pages[0], i, USERS[0].username);
  }

  await pages[0].waitForTimeout(3000);

  console.log('  Expected: back to global=3, all panels=3');
  await countAvatars(pages[0], USERS[0].username);
  await countAvatars(pages[1], USERS[1].username);

  // --- Step 7: Logout alice ---
  console.log('\n=== Step 7: alice logs out ===');
  await logout(pages[0], USERS[0].username);

  // Wait for deactivation callbacks to fire and push updates
  await pages[1].waitForTimeout(8000);

  // --- Step 8: Check avatar counts after alice logout ---
  console.log('\n=== Step 8: Avatar counts after alice logout ===');
  console.log('  Expected: global=2, all panels=2');
  await countAvatars(pages[1], USERS[1].username);
  await countAvatars(pages[2], USERS[2].username);

  // --- Step 9: Logout bob ---
  console.log('\n=== Step 9: bob logs out ===');
  await logout(pages[1], USERS[1].username);

  await pages[2].waitForTimeout(8000);

  // --- Step 10: Check avatar counts - only charlie remains ---
  console.log('\n=== Step 10: Avatar counts after bob logout ===');
  console.log('  Expected: global=1, all panels=1');
  await countAvatars(pages[2], USERS[2].username);

  // --- Step 11: Cleanup ---
  console.log('\n=== Step 11: Logging out charlie and closing ===');
  await logout(pages[2], USERS[2].username);

  // Keep browser open briefly so you can see final state
  await pages[2].waitForTimeout(2000);

  for (const ctx of contexts) {
    await ctx.close();
  }
  await browser.close();

  console.log('\n=== Test complete ===');
}

run().catch(err => {
  console.error('Test failed:', err);
  process.exit(1);
});
