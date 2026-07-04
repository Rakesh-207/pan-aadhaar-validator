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

  fetch("/api/auth/me", { credentials: "same-origin" })
    .then(function (r) {
      if (!r.ok) { bail(); return null; }
      return r.json();
    })
    .then(function (u) { if (u) renderUser(u); })
    .catch(function () {
      if (bootErr) bootErr.hidden = false;
      else bail();
    });

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
})();
