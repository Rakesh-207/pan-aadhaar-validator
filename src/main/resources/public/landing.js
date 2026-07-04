// Landing page auth wiring. Decides between real Google Sign In (GIS) and the
// guest fallback based on /api/auth/config, handles the ID-token -> session
// cookie exchange, and navigates to /app on success.
(function () {
  "use strict";
  var gSlot = document.getElementById("g-signin");
  var note = document.getElementById("note-area");

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
    var guestBtn1 = document.getElementById("guest-continue");
    var guestBtn2 = document.getElementById("guest-continue-2");
    if (cfg.mode === "google" && cfg.googleClientId) {
      // Production: Google Sign-In only. Remove the guest/dev CTAs (and the
      // guest-oriented final CTA section) from the DOM entirely so neither
      // their text nor any path to dev-login is present in production.
      if (guestBtn1) guestBtn1.remove();
      if (guestBtn2 && guestBtn2.closest) {
        var cta = guestBtn2.closest("section");
        if (cta) { cta.remove(); } else { guestBtn2.remove(); }
      }
      loadGis(cfg.googleClientId);
    } else {
      // Local dev-bypass: no Google button; guest login is the entry point.
      if (gSlot) gSlot.hidden = true;
      showNote("Guest mode is active. Try as Guest is fully available.", false);
    }
  }

  function loginAsGuest(btn) {
    btn.disabled = true;
    postJson("/api/auth/dev-login", {})
      .then(function (r) {
        if (r.ok) {
          goToApp();
        } else {
          showNote("Guest sign-in failed.", true);
          btn.disabled = false;
        }
      })
      .catch(function () {
        showNote("Network error during guest sign-in.", true);
        btn.disabled = false;
      });
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
      text: "continue_with"
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

  document.addEventListener("DOMContentLoaded", function () {
    var guestBtn1 = document.getElementById("guest-continue");
    var guestBtn2 = document.getElementById("guest-continue-2");

    if (guestBtn1) {
      guestBtn1.addEventListener("click", function () { loginAsGuest(guestBtn1); });
    }
    if (guestBtn2) {
      guestBtn2.addEventListener("click", function () { loginAsGuest(guestBtn2); });
    }

    var toggle = document.getElementById("theme-toggle");
    if (toggle) {
      toggle.addEventListener("click", function () {
        var current = document.documentElement.getAttribute("data-theme");
        if (!current) {
          current = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
        }
        var next = (current === "dark") ? "light" : "dark";
        document.documentElement.setAttribute("data-theme", next);
        localStorage.setItem("theme", next);
        var meta = document.getElementById("theme-color");
        if (meta) {
          meta.setAttribute("content", next === "dark" ? "#0c0d0e" : "#faf9f6");
        }
      });
    }
  });
})();
