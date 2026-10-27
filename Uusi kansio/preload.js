const { contextBridge, ipcRenderer } = require("electron");
const inv = (c) => (a) => ipcRenderer.invoke(c, a);
contextBridge.exposeInMainWorld("api", {
  versions: inv("versions"), ensure: inv("ensure"), openDir: inv("open"),
  copyProfile: inv("copy"), removeProfile: inv("remove"),
  msLogin: inv("ms:login"), search: inv("mr:search"), install: inv("mr:install"), play: inv("play"), listMods: inv("mods:list"), toggleMod: inv("mods:toggle"),
  removeMod: inv("mods:remove"), installPack: inv("pack:install"),
  on: (ch, fn) => ipcRenderer.on(ch, (e, d) => fn(d)),
});
