 1→/**
 2→ * Welcome to your Workbox-powered service worker!
 3→ *
 4→ * You'll need to register this file in your web app and you should
 5→ * disable HTTP caching for this file too.
 6→ * See https://goo.gl/nhQhGp
 7→ *
 8→ * The rest of the code is auto-generated. Please don't update this file
 9→ * directly; instead, make changes to your Workbox build configuration
10→ * and re-run your build process.
11→ * See https://goo.gl/2aRDsh
12→ */
13→
14→importScripts("https://storage.googleapis.com/workbox-cdn/releases/3.6.3/workbox-sw.js");
15→
16→importScripts(
17→  "precache-manifest.4dbcb29d8c04aa102e92a2de66ed5931.js"
18→);
19→
20→workbox.core.setCacheNameDetails({prefix: "mall-sen-admin"});
21→
22→/**
23→ * The workboxSW.precacheAndRoute() method efficiently caches and responds to
24→ * requests for URLs in the manifest.
25→ * See https://goo.gl/S9QRab
26→ */
27→self.__precacheManifest = [].concat(self.__precacheManifest || []);
28→workbox.precaching.suppressWarnings();
29→workbox.precaching.precacheAndRoute(self.__precacheManifest, {});