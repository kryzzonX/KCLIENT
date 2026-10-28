const { app, BrowserWindow, ipcMain, shell } = require("electron");
const path = require("path"), fs = require("fs"), os = require("os"), crypto = require("crypto");
const { spawnSync } = require("child_process");
const { Client, Authenticator } = require("minecraft-launcher-core");
const { Auth } = require("msmc");
const AdmZip = require("adm-zip");

const ROOT = path.join(os.homedir(), ".kclient");
const GAME = path.join(ROOT, "game"); // yhteiset versiot, kirjastot ja assetit
const profDir = (slug) => path.join(ROOT, "profiles", slug); // jokaisella profiililla oma kansio
const SUB = ["mods", "saves", "config", "resourcepacks", "shaderpacks", "screenshots", "logs"];
const UA = { "User-Agent": "k-client-launcher/1.0 (personal use)" };
let win;

const getJSON = async (url) => {
  const r = await fetch(url, { headers: UA });
  if (!r.ok) throw new Error(`${url} -> ${r.status}`);
  return r.json();
};
const send = (ch, d) => win && !win.isDestroyed() && win.webContents.send(ch, d);

app.whenReady().then(() => {
  win = new BrowserWindow({ width: 1200, height: 780, backgroundColor: "#0c0814", autoHideMenuBar: true,
    webPreferences: { preload: path.join(__dirname, "preload.js") } });
  win.loadFile("index.html");
});
app.on("window-all-closed", () => app.quit());

