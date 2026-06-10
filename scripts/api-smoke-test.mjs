#!/usr/bin/env node

import { readFile, writeFile } from 'node:fs/promises';

const API_BASE_URL = (process.env.API_BASE_URL || 'http://localhost:8080/api').replace(/\/$/, '');
const USER_ACCOUNT = process.env.API_USER || '20240001';
const ADMIN_ACCOUNT = process.env.API_ADMIN || 'admin001';
const PASSWORD = process.env.API_PASSWORD || '123456';
const RUN_MUTATING = process.env.RUN_MUTATING === '1';
const TEST_PHASE = process.env.TEST_PHASE || (RUN_MUTATING ? 'full' : 'smoke');
const STATE_FILE = new URL('.api-smoke-state.json', import.meta.url);

const results = [];
let userToken = '';
let adminToken = '';

class TestError extends Error {}

function assert(condition, message) {
  if (!condition) throw new TestError(message);
}

function hasArray(value) {
  return Array.isArray(value);
}

function pageRecords(page) {
  return Array.isArray(page) ? page : page?.records || page?.list || [];
}

async function request(method, path, options = {}) {
  const url = new URL(API_BASE_URL + path);
  if (options.query) {
    for (const [key, value] of Object.entries(options.query)) {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    }
  }

  const headers = {};
  if (options.token) headers.Authorization = `Bearer ${options.token}`;

  let body;
  if (options.formData) {
    body = options.formData;
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.body);
  }

  const response = await fetch(url, { method, headers, body });
  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    throw new TestError(`Response is not JSON: HTTP ${response.status}, body=${text.slice(0, 120)}`);
  }

  if (!response.ok) {
    throw new TestError(`HTTP ${response.status}: ${payload?.message || text}`);
  }
  if (!payload || payload.code !== 200) {
    throw new TestError(`API code is not 200: ${payload?.code}, message=${payload?.message}`);
  }
  return payload.data;
}

async function test(id, moduleName, name, fn) {
  const startedAt = Date.now();
  try {
    await fn();
    results.push({ id, moduleName, name, status: 'PASS', ms: Date.now() - startedAt });
  } catch (error) {
    results.push({
      id,
      moduleName,
      name,
      status: 'FAIL',
      ms: Date.now() - startedAt,
      error: error?.message || String(error),
    });
  }
}

function skip(id, moduleName, name, reason) {
  results.push({ id, moduleName, name, status: 'SKIP', ms: 0, error: reason });
}

async function login(account) {
  return request('POST', '/auth/login', {
    body: { account, password: PASSWORD },
  });
}

async function findByName(path, token, name) {
  const data = await request('GET', path, { token });
  assert(Array.isArray(data), `${path} should return array`);
  return data.find((item) => item.name === name || item.title === name);
}

function onePixelPngBlob() {
  const bytes = Uint8Array.from([
    137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1,
    0, 0, 0, 1, 8, 6, 0, 0, 0, 31, 21, 196, 137, 0, 0, 0, 10, 73, 68, 65, 84,
    120, 156, 99, 0, 1, 0, 0, 5, 0, 1, 13, 10, 45, 180, 0, 0, 0, 0, 73, 69,
    78, 68, 174, 66, 96, 130,
  ]);
  return new Blob([bytes], { type: 'image/png' });
}

async function readState() {
  try {
    return JSON.parse(await readFile(STATE_FILE, 'utf8'));
  } catch {
    return {};
  }
}

async function writeState(state) {
  await writeFile(STATE_FILE, `${JSON.stringify(state, null, 2)}\n`, 'utf8');
  console.log(`Saved test state: ${STATE_FILE.pathname}`);
}

