// Shared session UI for the protected app. Loads the signed-in user, renders the
// chip, wires logout, and bounces to the landing page when the session is gone.
(function () {
  "use strict";
  var userName = document.getElementById("user-name");
  var userEmail = document.getElementById("user-email");
  var userAvatar = document.getElementById("user-avatar");
  var logoutBtn = document.getElementById("logout");
  var bootErr = document.getElementById("boot-err");

  function bail() { window.location.assign("/"); }

  function initials(name) {
    if (!name) return "?";
    var parts = name.trim().split(/\s+/).slice(0, 2);
    return parts.map(function (p) { return p.charAt(0).toUpperCase(); }).join("");
  }

  function renderUser(u) {
    if (userName) userName.textContent = u.name || u.email || "User";
    if (userEmail) userEmail.textContent = u.email || "";
    if (userAvatar) {
      if (u.picture) {
        var img = document.createElement("img");
        img.src = u.picture;
        img.alt = "";
        img.referrerPolicy = "no-referrer";
        userAvatar.appendChild(img);
      } else {
        userAvatar.textContent = initials(u.name || u.email);
      }
    }
  }

  function showBootErr() {
    if (bootErr) bootErr.hidden = false;
    else bail();
  }

  // The single session check on /app. 401 -> back to sign-in; a genuine
  // network/server failure -> show the banner; 200 -> render the user and
  // make absolutely sure any stale banner is hidden.
  fetch("/api/auth/me", { credentials: "same-origin" })
    .then(function (r) {
      if (r.status === 401) { bail(); return null; }
      if (!r.ok) { showBootErr(); return null; }
      return r.json();
    })
    .then(function (u) {
      if (!u) return;
      if (bootErr) bootErr.hidden = true;
      renderUser(u);
    })
    .catch(function () { showBootErr(); });

  if (logoutBtn) {
    logoutBtn.addEventListener("click", function () {
      logoutBtn.disabled = true;
      fetch("/api/auth/logout", {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" }
      }).catch(function () {}).finally(function () { bail(); });
    });
  }

  document.addEventListener("DOMContentLoaded", function () {
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
