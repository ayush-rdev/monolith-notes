/**
 * Monolith Cloud Vault — Zero-Dependency High-Performance Sync Server
 * Built with native Node.js (node:http, node:crypto, node:sqlite).
 *
 * Runs instantly with: node server.js
 * No external npm packages or node_modules required!
 */

const http = require('node:http');
const crypto = require('node:crypto');
const { DatabaseSync } = require('node:sqlite');
const path = require('node:path');
const fs = require('node:fs');

const PORT = process.env.PORT || 8080;
const DB_PATH = process.env.DB_PATH || path.join(__dirname, 'vault.db');

// Initialize SQLite database
const db = new DatabaseSync(DB_PATH);
db.exec('PRAGMA journal_mode = WAL;');
db.exec('PRAGMA foreign_keys = ON;');

db.exec(`
  CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email TEXT UNIQUE NOT NULL COLLATE NOCASE,
    password_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    created_at INTEGER NOT NULL
  );

  CREATE TABLE IF NOT EXISTS tokens (
    token TEXT PRIMARY KEY,
    user_id INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
  );

  CREATE TABLE IF NOT EXISTS vault_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    file_name TEXT NOT NULL,
    data_base64 TEXT NOT NULL,
    client_modified INTEGER NOT NULL,
    server_modified INTEGER NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    UNIQUE(user_id, file_name),
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
  );

  CREATE INDEX IF NOT EXISTS idx_vault_items_user_modified ON vault_items(user_id, server_modified);
`);

console.log(`[Monolith Cloud Vault] Database initialized at: ${DB_PATH}`);

function hashPassword(password, salt) {
  return crypto.scryptSync(password, salt, 64).toString('hex');
}

function parseBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', chunk => {
      body += chunk;
      if (body.length > 50 * 1024 * 1024) { // 50MB limit
        reject(new Error('Payload too large'));
      }
    });
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch (err) {
        reject(new Error('Invalid JSON payload'));
      }
    });
    req.on('error', reject);
  });
}

function sendJson(res, statusCode, data) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization'
  });
  res.end(JSON.stringify(data));
}

function authenticate(req, query, body) {
  let token = null;
  const authHeader = req.headers['authorization'];
  if (authHeader && authHeader.startsWith('Bearer ')) {
    token = authHeader.slice(7).trim();
  } else if (query.get('token')) {
    token = query.get('token');
  } else if (body && body.token) {
    token = body.token;
  }

  if (!token) return null;

  const stmt = db.prepare('SELECT user_id FROM tokens WHERE token = ?');
  const row = stmt.get(token);
  return row ? row.user_id : null;
}

