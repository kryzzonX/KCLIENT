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
  return { ok: true, total: files.length, failed, name: v.name };
});

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
