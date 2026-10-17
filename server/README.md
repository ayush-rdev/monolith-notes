# ☁️ Monolith Cloud Vault — Sync Server

A zero-dependency, ultra-lightweight, and end-to-end encrypted (E2EE) synchronization server for **Monolith Notes**.

## 🚀 Quick Start

### Option 1: Native Node.js (Zero NPM Dependencies)
Requires Node.js 22+:
```bash
node server.js
```
The server will start instantly on port `8080` using an embedded SQLite database (`vault.db`).

### Option 2: Docker
```bash
docker compose up -d
```

### Option 3: Deploy to Free Cloud Providers
- **Render**: Create a free Web Service with runtime **Node.js** and build/start command `node server.js`.
- **Railway**: Connect GitHub repository and select the `server/` directory.
- **Fly.io**: Run `fly launch` in the `server/` directory.

---

## 🔒 Security & E2EE Privacy

All Markdown notes, titles, and media attachments are **encrypted locally on the user's mobile device using AES-256-GCM** before being transmitted.
The server only ever stores opaque encrypted ciphertext blobs and cannot read note contents, headers, or metadata.