async function run() {
  console.log(`API smoke test base: ${API_BASE_URL}`);
  console.log(`Test phase: ${TEST_PHASE}`);

  await test('API-001', 'auth', 'user login', async () => {
    const data = await login(USER_ACCOUNT);
    assert(data.token, 'token is missing');
    assert(data.username === USER_ACCOUNT, 'username mismatch');
    userToken = data.token;
  });

  await test('API-002', 'auth', 'admin login', async () => {
    const data = await login(ADMIN_ACCOUNT);
    assert(data.token, 'token is missing');
    assert(data.role === 'admin', 'role should be admin');
    adminToken = data.token;
  });

  await test('API-003', 'auth', 'profile', async () => {
    const data = await request('GET', '/auth/profile', { token: userToken });
    assert(data.username === USER_ACCOUNT, 'profile username mismatch');
  });

  await test('API-004', 'auth', 'stats', async () => {
    const data = await request('GET', '/auth/stats', { token: userToken });
    assert(typeof data.favoriteCount === 'number', 'favoriteCount should be number');
    assert(typeof data.reviewCount === 'number', 'reviewCount should be number');
  });

  await test('API-005', 'canteen', 'banners', async () => {
    const data = await request('GET', '/canteens/banners');
    assert(hasArray(data), 'banners should be array');
  });

  await test('API-006', 'canteen', 'canteens', async () => {
    const data = await request('GET', '/canteens');
    assert(hasArray(data), 'canteens should be array');
    assert(data.some((item) => item.id === 1), 'canteen id=1 missing');
  });

  await test('API-007', 'canteen', 'canteen images', async () => {
    const data = await request('GET', '/canteens/images');
    assert(data && typeof data === 'object', 'images map should be object');
    assert(Object.keys(data).length > 0, 'images map is empty');
  });

  await test('API-008', 'canteen', 'canteens all', async () => {
    const data = await request('GET', '/canteens/all');
    assert(hasArray(data), 'canteens/all should be array');
    assert(data.some((item) => hasArray(item.stalls)), 'stalls should exist');
  });

  await test('API-009', 'canteen', 'stall detail', async () => {
    const data = await request('GET', '/canteens/stallDetail', {
      query: { canteenName: '明湖餐厅', stallName: '明湖一层基本伙食窗口' },
    });
    assert(data.id === 1, 'stall id should be 1');
  });

  await test('API-010', 'stall', 'stalls by canteen', async () => {
    const data = await request('GET', '/stalls', { query: { canteenId: 1 } });
    assert(hasArray(data), 'stalls should be array');
    assert(data.some((item) => item.id === 1), 'stall id=1 missing');
  });

  await test('API-011', 'dish', 'hot dishes', async () => {
    const data = await request('GET', '/dishes/hot');
    assert(hasArray(data), 'hot dishes should be array');
    assert(data.length > 0, 'hot dishes is empty');
  });

  await test('API-012', 'dish', 'dish search', async () => {
    const data = await request('GET', '/dishes', { query: { page: 1, pageSize: 10, keyword: '牛肉' } });
    assert(hasArray(pageRecords(data)), 'dish page records should be array');
  });

  await test('API-013', 'dish', 'dish detail', async () => {
    const data = await request('GET', '/dishes/1', { token: userToken });
    assert(data.id === 1, 'dish id should be 1');
    assert(hasArray(data.ratingDistribution), 'ratingDistribution should be array');
  });

  await test('API-014', 'dish', 'add view count', async () => {
    await request('POST', '/dishes/1/view', { token: userToken });
  });

  await test('API-015', 'favorite', 'favorite list', async () => {
    const data = await request('GET', '/favorites', {
      token: userToken,
      query: { page: 1, pageSize: 50 },
    });
    assert(hasArray(pageRecords(data)), 'favorite records should be array');
  });

  await test('API-016', 'favorite', 'toggle favorite and restore', async () => {
    const first = await request('POST', '/favorites/toggle', {
      token: userToken,
      body: { dishId: 1 },
    });
    const second = await request('POST', '/favorites/toggle', {
      token: userToken,
      body: { dishId: 1 },
    });
    assert(typeof first.favorited === 'boolean', 'first favorited should be boolean');
    assert(second.favorited === !first.favorited, 'second toggle should restore previous state');
  });

  await test('API-017', 'review', 'review list', async () => {
    const data = await request('GET', '/dishes/6/reviews', {
      query: { page: 1, pageSize: 20 },
    });
    assert(hasArray(pageRecords(data)), 'review records should be array');
  });

  await test('API-018', 'upload', 'image upload', async () => {
    const form = new FormData();
    form.append('file', onePixelPngBlob(), 'api-smoke.png');
    const data = await request('POST', '/upload/image', {
      token: userToken,
      formData: form,
    });
    assert(data.url && data.relativeUrl, 'upload result should contain url and relativeUrl');
  });

  await test('API-019', 'admin', 'admin users', async () => {
    const data = await request('GET', '/admin/users', {
      token: adminToken,
      query: { page: 1, pageSize: 10 },
    });
    assert(hasArray(pageRecords(data)), 'admin users records should be array');
  });

  await test('API-020', 'admin', 'admin canteens', async () => {
    const data = await request('GET', '/admin/canteens', { token: adminToken });
    assert(hasArray(data), 'admin canteens should be array');
  });

  await test('API-021', 'admin', 'admin stalls', async () => {
    const data = await request('GET', '/admin/stalls', { token: adminToken });
    assert(hasArray(data), 'admin stalls should be array');
  });

  await test('API-022', 'admin', 'admin dishes', async () => {
    const data = await request('GET', '/admin/dishes', { token: adminToken });
    assert(hasArray(data), 'admin dishes should be array');
  });

  await test('API-023', 'admin', 'admin reviews', async () => {
    const data = await request('GET', '/admin/reviews', {
      token: adminToken,
      query: { page: 1, pageSize: 10 },
    });
    assert(hasArray(pageRecords(data)), 'admin review records should be array');
  });

  await test('API-024', 'admin', 'admin banners', async () => {
    const data = await request('GET', '/admin/banners', { token: adminToken });
    assert(hasArray(data), 'admin banners should be array');
  });

  await test('API-025', 'admin', 'toggle review hide and restore', async () => {
    await request('PUT', '/admin/reviews/1/hide', { token: adminToken });
    await request('PUT', '/admin/reviews/1/hide', { token: adminToken });
  });

  await test('API-026', 'admin', 'stats overview placeholder', async () => {
    const data = await request('GET', '/admin/stats/overview', { token: adminToken });
    assert(typeof data === 'string', 'current stats overview should be placeholder string');
  });

  if (TEST_PHASE === 'add') {
    await runAddPhase();
    printResults();
    return;
  }

  if (TEST_PHASE === 'cleanup') {
    await runCleanupPhase();
    printResults();
    return;
  }

  if (RUN_MUTATING) {
    const suffix = Date.now();

    await test('API-027', 'list', 'create/get/share/delete list', async () => {
      const created = await request('POST', '/lists', {
        token: userToken,
        body: {
          name: `api-smoke-list-${suffix}`,
          description: 'api smoke test',
          dishIds: [1, 2, 6],
        },
      });
      assert(created.id, 'created list id is missing');
      const detail = await request('GET', `/lists/${created.id}`, { token: userToken });
      assert(detail.shareToken, 'shareToken is missing');
      const shared = await request('GET', `/lists/share/${detail.shareToken}`);
      assert(shared.id === created.id, 'shared list id mismatch');
      await request('DELETE', `/lists/${created.id}`, { token: userToken });
    });

    await test('API-028', 'admin', 'create/update/delete canteen', async () => {
      const name = `api-smoke-canteen-${suffix}`;
      await request('POST', '/admin/canteens', {
        token: adminToken,
        body: {
          name,
          images: '["/images/seed/canteens/canteen-dining-hall.jpg"]',
          location: 'api smoke',
          description: 'api smoke test',
          sortOrder: 999,
          status: 'open',
        },
      });
      const created = await findByName('/admin/canteens', adminToken, name);
      assert(created?.id, 'created canteen not found');
      await request('PUT', `/admin/canteens/${created.id}`, {
        token: adminToken,
        body: { ...created, description: 'api smoke updated', images: JSON.stringify(['/images/seed/canteens/canteen-dining-hall.jpg']) },
      });
      await request('DELETE', `/admin/canteens/${created.id}`, { token: adminToken });
    });

    await test('API-029', 'admin', 'create/update/delete stall', async () => {
      const name = `api-smoke-stall-${suffix}`;
      await request('POST', '/admin/stalls', {
        token: adminToken,
        body: {
          canteenId: 1,
          name,
          images: '["/images/seed/canteens/canteen-food-counter.jpg"]',
          location: 'api smoke',
          description: 'api smoke test',
          avgRating: 0,
          sortOrder: 999,
          status: 'open',
        },
      });
      const created = await findByName('/admin/stalls', adminToken, name);
      assert(created?.id, 'created stall not found');
      await request('PUT', `/admin/stalls/${created.id}`, {
        token: adminToken,
        body: { ...created, description: 'api smoke updated', images: JSON.stringify(['/images/seed/canteens/canteen-food-counter.jpg']) },
      });
      await request('DELETE', `/admin/stalls/${created.id}`, { token: adminToken });
    });

    await test('API-030', 'admin', 'create/update/delete banner', async () => {
      const title = `api-smoke-banner-${suffix}`;
      await request('POST', '/admin/banners', {
        token: adminToken,
        body: {
          title,
          subtitle: 'api smoke test',
          type: 'dish',
          targetId: 1,
          targetUrl: null,
          canteenId: 1,
          sortOrder: 999,
          status: 'enabled',
          images: '["/images/seed/dishes/tomato-egg.jpg"]',
        },
      });
      const created = await findByName('/admin/banners', adminToken, title);
      assert(created?.id, 'created banner not found');
      await request('PUT', `/admin/banners/${created.id}`, {
        token: adminToken,
        body: {
          title,
          subtitle: 'api smoke updated',
          type: 'dish',
          targetId: 1,
          targetUrl: null,
          canteenId: 1,
          sortOrder: 999,
          status: 'enabled',
          images: '["/images/seed/dishes/tomato-egg.jpg"]',
        },
      });
      await request('DELETE', `/admin/banners/${created.id}`, { token: adminToken });
    });

    await test('API-031', 'admin', 'create/update/off dish', async () => {
      const name = `api-smoke-dish-${suffix}`;
      await request('POST', '/admin/dishes', {
        token: adminToken,
        body: {
          stallId: 1,
          name,
          price: 1200,
          description: 'api smoke test',
          images: ['/images/seed/dishes/tomato-egg.jpg'],
          tags: 'daily,recommended',
          status: 'on',
        },
      });
      const created = await findByName('/admin/dishes', adminToken, name);
      assert(created?.id, 'created dish not found');
      await request('PUT', `/admin/dishes/${created.id}`, {
        token: adminToken,
        body: {
          stallId: 1,
          name,
          price: 1300,
          description: 'api smoke updated',
          images: ['/images/seed/dishes/tomato-egg.jpg'],
          tags: 'daily,recommended',
          status: 'on',
        },
      });
      await request('DELETE', `/admin/dishes/${created.id}`, { token: adminToken });
    });
  } else {
    skip('API-027', 'list', 'create/get/share/delete list', 'Set RUN_MUTATING=1 to run');
    skip('API-028', 'admin', 'create/update/delete canteen', 'Set RUN_MUTATING=1 to run');
    skip('API-029', 'admin', 'create/update/delete stall', 'Set RUN_MUTATING=1 to run');
    skip('API-030', 'admin', 'create/update/delete banner', 'Set RUN_MUTATING=1 to run');
    skip('API-031', 'admin', 'create/update/off dish', 'Set RUN_MUTATING=1 to run');
  }

  printResults();
}

