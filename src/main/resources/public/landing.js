// Landing page auth wiring. Decides between real Google Sign In (GIS) and the
// local dev bypass based on /api/auth/config, handles the ID-token -> session
// cookie exchange, and navigates to /app on success.
(function () {
  "use strict";
  var ctaArea = document.getElementById("cta-area");
  var gSlot = document.getElementById("g-signin");
  var devBtn = document.getElementById("dev-continue");
  var note = document.getElementById("cta-note");

  function showNote(msg, isError) {
    if (!note) return;
    note.textContent = msg;
    note.className = "hero-foot" + (isError ? " hero-foot-err" : "");
    note.hidden = false;
  }

  function postJson(url, body) {
    return fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      credentials: "same-origin"
    });
  }

  function goToApp() { window.location.assign("/app"); }

  // Already signed in? Skip straight to the app for a returning user.
  fetch("/api/auth/me", { credentials: "same-origin" })
    .then(function (r) { if (r.ok) goToApp(); })
    .catch(function () {});

  fetch("/api/auth/config", { credentials: "same-origin" })
    .then(function (r) { return r.json(); })
    .then(function (cfg) { setup(cfg); })
    .catch(function () { showNote("Could not reach the auth service.", true); });

  function setup(cfg) {
    if (cfg.mode === "dev_bypass") {
      devBtn.hidden = false;
      devBtn.addEventListener("click", function () {
        devBtn.disabled = true;
        postJson("/api/auth/dev-login", {}).then(function (r) {
          if (r.ok) goToApp();
          else showNote("Local sign-in failed.", true);
        }).catch(function () { showNote("Network error.", true); });
      });
      showNote("Dev mode: local bypass is on (never used in production).", false);
      return;
    }
    if (!cfg.googleClientId) {
      showNote("Google sign-in is not configured on this server.", true);
      return;
    }
    loadGis(cfg.googleClientId);
  }

  function loadGis(clientId) {
    var s = document.createElement("script");
    s.src = "https://accounts.google.com/gsi/client";
    s.async = true;
    s.defer = true;
    s.onload = function () { initGis(clientId); };
    s.onerror = function () { showNote("Could not load Google Identity Services.", true); };
    document.head.appendChild(s);
  }

  function initGis(clientId) {
    if (!window.google || !google.accounts || !google.accounts.id) {
      showNote("Google Identity Services unavailable.", true);
      return;
    }
    google.accounts.id.initialize({
      client_id: clientId,
      callback: onCredential
    });
    google.accounts.id.renderButton(gSlot, {
      type: "standard",
      theme: "outline",
      size: "large",
      shape: "pill",
      text: "continue_with",
      width: 260
    });
  }

  function onCredential(response) {
    if (!response || !response.credential) {
      showNote("No credential returned by Google.", true);
      return;
    }
    postJson("/api/auth/google", { credential: response.credential })
      .then(function (r) {
        if (r.ok) goToApp();
        else if (r.status === 401) showNote("Google could not verify that sign-in.", true);
        else showNote("Sign-in failed. Please try again.", true);
      })
      .catch(function () { showNote("Network error during sign-in.", true); });
  }
})();
