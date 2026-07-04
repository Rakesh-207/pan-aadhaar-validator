const { chromium } = require("playwright");
(async () => {
  const b = await chromium.launch({ headless: true });
  // ---------- NON-BYPASS landing ----------
  const p1 = await b.newPage({ viewport: { width: 1280, height: 900 } });
  await p1.goto("http://localhost:8097/", { waitUntil: "load" });
  await p1.waitForTimeout(3000);
  const guestHidden = await p1.isHidden("#guest-continue");
  const guest2Hidden = await p1.isHidden("#guest-continue-2");
  const gisIframe = await p1.$$eval("iframe", ifs => ifs.filter(f => (f.src||"").includes("accounts.google.com")).length);
  // slot chrome
  const slotStyle = await p1.$eval("#g-signin", el => {
    const cs = getComputedStyle(el);
    return { bg: cs.backgroundColor, pad: cs.padding, border: cs.borderWidth, w: el.offsetWidth };
  });
  const rowAlign = await p1.$eval(".btn-group", el => getComputedStyle(el).alignItems);
  console.log("[non-bypass] guest#1 hidden:", guestHidden, "| guest#2 hidden:", guest2Hidden);
  console.log("[non-bypass] GIS iframe rendered:", gisIframe);
  console.log("[non-bypass] g-signin slot:", JSON.stringify(slotStyle));
  console.log("[non-bypass] btn-group align-items:", rowAlign);

  // ---------- BYPASS /app with a session (banner must be hidden) ----------
  const p2 = await b.newPage({ viewport: { width: 1280, height: 900 } });
  // mint a guest cookie via dev-login, then inject it for localhost
  const resp = await p2.request.post("http://localhost:8096/api/auth/dev-login", {
    headers: { "Content-Type": "application/json", "Origin": "http://localhost:8096" },
    data: ""
  });
  const sc = resp.headers()["set-cookie"];
  const cookie = sc.split(";")[0];
  const [name, value] = cookie.split("=");
  await p2.context().addCookies([{ name, value, domain: "localhost", path: "/" }]);
  await p2.goto("http://localhost:8096/app", { waitUntil: "load" });
  await p2.waitForTimeout(1500);
  const bannerVisible = await p2.isVisible("#boot-err");
  const userName = await p2.textContent("#user-name").catch(() => "");
  const bannerDefault = await p2.$eval("#boot-err", el => el.hasAttribute("hidden"));
  console.log("[bypass /app] #boot-err has hidden attr:", bannerDefault, "| isVisible:", bannerVisible);
  console.log("[bypass /app] user-name chip:", userName.trim());
  console.log("[bypass /app] /me-status via page check done");
  await p2.screenshot({ path: "/tmp/app-bypass.png" });
  await p1.screenshot({ path: "/tmp/landing-nonbypass.png" });
  await b.close();
})();