async function runAddPhase() {
  const suffix = Date.now();
  const state = {
    suffix,
    createdAt: new Date().toISOString(),
  };

  await test('API-027', 'list', 'add persistent canteen', async () => {
    const name = `api-smoke-canteen-${suffix}`;
    await request('POST', '/admin/canteens', {
      token: adminToken,
      body: {
        name,
        images: '["/images/seed/canteens/canteen-dining-hall.jpg"]',
        location: 'api smoke',
        description: 'api smoke persistent test',
        sortOrder: 999,
        status: 'open',
      },
    });
    const created = await findByName('/admin/canteens', adminToken, name);
    assert(created?.id, 'created canteen not found');
    state.canteenId = created.id;
    state.canteenName = name;
  });

  await test('API-028', 'admin', 'add persistent stall', async () => {
    const name = `api-smoke-stall-${suffix}`;
    await request('POST', '/admin/stalls', {
      token: adminToken,
      body: {
        canteenId: 1,
        name,
        images: '["/images/seed/canteens/canteen-food-counter.jpg"]',
        location: 'api smoke',
        description: 'api smoke persistent test',
        avgRating: 0,
        sortOrder: 999,
        status: 'open',
      },
    });
    const created = await findByName('/admin/stalls', adminToken, name);
    assert(created?.id, 'created stall not found');
    state.stallId = created.id;
    state.stallName = name;
  });

  await test('API-029', 'admin', 'add persistent banner', async () => {
    const title = `api-smoke-banner-${suffix}`;
    await request('POST', '/admin/banners', {
      token: adminToken,
      body: {
        title,
        subtitle: 'api smoke persistent test',
        type: 'dish',
        targetId: 1,
        targetUrl: null,
        canteenId: 1,
        sortOrder: 999,
        status: 'enabled',
        images: '["/images/seed/dishes/tomato-egg.jpg"]',
      },
    });
    const created = await findByName('/admin/banners', adminToken, title);
    assert(created?.id, 'created banner not found');
    state.bannerId = created.id;
    state.bannerTitle = title;
  });

  await test('API-030', 'admin', 'add persistent dish', async () => {
    const name = `api-smoke-dish-${suffix}`;
    await request('POST', '/admin/dishes', {
      token: adminToken,
      body: {
        stallId: 1,
        name,
        price: 1200,
        description: 'api smoke persistent test',
        images: ['/images/seed/dishes/tomato-egg.jpg'],
        tags: 'daily,recommended',
        status: 'on',
      },
    });
    const created = await findByName('/admin/dishes', adminToken, name);
    assert(created?.id, 'created dish not found');
    state.dishId = created.id;
    state.dishName = name;
  });

  await test('API-031', 'list', 'add persistent list', async () => {
    const created = await request('POST', '/lists', {
      token: userToken,
      body: {
        name: `api-smoke-list-${suffix}`,
        description: 'api smoke persistent test',
        dishIds: [state.dishId, 1, 2],
      },
    });
    assert(created.id, 'created list id is missing');
    state.listId = created.id;
  });

  await test('API-032', 'favorite', 'add persistent favorite', async () => {
    const result = await request('POST', '/favorites/toggle', {
      token: userToken,
      body: { dishId: state.dishId },
    });
    assert(result.favorited === true, 'created dish should become favorited');
    state.favoriteDishId = state.dishId;
  });

  await test('API-033', 'review', 'add persistent review', async () => {
    const content = `api smoke review ${suffix}`;
    await request('POST', '/reviews', {
      token: userToken,
      body: {
        dishId: state.dishId,
        rating: 5,
        content,
        images: [],
      },
    });
    const page = await request('GET', '/admin/reviews', {
      token: adminToken,
      query: { page: 1, pageSize: 500 },
    });
    const review = pageRecords(page).find((item) => item.content === content);
    assert(review?.id, 'created review not found');
    state.reviewId = review.id;
    state.reviewContent = content;
  });

  await writeState(state);
}

