const examples = {
  pan: [
    { label: "AFZPK7190K", value: "AFZPK7190K" },
    { label: "lowercase", value: "afzpk7190k" },
    { label: "with spaces", value: "AFZPK 7190 K" },
    { label: "bad category", value: "ABCDE1234F" },
    { label: "too short", value: "ABC123" }
  ],
  aadhaar: [
    { label: "valid sample", value: "234567890124" },
    { label: "grouped", value: "2345 6789 0124" },
    { label: "starts with 0", value: "012345678901" },
    { label: "bad checksum", value: "234567890120" },
    { label: "too short", value: "2345678901" }
  ]
};

const rules = {
  pan: [
    "Exactly 10 characters.",
    "Positions 1-5 are letters; positions 6-9 are digits; position 10 is a letter.",
    "4th character is a valid holder category: P, C, H, A, B, G, J, L, F, T.",
    "Spaces and hyphens are ignored; letters are upper-cased automatically."
  ],
  aadhaar: [
    "Exactly 12 digits (0-9 only).",
    "Must not start with 0 or 1 (UIDAI rule).",
    "Locale digits (e.g. Devanagari) are rejected.",
    "Final digit is validated using the Verhoeff checksum algorithm."
  ]
};

const placeholders = { pan: "e.g. AFZPK7190K", aadhaar: "e.g. 2345 6789 0124" };
const labels = { pan: "Enter PAN", aadhaar: "Enter Aadhaar number" };

let type = "pan";
let timer = null;

const $ = (id) => document.getElementById(id);

function setType(next) {
  type = next;
  for (const btn of document.querySelectorAll(".seg")) {
    const on = btn.dataset.type === next;
    btn.classList.toggle("active", on);
    btn.setAttribute("aria-selected", on ? "true" : "false");
  }
  $("label-value").textContent = labels[next];
  $("value").placeholder = placeholders[next];
  $("examples-title").textContent = next === "pan" ? "PAN examples" : "Aadhaar examples";
  renderExamples();
  renderRules();
  if ($("value").value.trim()) {
    validate();
  } else {
    $("preview").classList.add("hidden");
    $("result").classList.add("hidden");
  }
}

function renderExamples() {
  const wrap = $("examples");
  wrap.innerHTML = "";
  for (const ex of examples[type]) {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "chip";
    b.textContent = ex.label;
    b.title = ex.value;
    b.addEventListener("click", () => { $("value").value = ex.value; validate(); });
    wrap.appendChild(b);
  }
}

function renderRules() {
  const wrap = $("rules");
  wrap.innerHTML = "";
  for (const r of rules[type]) {
    const li = document.createElement("div");
    li.className = "rule";
    li.textContent = r;
    wrap.appendChild(li);
  }
}

async function validate() {
  const value = $("value").value;
  try {
    const res = await fetch("/api/validate", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ type, value })
    });
    const data = await res.json();
    render(data);
  } catch (e) {
    renderError();
  }
}

function render(data) {
  $("preview").classList.remove("hidden");
  $("result").classList.remove("hidden");
  $("normalized").textContent = data.normalizedValue || "—";

  const badge = $("badge");
  badge.className = "badge " + (data.valid ? "ok" : "bad");
  badge.textContent = data.valid ? "VALID FORMAT" : "INVALID";
  $("message").textContent = data.message || "";
  $("reason").textContent = data.reasonCode && data.reasonCode !== "VALID"
      ? "Reason code: " + data.reasonCode : "";

  const checks = $("checks");
  checks.innerHTML = "";
  for (const c of (data.checks || [])) {
    const li = document.createElement("li");
    li.className = "check " + (c.status || "skip");
    const dot = document.createElement("span");
    dot.className = "dot";
    const label = document.createElement("span");
    label.className = "clabel";
    label.textContent = c.label;
    const detail = document.createElement("span");
    detail.className = "cdetail";
    detail.textContent = c.detail || "";
    li.append(dot, label, detail);
    checks.appendChild(li);
  }
}

function renderError() {
  $("result").classList.remove("hidden");
  $("badge").className = "badge bad";
  $("badge").textContent = "ERROR";
  $("message").textContent = "Could not reach the validation service.";
  $("reason").textContent = "";
  $("checks").innerHTML = "";
}

function debounce(fn, ms) {
  return (...args) => { clearTimeout(timer); timer = setTimeout(() => fn(...args), ms); };
}

document.addEventListener("DOMContentLoaded", () => {
  for (const btn of document.querySelectorAll(".seg")) {
    btn.addEventListener("click", () => setType(btn.dataset.type));
  }
  $("value").addEventListener("input", debounce(validate, 250));
  $("validate").addEventListener("click", validate);
  $("clear").addEventListener("click", () => {
    $("value").value = "";
    $("preview").classList.add("hidden");
    $("result").classList.add("hidden");
    $("value").focus();
  });
  setType("pan");
});
