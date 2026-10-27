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

// ---------- Automaattinen modien korjaus ennen käynnistystä
// Poistaa käytöstä modit, joiden pakollinen riippuvuus puuttuu tai jotka
// ilmoittavat Fabric-metatiedoissa rikkovansa toisen asennetun modin.
function autoFixMods(slug) {
  const dir = path.join(profDir(slug), "mods");
  if (!fs.existsSync(dir)) return [];
  const files = fs.readdirSync(dir).filter((f) => /\\.jar(\\.disabled)?$/i.test(f));
  const entries = files.map((file) => {
    let info = null;
    try {
      const jar = new AdmZip(path.join(dir, file));
      const fabric = jar.getEntry("fabric.mod.json");
      if (fabric) {
        const d = JSON.parse(fabric.getData().toString("utf8"));
        info = {
          id: String(d.id || "").toLowerCase(),
          required: Object.keys(d.depends || {}).map((id) => id.toLowerCase()),
          breaks: Object.keys(d.breaks || {}).map((id) => id.toLowerCase())
        };
      } else {
        const quilt = jar.getEntry("quilt.mod.json");
        if (quilt) {
          const d = JSON.parse(quilt.getData().toString("utf8")).quilt_loader || {};
          const deps = (d.depends || []).filter((x) => x && x.id);
          info = {
            id: String(d.id || "").toLowerCase(),
            required: deps.filter((x) => x.type !== "optional").map((x) => String(x.id).toLowerCase()),
            breaks: []
          };
        }
      }
    } catch {}
    return { file, enabled: file.toLowerCase().endsWith(".jar"), info };
  });
  const enabled = new Map();
  for (const entry of entries) {
    if (entry.enabled && entry.info && entry.info.id) enabled.set(entry.info.id, entry);
  }
  const disable = new Set();
  const reasons = new Map();

  // Puuttuvat tai jo käytöstä poistetut pakolliset riippuvuudet.
  for (const entry of entries) {
    if (!entry.enabled || !entry.info) continue;
    for (const dep of entry.info.required) {
      if (!enabled.has(dep)) {
        disable.add(entry.file);
        reasons.set(entry.file, `pakollinen riippuvuus puuttuu: ${dep}`);
        break;
      }
    }
  }

  // Fabricin "breaks"-ilmoitukset: poistetaan käytöstä ne modit, jotka
  // ilmoittavat olevansa yhteensopimattomia toisen päällä olevan modin kanssa.
  for (const entry of entries) {
    if (!entry.enabled || !entry.info || disable.has(entry.file)) continue;
    const conflict = entry.info.breaks.find((id) => enabled.has(id) && !disable.has(enabled.get(id).file));
    if (conflict) {
      disable.add(entry.file);
      reasons.set(entry.file, `yhteensopimaton modin ${conflict} kanssa`);
    }
  }

  // Riippuvuusketjut: jos riippuvuus päätettiin poistaa käytöstä, poista myös
  // kaikki sitä pakollisesti tarvitsevat modit käytöstä.
  let changed = true;
  while (changed) {
    changed = false;
    for (const entry of entries) {
      if (!entry.enabled || !entry.info || disable.has(entry.file)) continue;
      const dep = entry.info.required.find((id) => {
        const target = enabled.get(id);
        return !target || disable.has(target.file);
      });
      if (dep) {
        disable.add(entry.file);
        reasons.set(entry.file, `riippuvuus ${dep} ei ole käytössä`);
        changed = true;
      }
    }
  }

  const changedFiles = [];
  for (const file of disable) {
    const from = path.join(dir, file);
    const to = path.join(dir, file + ".disabled");
    if (fs.existsSync(from)) {
      fs.renameSync(from, to);
      changedFiles.push(file);
      send("status", `AutoFix: poistetaan käytöstä ${file} (${reasons.get(file) || "modiyhteensopivuus"})`);
    }
  }
  return changedFiles;
}

// ---------- Peli
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
  const autoFixed = autoFixMods(prof.slug);
  if (autoFixed.length) send("status", `AutoFix: poistettiin käytöstä ${autoFixed.length} ongelmallista modia.`);
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
  return { refresh };
});