async function runCleanupPhase() {
  const state = await readState();
  assert(Object.keys(state).length > 0, 'No state file found. Run TEST_PHASE=add first.');

  await test('API-027', 'cleanup', 'delete persistent review', async () => {
    if (!state.reviewId) return;
    await request('DELETE', `/admin/reviews/${state.reviewId}`, { token: adminToken });
  });

  await test('API-028', 'cleanup', 'remove persistent favorite', async () => {
    if (!state.favoriteDishId) return;
    const result = await request('POST', '/favorites/toggle', {
      token: userToken,
      body: { dishId: state.favoriteDishId },
    });
    assert(result.favorited === false, 'favorite should be removed');
  });

  await test('API-029', 'cleanup', 'delete persistent list', async () => {
    if (!state.listId) return;
    await request('DELETE', `/lists/${state.listId}`, { token: userToken });
  });

  await test('API-030', 'cleanup', 'off persistent dish', async () => {
    if (!state.dishId) return;
    await request('DELETE', `/admin/dishes/${state.dishId}`, { token: adminToken });
  });

  await test('API-031', 'cleanup', 'delete persistent banner', async () => {
    if (!state.bannerId) return;
    await request('DELETE', `/admin/banners/${state.bannerId}`, { token: adminToken });
  });

  await test('API-032', 'cleanup', 'delete persistent stall', async () => {
    if (!state.stallId) return;
    await request('DELETE', `/admin/stalls/${state.stallId}`, { token: adminToken });
  });

  await test('API-033', 'cleanup', 'delete persistent canteen', async () => {
    if (!state.canteenId) return;
    await request('DELETE', `/admin/canteens/${state.canteenId}`, { token: adminToken });
  });
}

function printResults() {
  const passed = results.filter((item) => item.status === 'PASS').length;
  const failed = results.filter((item) => item.status === 'FAIL').length;
  const skipped = results.filter((item) => item.status === 'SKIP').length;

  console.log('\nAPI smoke test results');
  console.table(results.map((item) => ({
    id: item.id,
    module: item.moduleName,
    name: item.name,
    status: item.status,
    ms: item.ms,
    error: item.error || '',
  })));
  console.log(`Summary: ${passed} passed, ${failed} failed, ${skipped} skipped`);

  if (failed > 0) process.exitCode = 1;
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