const server = http.createServer(async (req, res) => {
  // Handle CORS preflight
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization'
    });
    return res.end();
  }

  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = url.pathname;

  try {
    // 1. Health check
    if (req.method === 'GET' && pathname === '/api/v1/health') {
      return sendJson(res, 200, {
        status: 'ok',
        app: 'Monolith Cloud Vault Server',
        version: '1.0.0',
        timestamp: Date.now()
      });
    }

    // 2. User registration
    if (req.method === 'POST' && pathname === '/api/v1/auth/register') {
      const body = await parseBody(req);
      const email = (body.email || '').trim().toLowerCase();
      const password = body.password || '';

      if (!email || !email.includes('@') || password.length < 6) {
        return sendJson(res, 400, {
          success: false,
          message: 'Valid email and minimum 6-character password required'
        });
      }

      const existing = db.prepare('SELECT id FROM users WHERE email = ?').get(email);
      if (existing) {
        return sendJson(res, 409, {
          success: false,
          message: 'An account with this email already exists. Please log in.'
        });
      }

      const salt = crypto.randomBytes(16).toString('hex');
      const passwordHash = hashPassword(password, salt);
      const now = Date.now();

      const insertUser = db.prepare('INSERT INTO users (email, password_hash, salt, created_at) VALUES (?, ?, ?, ?)');
      const result = insertUser.run(email, passwordHash, salt, now);
      const userId = Number(result.lastInsertRowid);

      const token = crypto.randomBytes(32).toString('hex');
      db.prepare('INSERT INTO tokens (token, user_id, created_at) VALUES (?, ?, ?)').run(token, userId, now);

      return sendJson(res, 201, {
        success: true,
        message: 'Account created successfully',
        token,
        email
      });
    }

    // 3. User login
    if (req.method === 'POST' && pathname === '/api/v1/auth/login') {
      const body = await parseBody(req);
      const email = (body.email || '').trim().toLowerCase();
      const password = body.password || '';

      const user = db.prepare('SELECT id, password_hash, salt FROM users WHERE email = ?').get(email);
      if (!user) {
        return sendJson(res, 401, {
          success: false,
          message: 'Invalid email or password'
        });
      }

      const testHash = hashPassword(password, user.salt);
      if (testHash !== user.password_hash) {
        return sendJson(res, 401, {
          success: false,
          message: 'Invalid email or password'
        });
      }

      const token = crypto.randomBytes(32).toString('hex');
      db.prepare('INSERT INTO tokens (token, user_id, created_at) VALUES (?, ?, ?)').run(token, user.id, Date.now());

      return sendJson(res, 200, {
        success: true,
        message: 'Login successful',
        token,
        email
      });
    }

    // 4. Sync: Push items (Client -> Server)
    if (req.method === 'POST' && pathname === '/api/v1/sync/push') {
      const body = await parseBody(req);
      const userId = authenticate(req, url.searchParams, body);

      if (!userId) {
        return sendJson(res, 401, { success: false, message: 'Unauthorized: valid token required' });
      }

      const items = Array.isArray(body.items) ? body.items : [];
      const now = Date.now();
      let pushedCount = 0;

      const upsert = db.prepare(`
        INSERT INTO vault_items (user_id, file_name, data_base64, client_modified, server_modified, is_deleted)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(user_id, file_name) DO UPDATE SET
          data_base64 = excluded.data_base64,
          client_modified = excluded.client_modified,
          server_modified = excluded.server_modified,
          is_deleted = excluded.is_deleted
      `);

      for (const item of items) {
        if (!item.file_name) continue;
        const fileName = item.file_name;
        const dataBase64 = item.data_base64 || '';
        const clientModified = Number(item.client_modified) || now;
        const isDeleted = item.is_deleted ? 1 : 0;

        upsert.run(userId, fileName, dataBase64, clientModified, now, isDeleted);
        pushedCount++;
      }

      return sendJson(res, 200, {
        success: true,
        pushed_count: pushedCount,
        server_time: now
      });
    }

    // 5. Sync: Pull items (Server -> Client)
    if (req.method === 'GET' && pathname === '/api/v1/sync/pull') {
      const userId = authenticate(req, url.searchParams);

      if (!userId) {
        return sendJson(res, 401, { success: false, message: 'Unauthorized: valid token required' });
      }

      const since = Number(url.searchParams.get('since')) || 0;
      const stmt = db.prepare(`
        SELECT file_name, data_base64, client_modified, server_modified, is_deleted
        FROM vault_items
        WHERE user_id = ? AND server_modified > ?
        ORDER BY server_modified ASC
      `);

      const rows = stmt.all(userId, since);
      const items = rows.map(r => ({
        file_name: r.file_name,
        data_base64: r.data_base64,
        client_modified: r.client_modified,
        server_modified: r.server_modified,
        is_deleted: r.is_deleted === 1
      }));

      return sendJson(res, 200, {
        success: true,
        server_time: Date.now(),
        items
      });
    }

    // Fallback 404
    sendJson(res, 404, { success: false, message: 'Endpoint not found' });
  } catch (err) {
    console.error('[Error handling request]:', err);
    sendJson(res, 500, { success: false, message: err.message || 'Internal server error' });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[Monolith Cloud Vault] Server listening on http://0.0.0.0:${PORT}`);
});