ipcMain.handle("versions", async () => {
  const m = await getJSON("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
  return m.versions.filter((v) => v.type === "release").map((v) => v.id);
});
ipcMain.handle("ensure", (e, slug) => {
  SUB.forEach((s) => fs.mkdirSync(path.join(profDir(slug), s), { recursive: true }));
  return profDir(slug);
});
ipcMain.handle("open", (e, slug) => shell.openPath(profDir(slug)));
ipcMain.handle("copy", (e, { from, to }) => fs.cpSync(profDir(from), profDir(to), { recursive: true }));
ipcMain.handle("remove", (e, slug) => fs.rmSync(profDir(slug), { recursive: true, force: true }));

// ---------- Modrinth
ipcMain.handle("mr:search", async (e, { q, ver, offset }) => {
  const facets = [["project_type:mod"], ["categories:fabric"]];
  if (ver) facets.push([`versions:${ver}`]);
  const u = new URL("https://api.modrinth.com/v2/search");
  u.search = new URLSearchParams({ query: q || "", facets: JSON.stringify(facets), limit: 20,
    offset: offset || 0, index: q ? "relevance" : "downloads" });
  const j = await getJSON(u.toString());
  return { total: j.total_hits, hits: j.hits.map((h) => ({ id: h.project_id, title: h.title, desc: h.description, downloads: h.downloads })) };
});

async function mrInstall(id, ver, dir, seen) {
  if (seen.has(id)) return [];
  seen.add(id);
  const q = `game_versions=${encodeURIComponent(JSON.stringify([ver]))}&loaders=${encodeURIComponent('["fabric"]')}`;
  const vs = await getJSON(`https://api.modrinth.com/v2/project/${id}/version?${q}`);
  const v = ["release", "beta", "alpha"].map((t) => vs.find((x) => x.version_type === t)).find(Boolean);
  if (!v) throw new Error(`ei Fabric-versiota Minecraft ${ver}:lle`);
  const f = v.files.find((x) => x.primary) || v.files[0];
  const dest = path.join(dir, f.filename);
  if (!fs.existsSync(dest)) {
    const r = await fetch(f.url, { headers: UA });
    if (!r.ok) throw new Error("lataus epäonnistui");
    const buf = Buffer.from(await r.arrayBuffer());
    const want = f.hashes && f.hashes.sha512;
    if (want && crypto.createHash("sha512").update(buf).digest("hex") !== want) throw new Error("tarkistussumma ei täsmää");
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(dest, buf);
  }
  const out = [f.filename];
  for (const d of v.dependencies || []) {
    if (d.dependency_type === "required" && d.project_id) {
      try { out.push(...(await mrInstall(d.project_id, ver, dir, seen))); } catch {}
    }
  }
  return out;
}
ipcMain.handle("mr:install", async (e, { ids, ver, slug }) => {
  const dir = path.join(profDir(slug), "mods"), seen = new Set(), ok = [], bad = [];
  for (const [i, id] of ids.entries()) {
    send("status", `Ladataan modeja (${i + 1}/${ids.length}): ${id}`);
    try { ok.push(...(await mrInstall(id, ver, dir, seen))); } catch (err) { bad.push(`${id}: ${err.message}`); }
  }
  return { ok, bad };
});

// ---------- Microsoft (avaa Microsoftin viralliset kirjautumissivut)
const authMgr = new Auth("select_account");
ipcMain.handle("ms:login", async () => {
  const xbox = await authMgr.launch("electron");
  const mc = await xbox.getMinecraft();
  return { name: mc.profile.name, uuid: mc.profile.id, refresh: xbox.save() };
});


// ---------- Asennetut modit (päälle/pois = .jar <-> .jar.disabled)
ipcMain.handle("mods:list", (e, slug) => {
  const d = path.join(profDir(slug), "mods");
  if (!fs.existsSync(d)) return [];
  return fs.readdirSync(d).filter((f) => /\.jar(\.disabled)?$/.test(f)).sort()
    .map((f) => ({ file: f, enabled: f.endsWith(".jar"), name: f.replace(/\.jar(\.disabled)?$/, "") }));
});
ipcMain.handle("mods:toggle", (e, { slug, file, enable }) => {
  const d = path.join(profDir(slug), "mods"), base = path.basename(file).replace(/\.disabled$/, "");
  fs.renameSync(path.join(d, path.basename(file)), path.join(d, enable ? base : base + ".disabled"));
});
ipcMain.handle("mods:remove", (e, { slug, file }) =>
  fs.rmSync(path.join(profDir(slug), "mods", path.basename(file)), { force: true }));

// ---------- OneClient-modipaketti (Modrinth .mrpack) = samat modit kuin OneClientissä
ipcMain.handle("pack:install", async (e, { slug, ver }) => {
  const q = `game_versions=${encodeURIComponent(JSON.stringify([ver]))}&loaders=${encodeURIComponent('["fabric"]')}`;
  const vs = await getJSON(`https://api.modrinth.com/v2/project/oneclient-modpack/version?${q}`);
  const v = vs.find((x) => x.version_type === "release") || vs[0];
  if (!v) return { ok: false };
  const f = v.files.find((x) => x.primary) || v.files[0];
  send("status", "Ladataan OneClient-pakettia...");
  const r0 = await fetch(f.url, { headers: UA });
  if (!r0.ok) throw new Error("paketin lataus epäonnistui");
  const zip = new AdmZip(Buffer.from(await r0.arrayBuffer()));
  const index = JSON.parse(zip.readAsText("modrinth.index.json"));
  const base = profDir(slug);
  const safe = (p) => {
    const t = path.resolve(base, p);
    if (!t.startsWith(base + path.sep)) throw new Error("virheellinen polku");
    return t;
  };
  const files = index.files.filter((x) => !(x.env && x.env.client === "unsupported"));
  const queue = [...files], failed = [];
  let done = 0;
  const worker = async () => {
    for (let x; (x = queue.shift()); ) {
      try {
        const dest = safe(x.path);
        if (!fs.existsSync(dest)) {
          const r = await fetch(x.downloads[0], { headers: UA });
          if (!r.ok) throw new Error(String(r.status));
          const b = Buffer.from(await r.arrayBuffer());
          const want = x.hashes && x.hashes.sha512;
          if (want && crypto.createHash("sha512").update(b).digest("hex") !== want) throw new Error("hash");
          fs.mkdirSync(path.dirname(dest), { recursive: true });
          fs.writeFileSync(dest, b);
        }
      } catch { failed.push(x.path); }
      send("status", `OneClient-modit: ${++done}/${files.length}`);
    }
  };
  await Promise.all(Array.from({ length: 6 }, worker));
  for (const en of zip.getEntries()) {
    const m = /^(?:client-)?overrides\/(.+)$/.exec(en.entryName);
    if (m && !en.isDirectory) {
      const dest = safe(m[1]);
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.writeFileSync(dest, en.getData());
    }
  }

  // Poista OneConfig/Polymod sekä kaikki modit, joiden pakollinen riippuvuus
  // viittaa niihin. Tarkistus tehdään myös overrides-kansion jälkeen.
  const modsDir = path.join(base, "mods");
  const blockedIds = new Set(["oneconfig", "polymod", "polyui"]);
  const blockedName = /one.?config|polymod|polyfrost/i;
  const readModInfo = (jarPath) => {
    try {
      const jar = new AdmZip(jarPath);
      for (const metaName of ["fabric.mod.json", "quilt.mod.json"]) {
        const entry = jar.getEntry(metaName);
        if (!entry) continue;
        const data = JSON.parse(entry.getData().toString("utf8"));
        if (metaName === "fabric.mod.json") {
          const depends = data.depends || {};
          return {
            id: String(data.id || "").toLowerCase(),
            name: String(data.name || "").toLowerCase(),
            required: Object.keys(depends).map((id) => id.toLowerCase())
          };
        }
        const q = data.quilt_loader || {};
        const depends = (q.depends || []).filter((d) => d && d.id).map((d) => String(d.id).toLowerCase());
        return { id: String(q.id || "").toLowerCase(), name: String(q.metadata?.name || "").toLowerCase(), required: depends };
      }
    } catch {}
    return null;
  };
  if (fs.existsSync(modsDir)) {
    const jars = fs.readdirSync(modsDir).filter((name) => /\.jar$/i.test(name));
    const infos = new Map();
    for (const name of jars) {
      const info = readModInfo(path.join(modsDir, name));
      if (info) infos.set(name, info);
    }
    const removedIds = new Set(blockedIds);
    const remove = new Set();
    for (const name of jars) {
      const info = infos.get(name);
      if (blockedName.test(name) || (info && (blockedName.test(info.id) || blockedName.test(info.name) || blockedIds.has(info.id)))) {
        remove.add(name);
        if (info && info.id) removedIds.add(info.id);
      }
    }
    let changed = true;
    while (changed) {
      changed = false;
      for (const name of jars) {
        if (remove.has(name)) continue;
        const info = infos.get(name);
        if (info && info.required.some((id) => removedIds.has(id))) {
          remove.add(name);
          if (info.id) removedIds.add(info.id);
          changed = true;
        }
      }
    }
    for (const name of remove) {
      fs.rmSync(path.join(modsDir, name), { force: true });
      send("status", `Poistetaan OneConfig-riippuvuus: ${name}`);
    }
  }
  return { ok: true, total: files.length, failed, name: v.name };
});

// ---------- AutoFix: poistaa käytöstä modit, jotka kaataisivat Fabricin käynnistyksessä
// (puuttuva tai väärän version riippuvuus, väärä Minecraft-versio, kaksoiskappaleet, breaks)
// <autofix-pure>
const SKIP_DEPS = new Set(["java", "fabricloader", "fabric-loader"]);
const parseV = (v) => String(v).trim().replace(/^[v=]+/, "").replace(/[-+].*$/, "").split(".")
  .map((x) => (/^\d+$/.test(x) ? +x : NaN));

function cmpV(a, b) {
  const n = Math.max(a.length, b.length);
  for (let i = 0; i < n; i++) {
    const x = a[i] === undefined ? 0 : a[i], y = b[i] === undefined ? 0 : b[i];
    if (Number.isNaN(x) || Number.isNaN(y)) return null;
    if (x !== y) return x < y ? -1 : 1;
  }
  return 0;
}

function oneComp(version, t) {
  if (t === "*" || /^[xX]$/.test(t)) return true;
  const m = /^(>=|<=|>|<|=|~|\^)?(.+)$/.exec(t);
  const op = m[1] || "=", raw = m[2], v = parseV(version);
  if (/(^|\.)[xX*]$/.test(raw)) {
    const pre = parseV(raw.replace(/\.?[xX*]$/, ""));
    if (pre.some(Number.isNaN) || v.some(Number.isNaN)) return null;
    return pre.every((x, i) => v[i] === x);
  }
  const w = parseV(raw), c = cmpV(v, w);
  if (c === null) return null;
  if (op === ">=") return c >= 0;
  if (op === ">") return c > 0;
  if (op === "<=") return c <= 0;
  if (op === "<") return c < 0;
  if (op === "=") return c === 0;
  if (c < 0) return false;
  const up = op === "~" && w.length >= 2 ? [w[0], w[1] + 1] : [w[0] + 1];
  return cmpV(v, up) < 0;
}

// true = täyttyy, false = ei täyty varmasti, null = ei voida päätellä (ei toimita)
function satisfies(version, range) {
  let unknown = false;
  for (const r of Array.isArray(range) ? range : [range]) {
    for (const alt of String(r).split("||")) {
      let res = true;
      for (const t of alt.trim().split(/\s+/).filter(Boolean)) {
        const x = oneComp(version, t);
        if (x === false) { res = false; break; }
        if (x === null) res = null;
      }
      if (res === true) return true;
      if (res === null) unknown = true;
    }
  }
  return unknown ? null : false;
}

function planFixes(mods, mcVersion, unknown) {
  const out = new Map(), alive = () => mods.filter((m) => !out.has(m.file));
  const byId = new Map();
  for (const m of mods) { if (!byId.has(m.id)) byId.set(m.id, []); byId.get(m.id).push(m); }
  for (const [id, list] of byId) {
    if (list.length < 2) continue;
    list.sort((a, b) => cmpV(parseV(b.version), parseV(a.version)) || 0); // uusin ensin
    for (const m of list.slice(1)) out.set(m.file, `kaksoiskappale modista ${id}`);
  }
  for (let changed = true; changed; ) {
    changed = false;
    const avail = new Map([["minecraft", mcVersion]]);
    const add = (x) => { avail.set(x.id, x.version); x.provides.forEach((p) => avail.set(p, x.version)); x.nested.forEach(add); };
    alive().forEach(add);
    for (const m of alive()) {
      let reason = null;
      for (const [dep, range] of Object.entries(m.depends)) {
        if (SKIP_DEPS.has(dep)) continue;
        if (!avail.has(dep)) { if (!unknown) reason = `puuttuva riippuvuus: ${dep}`; }
        else if (satisfies(avail.get(dep), range) === false) reason = `ei tue ${dep} ${avail.get(dep)}`;
        if (reason) break;
      }
      if (!reason) {
        for (const [dep, range] of Object.entries(m.breaks)) {
          if (dep !== m.id && avail.has(dep) && satisfies(avail.get(dep), range) === true) {
            reason = `yhteensopimaton modin ${dep} kanssa`;
            break;
          }
        }
      }
      if (reason) { out.set(m.file, reason); changed = true; break; } // laske uudelleen yksi kerrallaan
    }
  }
  return out;
}
// </autofix-pure>

function readMeta(src, depth = 0) {
  let zip;
  try { zip = new AdmZip(src); } catch { return null; }
  const fe = zip.getEntry("fabric.mod.json");
  if (!fe) {
    return ["META-INF/mods.toml", "META-INF/neoforge.mods.toml", "mcmod.info"].some((n) => zip.getEntry(n))
      ? { foreign: true } : null;
  }
  let d;
  try { d = JSON.parse(fe.getData().toString("utf8").replace(/^\uFEFF/, "")); } catch { return null; }
  const obj = (x) => (x && typeof x === "object" && !Array.isArray(x) ? x : {});
  const m = {
    id: String(d.id || "").toLowerCase(), version: String(d.version || "0"),
    provides: (Array.isArray(d.provides) ? d.provides : []).map((p) => String(typeof p === "string" ? p : p && p.id).toLowerCase()),
    depends: obj(d.depends), breaks: obj(d.breaks), nested: [],
  };
  if (depth < 2 && Array.isArray(d.jars)) {
    for (const j of d.jars) {
      const e = j && j.file && zip.getEntry(j.file);
      if (e) { const n = readMeta(e.getData(), depth + 1); if (n && !n.foreign) m.nested.push(n); }
    }
  }
  return m;
}

function autoFixMods(slug, mcVersion) {
  const dir = path.join(profDir(slug), "mods");
  if (!fs.existsSync(dir)) return [];
  const mods = [];
  let unknown = 0;
  for (const file of fs.readdirSync(dir)) {
    if (!/\.jar$/i.test(file)) continue;
    const m = readMeta(path.join(dir, file));
    if (!m) unknown++; else if (!m.foreign) mods.push({ file, ...m });
  }
  const fixed = [];
  for (const [file, reason] of planFixes(mods, mcVersion, unknown)) {
    const from = path.join(dir, file), to = from + ".disabled";
    fs.rmSync(to, { force: true });
    fs.renameSync(from, to);
    fixed.push({ file, reason });
  }
  if (fixed.length) {
    const log = path.join(profDir(slug), "logs", "kclient-autofix.txt");
    fs.mkdirSync(path.dirname(log), { recursive: true });
    fs.appendFileSync(log, `[${new Date().toISOString()}] Minecraft ${mcVersion}\n` +
      fixed.map((x) => `  ${x.file} - ${x.reason}`).join("\n") + "\n");
  }
  return fixed;
}

function javaMajor(jp) {
  const r = spawnSync(jp || "java", ["-version"], { encoding: "utf8" });
  const m = /version "(\d+)(?:\.(\d+))?/.exec((r.stderr || "") + (r.stdout || ""));
  return !m ? 0 : m[1] === "1" ? +m[2] : +m[1];
}
function neededJava(mc) {
  const [a, b, c] = mc.split(".").map(Number);
  if (a > 1) return 21;
  return b >= 21 || (b === 20 && c >= 5) ? 21 : b >= 18 ? 17 : b === 17 ? 16 : 8;
}

ipcMain.handle("play", async (e, { acc, prof, set }) => {
  const need = neededJava(prof.version);
  const jp = (set && set.java) || "java";
  if (javaMajor(jp) < need) throw new Error(`Tarvitset Java ${need} tai uudemman: https://adoptium.net`);
  let authorization, refresh = acc.refresh;
  if (acc.type === "ms") {
    send("status", "Päivitetään Microsoft-kirjautuminen...");
    const xbox = await authMgr.refresh(acc.refresh);
    const mc = await xbox.getMinecraft();
    refresh = xbox.save();
    authorization = mc.mclc();
  } else authorization = Authenticator.getAuth(acc.name);

  SUB.forEach((s) => fs.mkdirSync(path.join(profDir(prof.slug), s), { recursive: true }));
  let fixed = [];
  if (!set || set.fix !== false) {
    try { fixed = autoFixMods(prof.slug, prof.version); }
    catch (err) { send("status", "AutoFix epäonnistui: " + err.message); }
  }
  let custom;
  if (prof.fabric) {
    send("status", "Haetaan Fabric...");
    const ls = await getJSON(`https://meta.fabricmc.net/v2/versions/loader/${prof.version}`);
    if (!ls.length) throw new Error(`Fabric ei tue versiota ${prof.version}`);
    const lv = (ls.find((l) => l.loader.stable) || ls[0]).loader.version;
    custom = `fabric-loader-${lv}-${prof.version}`;
    const vj = await getJSON(`https://meta.fabricmc.net/v2/versions/loader/${prof.version}/${lv}/profile/json`);
    const vd = path.join(GAME, "versions", custom);
    fs.mkdirSync(vd, { recursive: true });
    fs.writeFileSync(path.join(vd, custom + ".json"), JSON.stringify(vj));
  }
  const launcher = new Client();
  launcher.on("progress", (p) => send("progress", p));
  launcher.on("debug", (d) => send("status", String(d).slice(0, 120)));
  launcher.on("close", () => send("closed"));
  send("status", "Ladataan peliä...");
  const child = await launcher.launch({
    authorization, root: GAME, javaPath: jp,
    ...(set && +set.w && +set.h ? { window: { width: +set.w, height: +set.h } } : {}),
    version: { number: prof.version, type: "release", ...(custom ? { custom } : {}) },
    memory: { max: `${prof.ram}G`, min: `${Math.max(1, Math.floor(prof.ram / 2))}G` },
    overrides: { gameDirectory: profDir(prof.slug) },
  });
  if (!child) throw new Error("Pelin käynnistys epäonnistui.");
  return { refresh, fixed: fixed.map((x) => x.file) };
});
