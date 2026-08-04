(() => {
  "use strict";

  const EFFECT_LETTER = {
    LINE_CLEAR_SCORE: "A",
    SPIN_SCORE: "B",
    LINE_CLEAR_METER: "C",
    SPIN_METER: "D",
    EQUIPPED_LINE_CLEAR_METER: "E",
    EQUIPPED_SPIN_METER: "F",
    EQUIPPED_PASSIVE_FILL_SPEED: "G",
  };

  const PIECE_NAME = {
    1: "I",
    2: "J",
    3: "L",
    4: "O",
    5: "S",
    6: "T",
    7: "Z",
    8: "I3",
    9: "L3",
  };

  const state = {
    loaded: false,
    filename: null,
    effectTypes: Object.keys(EFFECT_LETTER),
    pieceTypes: { I: 1, J: 2, L: 3, O: 4, S: 5, T: 6, Z: 7 },
    accounts: [],
    selectedUuid: null,
    account: null,
    selectedArtifactId: null,
  };

  const el = {
    statusText: document.getElementById("statusText"),
    saveBtn: document.getElementById("saveBtn"),
    fileInput: document.getElementById("fileInput"),
    dropOverlay: document.getElementById("dropOverlay"),
    accountList: document.getElementById("accountList"),
    accountJson: document.getElementById("accountJson"),
    inventoryGrid: document.getElementById("inventoryGrid"),
    inventoryScroll: document.getElementById("inventoryScroll"),
    hoverTip: document.getElementById("hoverTip"),
    artifactEditor: document.getElementById("artifactEditor"),
    addArtifactForm: document.getElementById("addArtifactForm"),
    addPieceType: document.getElementById("addPieceType"),
    addEffectType: document.getElementById("addEffectType"),
    addArtifactBtn: document.getElementById("addArtifactBtn"),
  };

  async function api(path, options = {}) {
    const res = await fetch(path, options);
    const contentType = res.headers.get("content-type") || "";
    if (contentType.includes("application/json") || !res.ok) {
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || res.statusText || "Request failed");
      return data;
    }
    return res;
  }

  function pieceLetter(pieceType) {
    return PIECE_NAME[pieceType] || "?";
  }

  function artifactIconUrl(pieceType) {
    const letter = pieceLetter(pieceType).toLowerCase();
    if (!"ijlostz".includes(letter)) return null;
    return `/artifact-assets/artifact_piece_${letter}.png`;
  }

  function effectLabel(type) {
    const letter = EFFECT_LETTER[type] || "?";
    return `${letter} (${type})`;
  }

  function setStatus(text) {
    el.statusText.textContent = text;
  }

  function fillSelects() {
    el.addPieceType.innerHTML = "";
    for (const [name, value] of Object.entries(state.pieceTypes)) {
      if (name === "I3" || name === "L3") continue;
      const opt = document.createElement("option");
      opt.value = String(value);
      opt.textContent = name;
      el.addPieceType.appendChild(opt);
    }

    el.addEffectType.innerHTML = "";
    for (const type of state.effectTypes) {
      const opt = document.createElement("option");
      opt.value = type;
      opt.textContent = `${EFFECT_LETTER[type] || "?"} — ${type}`;
      el.addEffectType.appendChild(opt);
    }
  }

  async function refreshStatus() {
    const data = await api("/api/status");
    state.loaded = data.loaded;
    state.filename = data.filename;
    if (Array.isArray(data.effectTypes) && data.effectTypes.length) {
      state.effectTypes = data.effectTypes;
    }
    if (data.pieceTypes) state.pieceTypes = data.pieceTypes;
    fillSelects();
    el.saveBtn.disabled = !state.loaded;
    el.addArtifactBtn.disabled = !state.loaded || !state.selectedUuid;
    setStatus(
      state.loaded
        ? `Loaded: ${state.filename || "accounts.db"}`
        : "No database loaded — drop an accounts.db here"
    );
  }

  async function uploadFile(file) {
    if (!file) return;
    const form = new FormData();
    form.append("database", file, file.name);
    setStatus(`Loading ${file.name}…`);
    const data = await api("/api/upload", { method: "POST", body: form });
    state.loaded = true;
    state.filename = data.filename;
    state.selectedUuid = null;
    state.account = null;
    state.selectedArtifactId = null;
    el.saveBtn.disabled = false;
    setStatus(`Loaded: ${data.filename} (${data.accountCount} accounts)`);
    el.accountJson.textContent = "Select an account.";
    el.artifactEditor.className = "artifact-editor muted";
    el.artifactEditor.textContent = "Click an artifact in the inventory to edit it.";
    el.inventoryGrid.innerHTML = "";
    await loadAccounts();
  }

  async function loadAccounts() {
    const data = await api("/api/accounts");
    state.accounts = data.accounts || [];
    renderAccountList();
  }

  function renderAccountList() {
    el.accountList.innerHTML = "";
    if (!state.accounts.length) {
      el.accountList.innerHTML = '<div class="empty-hint">No accounts in this database.</div>';
      return;
    }

    for (const acct of state.accounts) {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "account-card" + (acct.uuid === state.selectedUuid ? " selected" : "");
      btn.innerHTML = `
        <div class="name">${escapeHtml(acct.username)}</div>
        <dl>
          <dt>uuid</dt><dd title="${escapeAttr(acct.uuid)}">${escapeHtml(acct.uuid)}</dd>
          <dt>xp</dt><dd>${escapeHtml(String(acct.xp))}</dd>
          <dt>created_at_ms</dt><dd>${escapeHtml(String(acct.created_at_ms))}</dd>
          <dt>schema_version</dt><dd>${escapeHtml(String(acct.schema_version))}</dd>
          <dt>salt_base64</dt><dd title="${escapeAttr(acct.salt_base64)}">${escapeHtml(acct.salt_base64)}</dd>
          <dt>hash_base64</dt><dd title="${escapeAttr(acct.hash_base64)}">${escapeHtml(acct.hash_base64)}</dd>
        </dl>
      `;
      btn.addEventListener("click", () => selectAccount(acct.uuid));
      el.accountList.appendChild(btn);
    }
  }

  async function selectAccount(uuid) {
    const data = await api(`/api/accounts/${encodeURIComponent(uuid)}`);
    state.selectedUuid = uuid;
    state.account = data.account;
    state.selectedArtifactId = null;
    el.addArtifactBtn.disabled = !state.loaded;
    renderAccountList();
    renderAccountJson();
    renderInventory();
    el.artifactEditor.className = "artifact-editor muted";
    el.artifactEditor.textContent = "Click an artifact in the inventory to edit it.";
  }

  function applyAccountUpdate(account) {
    state.account = account;
    const idx = state.accounts.findIndex((a) => a.uuid === account.uuid);
    if (idx >= 0) {
      state.accounts[idx] = {
        uuid: account.uuid,
        username: account.username,
        salt_base64: account.salt_base64,
        hash_base64: account.hash_base64,
        created_at_ms: account.created_at_ms,
        xp: account.xp,
        schema_version: account.schema_version,
      };
    }
    renderAccountJson();
    renderInventory();
  }

  function renderAccountJson() {
    if (!state.account) {
      el.accountJson.textContent = "Select an account.";
      return;
    }
    // Full account payload including parsed extra/profile (not raw-only).
    el.accountJson.textContent = JSON.stringify(state.account, null, 2);
  }

  function inventory() {
    return (state.account && state.account.extra && state.account.extra.profile
      && state.account.extra.profile.inventory) || [];
  }

  function equippedIds() {
    const ids =
      (state.account &&
        state.account.extra &&
        state.account.extra.profile &&
        state.account.extra.profile.equippedArtifactIds) ||
      [];
    return new Set(ids.filter(Boolean));
  }

  function renderInventory() {
    el.inventoryGrid.innerHTML = "";
    const items = inventory();
    if (!items.length) {
      el.inventoryGrid.innerHTML =
        '<div class="empty-hint" style="grid-column:1/-1">No artifacts in inventory.</div>';
      return;
    }

    const equipped = equippedIds();
    for (const artifact of items) {
      if (!artifact) continue;
      const slot = document.createElement("button");
      slot.type = "button";
      slot.className =
        "inv-slot" +
        (artifact.id === state.selectedArtifactId ? " selected" : "") +
        (equipped.has(artifact.id) ? " equipped" : "");
      slot.dataset.id = artifact.id;

      const icon = artifactIconUrl(artifact.pieceType);
      if (icon) {
        const img = document.createElement("img");
        img.src = icon;
        img.alt = pieceLetter(artifact.pieceType);
        slot.appendChild(img);
      } else {
        const fb = document.createElement("span");
        fb.className = "piece-fallback";
        fb.textContent = pieceLetter(artifact.pieceType);
        slot.appendChild(fb);
      }

      const badge = document.createElement("span");
      badge.className = "level-badge";
      badge.textContent = String(artifact.level ?? (artifact.effects || []).length);
      slot.appendChild(badge);

      slot.addEventListener("mouseenter", (e) => showTip(artifact, e));
      slot.addEventListener("mousemove", (e) => moveTip(e));
      slot.addEventListener("mouseleave", hideTip);
      slot.addEventListener("click", () => selectArtifact(artifact.id));
      el.inventoryGrid.appendChild(slot);
    }
  }

  function showTip(artifact, event) {
    const effects = artifact.effects || [];
    const lines = effects
      .map((e) => {
        const letter = EFFECT_LETTER[e.type] || "?";
        return `<li><span class="letter-tag">${letter}</span> q=${formatNum(e.quality)} — ${escapeHtml(
          e.type
        )}</li>`;
      })
      .join("");
    el.hoverTip.hidden = false;
    el.hoverTip.innerHTML = `
      <div class="tip-title">${escapeHtml(pieceLetter(artifact.pieceType))} Artifact (Lv${escapeHtml(
        String(artifact.level ?? effects.length)
      )})</div>
      <div class="tip-meta">base quality: ${escapeHtml(formatNum(artifact.baseQuality))}</div>
      <ul>${lines || "<li>No effects</li>"}</ul>
    `;
    moveTip(event);
  }

  function moveTip(event) {
    const tip = el.hoverTip;
    if (tip.hidden) return;
    const pad = 14;
    let x = event.clientX + pad;
    let y = event.clientY + pad;
    tip.style.left = "0px";
    tip.style.top = "0px";
    const rect = tip.getBoundingClientRect();
    if (x + rect.width > window.innerWidth - 8) x = event.clientX - rect.width - pad;
    if (y + rect.height > window.innerHeight - 8) y = event.clientY - rect.height - pad;
    tip.style.left = `${Math.max(8, x)}px`;
    tip.style.top = `${Math.max(8, y)}px`;
  }

  function hideTip() {
    el.hoverTip.hidden = true;
  }

  function selectArtifact(id) {
    state.selectedArtifactId = id;
    renderInventory();
    const artifact = inventory().find((a) => a && a.id === id);
    if (!artifact) {
      el.artifactEditor.className = "artifact-editor muted";
      el.artifactEditor.textContent = "Artifact not found.";
      return;
    }
    renderArtifactEditor(artifact);
  }

  function effectTypeOptions(selected) {
    return state.effectTypes
      .map((type) => {
        const sel = type === selected ? " selected" : "";
        return `<option value="${escapeAttr(type)}"${sel}>${EFFECT_LETTER[type] || "?"} — ${escapeHtml(
          type
        )}</option>`;
      })
      .join("");
  }

  function renderArtifactEditor(artifact) {
    el.artifactEditor.className = "artifact-editor";
    const effects = artifact.effects || [];
    el.artifactEditor.innerHTML = `
      <form id="editArtifactForm" class="editor-form">
        <label>ID<input name="id" value="${escapeAttr(artifact.id)}" readonly /></label>
        <label>Piece type
          <select name="pieceType">${Object.entries(state.pieceTypes)
            .map(([name, value]) => {
              const sel = Number(value) === Number(artifact.pieceType) ? " selected" : "";
              return `<option value="${value}"${sel}>${escapeHtml(name)}</option>`;
            })
            .join("")}</select>
        </label>
        <label>Level (synced to effect count)
          <input name="level" type="number" value="${escapeAttr(String(artifact.level ?? effects.length))}" readonly />
        </label>
        <label>Base quality
          <input name="baseQuality" type="number" step="any" value="${escapeAttr(
            formatNum(artifact.baseQuality)
          )}" required />
        </label>
        <div class="sidebar-title" style="padding-left:0">Effects</div>
        <div class="effects-editor" id="effectsEditor">
          ${effects
            .map(
              (e, i) => `
            <div class="effect-row" data-index="${i}">
              <select name="effectType">${effectTypeOptions(e.type)}</select>
              <input name="quality" type="number" step="any" value="${escapeAttr(
                formatNum(e.quality)
              )}" title="quality q" />
              <button type="button" class="btn btn-danger remove-effect" title="Remove effect">×</button>
            </div>`
            )
            .join("")}
        </div>
        <button type="button" class="btn btn-secondary" id="addEffectRow">Add effect</button>
        <div class="editor-actions">
          <button type="submit" class="btn btn-primary">Save Artifact</button>
          <button type="button" class="btn btn-danger" id="removeArtifactBtn">Remove Artifact</button>
        </div>
      </form>
    `;

    const form = document.getElementById("editArtifactForm");
    const effectsEditor = document.getElementById("effectsEditor");

    document.getElementById("addEffectRow").addEventListener("click", () => {
      const row = document.createElement("div");
      row.className = "effect-row";
      row.innerHTML = `
        <select name="effectType">${effectTypeOptions(state.effectTypes[0])}</select>
        <input name="quality" type="number" step="any" value="10" title="quality q" />
        <button type="button" class="btn btn-danger remove-effect" title="Remove effect">×</button>
      `;
      effectsEditor.appendChild(row);
      syncLevelField(form);
    });

    effectsEditor.addEventListener("click", (e) => {
      const btn = e.target.closest(".remove-effect");
      if (!btn) return;
      const rows = effectsEditor.querySelectorAll(".effect-row");
      if (rows.length <= 1) {
        alert("An artifact needs at least one effect.");
        return;
      }
      btn.closest(".effect-row").remove();
      syncLevelField(form);
    });

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        const body = readArtifactForm(form, effectsEditor);
        const data = await api(
          `/api/accounts/${encodeURIComponent(state.selectedUuid)}/artifacts/${encodeURIComponent(
            artifact.id
          )}`,
          {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
          }
        );
        applyAccountUpdate(data.account);
        state.selectedArtifactId = data.artifact.id;
        renderArtifactEditor(data.artifact);
        setStatus(`Saved artifact ${data.artifact.id.slice(0, 8)}…`);
      } catch (err) {
        alert(err.message);
      }
    });

    document.getElementById("removeArtifactBtn").addEventListener("click", async () => {
      if (!confirm("Remove this artifact from the inventory?")) return;
      try {
        const data = await api(
          `/api/accounts/${encodeURIComponent(state.selectedUuid)}/artifacts/${encodeURIComponent(
            artifact.id
          )}`,
          { method: "DELETE" }
        );
        state.selectedArtifactId = null;
        applyAccountUpdate(data.account);
        el.artifactEditor.className = "artifact-editor muted";
        el.artifactEditor.textContent = "Click an artifact in the inventory to edit it.";
        setStatus("Artifact removed (remember to Save DB).");
      } catch (err) {
        alert(err.message);
      }
    });
  }

  function syncLevelField(form) {
    const levelInput = form.querySelector('input[name="level"]');
    const count = form.querySelectorAll("#effectsEditor .effect-row").length;
    if (levelInput) levelInput.value = String(count);
  }

  function readArtifactForm(form, effectsEditor) {
    const fd = new FormData(form);
    const effects = [...effectsEditor.querySelectorAll(".effect-row")].map((row) => ({
      type: row.querySelector('select[name="effectType"]').value,
      quality: Number(row.querySelector('input[name="quality"]').value),
    }));
    return {
      id: fd.get("id"),
      pieceType: Number(fd.get("pieceType")),
      level: effects.length,
      baseQuality: Number(fd.get("baseQuality")),
      effects,
    };
  }

  el.addArtifactForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (!state.selectedUuid) {
      alert("Select an account first.");
      return;
    }
    const fd = new FormData(el.addArtifactForm);
    const body = {
      pieceType: Number(fd.get("pieceType")),
      baseQuality: Number(fd.get("baseQuality")),
      effects: [{ type: fd.get("effectType"), quality: Number(fd.get("quality")) }],
    };
    try {
      const data = await api(
        `/api/accounts/${encodeURIComponent(state.selectedUuid)}/artifacts`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        }
      );
      applyAccountUpdate(data.account);
      state.selectedArtifactId = data.artifact.id;
      renderArtifactEditor(data.artifact);
      setStatus(`Added artifact ${data.artifact.id.slice(0, 8)}… (remember to Save DB)`);
    } catch (err) {
      alert(err.message);
    }
  });

  el.saveBtn.addEventListener("click", async () => {
    try {
      const res = await fetch("/api/save");
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.error || "Save failed");
      }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = state.filename || "accounts.db";
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      setStatus(`Saved ${state.filename || "accounts.db"} to disk (download).`);
    } catch (err) {
      alert(err.message);
    }
  });

  el.fileInput.addEventListener("change", async () => {
    const file = el.fileInput.files && el.fileInput.files[0];
    try {
      await uploadFile(file);
    } catch (err) {
      alert(err.message);
    }
    el.fileInput.value = "";
  });

  // Drag-and-drop load
  let dragDepth = 0;
  window.addEventListener("dragenter", (e) => {
    e.preventDefault();
    dragDepth += 1;
    el.dropOverlay.classList.remove("hidden");
  });
  window.addEventListener("dragleave", (e) => {
    e.preventDefault();
    dragDepth = Math.max(0, dragDepth - 1);
    if (dragDepth === 0) el.dropOverlay.classList.add("hidden");
  });
  window.addEventListener("dragover", (e) => e.preventDefault());
  window.addEventListener("drop", async (e) => {
    e.preventDefault();
    dragDepth = 0;
    el.dropOverlay.classList.add("hidden");
    const file = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
    if (!file) return;
    try {
      await uploadFile(file);
    } catch (err) {
      alert(err.message);
    }
  });

  function formatNum(n) {
    if (typeof n !== "number" || !Number.isFinite(n)) return String(n ?? "");
    return String(Number(n));
  }

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function escapeAttr(s) {
    return escapeHtml(s).replace(/'/g, "&#39;");
  }

  refreshStatus()
    .then(async () => {
      if (state.loaded) await loadAccounts();
    })
    .catch((err) => setStatus("Failed to reach server: " + err.message));
})();
