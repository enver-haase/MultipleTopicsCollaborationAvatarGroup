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

// Type in a specific chat panel's text field (0-based panel index)
async function typeInPanel(page, panelIndex, text, username) {
  // Vaadin text-field has a shadow DOM; target the inner input
  const fields = page.locator('vaadin-text-field');
  const field = fields.nth(panelIndex);
  await field.waitFor({ timeout: 10000 });
  const input = field.locator('input');
  await input.fill(text);
  console.log(`  [${username}] typed "${text}" in panel ${panelIndex}`);
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
  console.log('\n=== Step 1: Logging in all three users ===');
  for (let i = 0; i < USERS.length; i++) {
    await loginUser(pages[i], USERS[i]);
  }

  // --- Step 2: Each user types in a different panel ---
  console.log('\n=== Step 2: Each user types in a different panel ===');
  // alice types in panel 0 (PET2001-talk)
  await typeInPanel(pages[0], 0, 'Hello from alice!', USERS[0].username);
  // bob types in panel 1 (VIC20-talk)
  await typeInPanel(pages[1], 1, 'Hello from bob!', USERS[1].username);
  // charlie types in panel 2 (C64-talk)
  await typeInPanel(pages[2], 2, 'Hello from charlie!', USERS[2].username);

  // Wait for collaboration push updates to propagate
  await pages[0].waitForTimeout(5000);

  // --- Step 3: Check avatar counts from each user's perspective ---
  console.log('\n=== Step 3: Avatar counts (all 3 users active) ===');
  console.log('  Expected: global=3, panel0=1, panel1=1, panel2=1, panel3=0');
  for (let i = 0; i < USERS.length; i++) {
    await countAvatars(pages[i], USERS[i].username);
  }

  // --- Step 4: Logout alice ---
  console.log('\n=== Step 4: alice logs out ===');
  await logout(pages[0], USERS[0].username);

  // Wait for deactivation callbacks to fire and push updates
  await pages[1].waitForTimeout(8000);

  // --- Step 5: Check avatar counts after alice logout ---
  console.log('\n=== Step 5: Avatar counts after alice logout ===');
  console.log('  Expected: global=2, panel0=0, panel1=1, panel2=1, panel3=0');
  await countAvatars(pages[1], USERS[1].username);
  await countAvatars(pages[2], USERS[2].username);

  // --- Step 6: Logout bob ---
  console.log('\n=== Step 6: bob logs out ===');
  await logout(pages[1], USERS[1].username);

  await pages[2].waitForTimeout(8000);

  // --- Step 7: Check avatar counts - only charlie remains ---
  console.log('\n=== Step 7: Avatar counts after bob logout ===');
  console.log('  Expected: global=1, panel0=0, panel1=0, panel2=1, panel3=0');
  await countAvatars(pages[2], USERS[2].username);

  // --- Step 8: Cleanup ---
  console.log('\n=== Step 8: Logging out charlie and closing ===');
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
