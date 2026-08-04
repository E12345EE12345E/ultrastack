"use strict";

const path = require("path");
const fs = require("fs");
const express = require("express");
const multer = require("multer");
const Database = require("better-sqlite3");
const { v4: uuidv4 } = require("uuid");

const PORT = Number(process.env.PORT) || 3847;
const ROOT = __dirname;
const DATA_DIR = path.join(ROOT, "data");
const WORKING_DB = path.join(DATA_DIR, "working.db");
const ASSETS_ARTIFACTS = path.join(ROOT, "..", "assets", "char_img", "artifacts");

const EFFECT_TYPES = [
  "LINE_CLEAR_SCORE",
  "SPIN_SCORE",
  "LINE_CLEAR_METER",
  "SPIN_METER",
  "EQUIPPED_LINE_CLEAR_METER",
  "EQUIPPED_SPIN_METER",
  "EQUIPPED_PASSIVE_FILL_SPEED",
];

const PIECE_TYPES = {
  I: 1,
  J: 2,
  L: 3,
  O: 4,
  S: 5,
  T: 6,
  Z: 7,
  I3: 8,
  L3: 9,
};

fs.mkdirSync(DATA_DIR, { recursive: true });

const app = express();
app.use(express.json({ limit: "10mb" }));
app.use(express.static(path.join(ROOT, "public")));
app.use("/artifact-assets", express.static(ASSETS_ARTIFACTS));

const upload = multer({
  storage: multer.diskStorage({
    destination: DATA_DIR,
    filename: (_req, _file, cb) => cb(null, "upload-" + Date.now() + ".db"),
  }),
  limits: { fileSize: 200 * 1024 * 1024 },
});

/** @type {import('better-sqlite3').Database | null} */
let db = null;
let loadedName = null;

function closeDb() {
  if (db) {
    try {
      db.close();
    } catch (_) {
      /* ignore */
    }
    db = null;
  }
}

function openWorkingDb() {
  closeDb();
  db = new Database(WORKING_DB);
  db.pragma("journal_mode = WAL");
  ensureAccountsTable();
}

