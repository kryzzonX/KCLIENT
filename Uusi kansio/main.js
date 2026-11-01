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

// ---------- K Client -mod (click GUI): kopioidaan asennusohjelman mukana tuleva jar profiilin mods-kansioon
const KMOD_VERSIONS = ["1.21.4", "26.3"];
function bundledMod(ver) {
  const dirs = [process.resourcesPath ? path.join(process.resourcesPath, "mod") : "", path.join(__dirname, "mod-jar")];
  for (const d of dirs) {
    try {
      const f = fs.readdirSync(d).find((x) => x.toLowerCase().startsWith(`k-client-${ver}-`) && x.endsWith(".jar") && !/sources|dev/i.test(x));
      if (f) return path.join(d, f);
    } catch {}
  }
  return null;
}
ipcMain.handle("kmod:install", (e, { slug, ver }) => {
  if (!KMOD_VERSIONS.includes(ver)) return { ok: false, reason: "version" };
  const src = bundledMod(ver);
  if (!src) return { ok: false, reason: "missing" };
  const dir = path.join(profDir(slug), "mods");
  fs.mkdirSync(dir, { recursive: true });
  for (const f of fs.readdirSync(dir)) {
    if (/^k-?client.*\.jar(\.disabled)?$/i.test(f)) fs.rmSync(path.join(dir, f), { force: true });
  }
  fs.copyFileSync(src, path.join(dir, path.basename(src)));
  return { ok: true };
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

// Siivoaa vanhoista profiileista OneConfig/Polyfrost-modit ja kaikki modit, jotka tarvitsevat niitä.
const LEGACY_RE = /one.?config|polyfrost|polymod|polyplus/i;
function findLegacy(mods) {
  const out = new Map();
  const ids = (m) => [m.id, ...m.provides, ...m.nested.flatMap((n) => [n.id, ...n.provides])];
  for (const m of mods) {
    if (LEGACY_RE.test(m.file) || ids(m).some((i) => LEGACY_RE.test(i)) || Object.keys(m.depends).some((k) => LEGACY_RE.test(k))) {
      out.set(m.file, "vanha Polyfrost-mod");
    }
  }
  for (let changed = true; changed; ) {
    changed = false;
    const gone = new Set();
    for (const m of mods) if (out.has(m.file)) ids(m).forEach((i) => gone.add(i));
    for (const m of mods) {
      if (out.has(m.file)) continue;
      const dep = Object.keys(m.depends).find((k) => gone.has(k));
      if (dep) { out.set(m.file, `tarvitsee modia ${dep}`); changed = true; }
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

function autoFixMods(slug, mcVersion, doFix = true) {
  const dir = path.join(profDir(slug), "mods");
  if (!fs.existsSync(dir)) return [];
  const mods = [], rawUnknown = [];
  for (const file of fs.readdirSync(dir)) {
    if (!/\.jar$/i.test(file)) continue;
    const m = readMeta(path.join(dir, file));
    if (!m) rawUnknown.push(file); else if (!m.foreign) mods.push({ file, ...m });
  }
  const plan = findLegacy(mods);
  for (const f of rawUnknown) if (LEGACY_RE.test(f)) plan.set(f, "vanha Polyfrost-mod");
  if (doFix) {
    const remaining = mods.filter((m) => !plan.has(m.file));
    for (const [f, r] of planFixes(remaining, mcVersion, rawUnknown.length)) plan.set(f, r);
  }
  const fixed = [];
  for (const [file, reason] of plan) {
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
  if (a > 1) return 25; // 26.x vaatii Java 25
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
  try { fixed = autoFixMods(prof.slug, prof.version, !set || set.fix !== false); }
  catch (err) { send("status", "AutoFix epäonnistui: " + err.message); }
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