function ensureAccountsTable() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS accounts (
      uuid TEXT PRIMARY KEY,
      username TEXT NOT NULL UNIQUE,
      salt_base64 TEXT NOT NULL,
      hash_base64 TEXT NOT NULL,
      created_at_ms INTEGER NOT NULL,
      xp INTEGER NOT NULL DEFAULT 0,
      schema_version INTEGER NOT NULL,
      extra_json TEXT
    );
  `);
}

function requireDb(_req, res, next) {
  if (!db) {
    return res.status(400).json({ error: "No database loaded. Drop an accounts.db file first." });
  }
  next();
}

function copyFileSync(src, dest) {
  fs.copyFileSync(src, dest);
}

function parseExtra(extraJson) {
  if (!extraJson) {
    return { profile: defaultProfile() };
  }
  try {
    const parsed = JSON.parse(extraJson);
    if (!parsed.profile) parsed.profile = defaultProfile();
    if (!Array.isArray(parsed.profile.inventory)) parsed.profile.inventory = [];
    if (!Array.isArray(parsed.profile.equippedArtifactIds)) {
      parsed.profile.equippedArtifactIds = [null, null];
    }
    while (parsed.profile.equippedArtifactIds.length < 2) {
      parsed.profile.equippedArtifactIds.push(null);
    }
    return parsed;
  } catch (e) {
    return { profile: defaultProfile(), _parseError: String(e.message || e) };
  }
}

function defaultProfile() {
  return {
    unlockedCharacterBits: 3,
    selectedCharacterId: 0,
    equippedArtifactIds: [null, null],
    inventory: [],
  };
}

function stringifyExtra(extra) {
  return JSON.stringify(extra);
}

function rowToAccount(row, includeExtra = false) {
  const account = {
    uuid: row.uuid,
    username: row.username,
    salt_base64: row.salt_base64,
    hash_base64: row.hash_base64,
    created_at_ms: row.created_at_ms,
    xp: row.xp,
    schema_version: row.schema_version,
  };
  if (includeExtra) {
    // Parsed profile only — omit raw extra_json to avoid duplicating a huge string in the UI.
    account.extra = parseExtra(row.extra_json);
  }
  return account;
}

function getAccountRow(uuid) {
  return db
    .prepare(
      `SELECT uuid, username, salt_base64, hash_base64, created_at_ms, xp, schema_version, extra_json
       FROM accounts WHERE uuid = ?`
    )
    .get(uuid);
}

function saveExtra(uuid, extra) {
  const extraJson = stringifyExtra(extra);
  db.prepare("UPDATE accounts SET extra_json = ? WHERE uuid = ?").run(extraJson, uuid);
  return extraJson;
}

function normalizeArtifact(input) {
  if (!input || typeof input !== "object") throw new Error("Artifact body required");

  let pieceType = input.pieceType;
  if (typeof pieceType === "string") {
    const key = pieceType.toUpperCase();
    if (!(key in PIECE_TYPES)) throw new Error("Unknown pieceType: " + pieceType);
    pieceType = PIECE_TYPES[key];
  }
  pieceType = Number(pieceType);
  if (!Number.isFinite(pieceType)) throw new Error("Invalid pieceType");

  const effects = Array.isArray(input.effects)
    ? input.effects.map((e) => {
        const type = String(e.type || "").trim();
        if (!EFFECT_TYPES.includes(type)) throw new Error("Unknown effect type: " + type);
        const quality = Number(e.quality);
        if (!Number.isFinite(quality)) throw new Error("Invalid effect quality");
        return { type, quality };
      })
    : [];

  let level = Number(input.level);
  if (!Number.isFinite(level) || level < 1) level = Math.max(1, effects.length || 1);
  if (effects.length === 0) {
    throw new Error("Artifact needs at least one effect");
  }
  // Keep level in sync with effect count (implementation.md: one effect per level).
  level = effects.length;

  const baseQuality = Number(input.baseQuality);
  if (!Number.isFinite(baseQuality)) throw new Error("Invalid baseQuality");

  return {
    id: input.id && String(input.id).trim() ? String(input.id).trim() : uuidv4(),
    pieceType,
    level,
    baseQuality,
    effects,
  };
}

app.get("/api/status", (_req, res) => {
  res.json({
    loaded: !!db,
    filename: loadedName,
    effectTypes: EFFECT_TYPES,
    pieceTypes: PIECE_TYPES,
  });
});

app.post("/api/upload", upload.single("database"), (req, res) => {
  if (!req.file) return res.status(400).json({ error: "No file uploaded" });
  try {
    closeDb();
    // Drop WAL sidecars from a previous working copy so we don't mix journals.
    for (const suffix of ["", "-wal", "-shm"]) {
      const p = WORKING_DB + suffix;
      if (fs.existsSync(p)) fs.unlinkSync(p);
    }
    copyFileSync(req.file.path, WORKING_DB);
    fs.unlinkSync(req.file.path);
    openWorkingDb();
    const tables = db
      .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='accounts'")
      .get();
    if (!tables) {
      closeDb();
      fs.unlinkSync(WORKING_DB);
      return res.status(400).json({ error: "Uploaded file has no accounts table" });
    }
    loadedName = req.file.originalname || "accounts.db";
    const count = db.prepare("SELECT COUNT(*) AS c FROM accounts").get().c;
    res.json({ ok: true, filename: loadedName, accountCount: count });
  } catch (e) {
    closeDb();
    res.status(500).json({ error: String(e.message || e) });
  }
});

app.get("/api/accounts", requireDb, (_req, res) => {
  const rows = db
    .prepare(
      `SELECT uuid, username, salt_base64, hash_base64, created_at_ms, xp, schema_version
       FROM accounts ORDER BY username COLLATE NOCASE`
    )
    .all();
  res.json({ accounts: rows.map((r) => rowToAccount(r, false)) });
});

app.get("/api/accounts/:uuid", requireDb, (req, res) => {
  const row = getAccountRow(req.params.uuid);
  if (!row) return res.status(404).json({ error: "Account not found" });
  res.json({ account: rowToAccount(row, true) });
});

app.put("/api/accounts/:uuid/extra", requireDb, (req, res) => {
  const row = getAccountRow(req.params.uuid);
  if (!row) return res.status(404).json({ error: "Account not found" });
  try {
    const extra = req.body && req.body.extra != null ? req.body.extra : req.body;
    if (!extra || typeof extra !== "object") throw new Error("extra object required");
    if (!extra.profile) extra.profile = defaultProfile();
    saveExtra(req.params.uuid, extra);
    res.json({ account: rowToAccount(getAccountRow(req.params.uuid), true) });
  } catch (e) {
    res.status(400).json({ error: String(e.message || e) });
  }
});

app.put("/api/accounts/:uuid/artifacts/:artifactId", requireDb, (req, res) => {
  const row = getAccountRow(req.params.uuid);
  if (!row) return res.status(404).json({ error: "Account not found" });
  try {
    const extra = parseExtra(row.extra_json);
    const inv = extra.profile.inventory;
    const idx = inv.findIndex((a) => a && a.id === req.params.artifactId);
    if (idx < 0) return res.status(404).json({ error: "Artifact not found" });
    const updated = normalizeArtifact({ ...req.body, id: req.params.artifactId });
    inv[idx] = updated;
    saveExtra(req.params.uuid, extra);
    res.json({ artifact: updated, account: rowToAccount(getAccountRow(req.params.uuid), true) });
  } catch (e) {
    res.status(400).json({ error: String(e.message || e) });
  }
});

app.delete("/api/accounts/:uuid/artifacts/:artifactId", requireDb, (req, res) => {
  const row = getAccountRow(req.params.uuid);
  if (!row) return res.status(404).json({ error: "Account not found" });
  const extra = parseExtra(row.extra_json);
  const before = extra.profile.inventory.length;
  extra.profile.inventory = extra.profile.inventory.filter(
    (a) => a && a.id !== req.params.artifactId
  );
  if (extra.profile.inventory.length === before) {
    return res.status(404).json({ error: "Artifact not found" });
  }
  extra.profile.equippedArtifactIds = (extra.profile.equippedArtifactIds || [null, null]).map(
    (id) => (id === req.params.artifactId ? null : id)
  );
  saveExtra(req.params.uuid, extra);
  res.json({ ok: true, account: rowToAccount(getAccountRow(req.params.uuid), true) });
});

app.post("/api/accounts/:uuid/artifacts", requireDb, (req, res) => {
  const row = getAccountRow(req.params.uuid);
  if (!row) return res.status(404).json({ error: "Account not found" });
  try {
    const extra = parseExtra(row.extra_json);
    const artifact = normalizeArtifact(req.body);
    if (extra.profile.inventory.some((a) => a && a.id === artifact.id)) {
      artifact.id = uuidv4();
    }
    extra.profile.inventory.push(artifact);
    saveExtra(req.params.uuid, extra);
    res.json({ artifact, account: rowToAccount(getAccountRow(req.params.uuid), true) });
  } catch (e) {
    res.status(400).json({ error: String(e.message || e) });
  }
});

app.get("/api/save", requireDb, (req, res) => {
  try {
    // Checkpoint WAL into the main file so the download is self-contained.
    db.pragma("wal_checkpoint(TRUNCATE)");
    const name = loadedName || "accounts.db";
    res.setHeader("Content-Type", "application/octet-stream");
    res.setHeader("Content-Disposition", `attachment; filename="${name.replace(/"/g, "")}"`);
    fs.createReadStream(WORKING_DB).pipe(res);
  } catch (e) {
    res.status(500).json({ error: String(e.message || e) });
  }
});

app.post("/api/save-to-path", requireDb, (req, res) => {
  const target = req.body && req.body.path;
  if (!target || typeof target !== "string") {
    return res.status(400).json({ error: "path string required" });
  }
  try {
    db.pragma("wal_checkpoint(TRUNCATE)");
    const resolved = path.resolve(target);
    fs.mkdirSync(path.dirname(resolved), { recursive: true });
    copyFileSync(WORKING_DB, resolved);
    res.json({ ok: true, path: resolved });
  } catch (e) {
    res.status(500).json({ error: String(e.message || e) });
  }
});

// Resume previous session if a working copy already exists.
if (fs.existsSync(WORKING_DB)) {
  try {
    openWorkingDb();
    loadedName = "working.db";
  } catch (e) {
    console.warn("[web_dbedit] Could not reopen working.db:", e.message);
    closeDb();
  }
}

app.listen(PORT, () => {
  console.log(`[web_dbedit] http://localhost:${PORT}`);
  console.log(`[web_dbedit] Drop an accounts.db into the page to begin.`);
});
