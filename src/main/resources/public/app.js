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
const hints = { pan: "10 characters", aadhaar: "12 digits" };

const ICONS = {
  ok: '<svg viewBox="0 0 24 24" width="18" height="18" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8"/><path d="M8 12.4l2.6 2.6 5.5-5.9" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  bad: '<svg viewBox="0 0 24 24" width="18" height="18" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8"/><path d="M9 9l6 6M15 9l-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
  pass: '<svg viewBox="0 0 20 20" width="17" height="17" fill="none"><path d="M4.5 10.4l3.2 3.2 7.8-8.2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  fail: '<svg viewBox="0 0 20 20" width="17" height="17" fill="none"><path d="M5.5 5.5l9 9M14.5 5.5l-9 9" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
  skip: '<svg viewBox="0 0 20 20" width="17" height="17" fill="none"><path d="M5 10h10" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>'
};

let type = "pan";
let timer = null;
let inFlight = 0;

const $ = (id) => document.getElementById(id);

function setType(next) {
  type = next;
  for (const btn of document.querySelectorAll(".seg")) {
    const on = btn.dataset.type === next;
    btn.classList.toggle("active", on);
    btn.setAttribute("aria-selected", on ? "true" : "false");
  }
  $("label-value").textContent = labels[next];
  $("hint").textContent = hints[next];
  $("value").placeholder = placeholders[next];
  $("examples-title").textContent = next === "pan" ? "PAN examples" : "Aadhaar examples";
  renderExamples();
  renderRules();
  if ($("value").value.trim()) {
    validate();
  } else {
    renderEmpty();
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
    b.addEventListener("click", () => { $("value").value = ex.value; syncInputState(); validate(); });
    wrap.appendChild(b);
  }
}

function renderRules() {
  const wrap = $("rules");
  wrap.innerHTML = "";
  for (const r of rules[type]) {
    const li = document.createElement("li");
    li.className = "rule";
    li.textContent = r;
    wrap.appendChild(li);
  }
}

function syncInputState() {
  const wrap = document.querySelector(".input-wrap");
  if (!$("value").value) {
    wrap.classList.remove("has-value");
  } else {
    wrap.classList.add("has-value");
  }
}

function renderEmpty() {
  $("empty").classList.remove("hidden");
  $("result").classList.add("hidden");
  $("preview").classList.add("hidden");
  clearExtraction();
}

function renderLoading() {
  $("empty").classList.add("hidden");
  $("result").classList.remove("hidden");

  const verdict = $("verdict");
  verdict.className = "verdict loading";
  $("v-icon").innerHTML = '<span class="skel-bar" style="width:18px;height:18px;display:inline-block"></span>';
  $("v-status").innerHTML = '<span class="skel-bar" style="width:96px;height:14px;display:inline-block"></span>';
  $("v-reason").classList.add("hidden");
  $("v-reason").textContent = "";
  $("v-message").innerHTML = '<span class="skel-bar" style="width:80%;height:12px"></span>';

  const checks = $("checks");
  checks.innerHTML = "";
  for (let i = 0; i < 4; i++) {
    const li = document.createElement("li");
    li.className = "check skel";
    const w = 55 + ((i * 37) % 35);
    li.innerHTML =
      '<span class="c-ico"><span class="skel-bar" style="width:16px;height:16px;border-radius:50%;display:inline-block"></span></span>' +
      '<span class="c-label"><span class="skel-bar" style="width:' + w + '%;height:11px;display:inline-block"></span></span>' +
      '<span class="c-detail"><span class="skel-bar" style="width:46px;height:11px;display:inline-block"></span></span>';
    checks.appendChild(li);
  }
}

function render(data) {
  $("empty").classList.add("hidden");
  $("result").classList.remove("hidden");
  $("preview").classList.remove("hidden");
  $("normalized").textContent = data.normalizedValue || "\u2014";

  const valid = !!data.valid;
  const verdict = $("verdict");
  verdict.className = "verdict " + (valid ? "ok" : "bad");
  $("v-icon").innerHTML = valid ? ICONS.ok : ICONS.bad;

  $("v-status").textContent = valid ? "Valid format" : "Invalid format";

  const reason = $("v-reason");
  if (data.reasonCode && data.reasonCode !== "VALID") {
    reason.textContent = data.reasonCode;
    reason.classList.remove("hidden");
  } else {
    reason.textContent = "";
    reason.classList.add("hidden");
  }

  $("v-message").textContent = data.message || "";

  const checks = $("checks");
  checks.innerHTML = "";
  const items = data.checks || [];
  for (let i = 0; i < items.length; i++) {
    const c = items[i];
    const li = document.createElement("li");
    li.className = "check " + (c.status || "skip");
    li.style.animationDelay = (i * 45) + "ms";

    const ico = document.createElement("span");
    ico.className = "c-ico";
    ico.innerHTML = c.status === "pass" ? ICONS.pass : c.status === "fail" ? ICONS.fail : ICONS.skip;

    const label = document.createElement("span");
    label.className = "c-label";
    label.textContent = c.label;

    const detail = document.createElement("span");
    detail.className = "c-detail";
    detail.textContent = c.detail || "";

    li.append(ico, label, detail);
    checks.appendChild(li);
  }
}

function renderError() {
  $("empty").classList.add("hidden");
  $("result").classList.remove("hidden");
  const verdict = $("verdict");
  verdict.className = "verdict bad";
  $("v-icon").innerHTML = ICONS.bad;
  $("v-status").textContent = "Service unreachable";
  $("v-reason").classList.add("hidden");
  $("v-reason").textContent = "";
  $("v-message").textContent = "Could not reach the validation service. Please try again.";
  $("checks").innerHTML = "";
}

async function validate() {
  const value = $("value").value;
  const seq = ++inFlight;
  clearExtraction();
  renderLoading();
  try {
    const res = await fetch("/api/validate", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ type, value })
    });
    const data = await res.json();
    if (seq === inFlight) render(data);
  } catch (e) {
    if (seq === inFlight) renderError();
  }
}

/* ---------- Image extraction ---------- */
let selectedFile = null;
let objectUrl = null;
const ALLOWED_IMG = ["image/png", "image/jpeg"];
const MAX_IMG = 5 * 1024 * 1024;

function formatBytes(n) {
  if (n < 1024) return n + " B";
  if (n < 1048576) return (n / 1024).toFixed(1) + " KB";
  return (n / 1048576).toFixed(1) + " MB";
}

function setImageFile(file) {
  if (!file) return;
  if (!ALLOWED_IMG.includes(file.type)) {
    showDropError("Unsupported file type. Use PNG or JPEG.");
    return;
  }
  if (file.size > MAX_IMG) {
    showDropError("File too large. Maximum 5MB.");
    return;
  }
  clearDropError();
  selectedFile = file;
  $("dz-title").textContent = file.name;
  $("dz-sub").textContent = formatBytes(file.size) + " \u2014 processed transiently, never stored";
  if (objectUrl) URL.revokeObjectURL(objectUrl);
  objectUrl = URL.createObjectURL(file);
  const thumb = $("dz-thumb");
  thumb.style.backgroundImage = "url('" + objectUrl + "')";
  thumb.classList.remove("hidden");
  $("dropzone").classList.add("has-file");
  $("extract").disabled = false;
}

function clearImage() {
  selectedFile = null;
  if (objectUrl) { URL.revokeObjectURL(objectUrl); objectUrl = null; }
  $("image-file").value = "";
  $("dz-title").textContent = "Drop a PAN / Aadhaar image";
  $("dz-sub").textContent = "PNG or JPEG, up to 5MB \u2014 processed transiently, never stored";
  $("dz-thumb").classList.add("hidden");
  $("dz-thumb").style.backgroundImage = "";
  $("dropzone").classList.remove("has-file", "drop-error", "drag");
  $("extract").disabled = true;
}

function showDropError(msg) {
  selectedFile = null;
  $("dz-title").textContent = msg;
  $("dz-sub").textContent = "PNG or JPEG, up to 5MB";
  $("dz-thumb").classList.add("hidden");
  $("dropzone").classList.add("drop-error");
  $("dropzone").classList.remove("has-file");
  $("extract").disabled = true;
}

function clearDropError() {
  $("dropzone").classList.remove("drop-error");
}

function renderExtraction(ext) {
  if (!ext) { clearExtraction(); return; }
  const block = $("extraction");
  block.classList.remove("hidden");
  $("ex-type").textContent = ext.extractedType || "UNKNOWN";
  $("ex-conf").textContent = (ext.confidence || "").toLowerCase()
    ? ext.confidence + " confidence" : "";
  $("ex-value").textContent = ext.value || "\u2014";
  const warn = $("ex-warn");
  if (ext.typeMismatch) {
    warn.textContent = "hint (" + (ext.hintType || "").toLowerCase()
      + ") differs from detected (" + (ext.extractedType || "").toLowerCase() + ")";
    warn.classList.remove("hidden");
  } else {
    warn.classList.add("hidden");
  }
}

function clearExtraction() {
  $("extraction").classList.add("hidden");
}

function renderExtractionError(data) {
  $("empty").classList.add("hidden");
  $("result").classList.remove("hidden");
  const verdict = $("verdict");
  verdict.className = "verdict bad";
  $("v-icon").innerHTML = ICONS.bad;
  $("v-status").textContent = "Extraction failed";
  $("v-reason").classList.add("hidden");
  $("v-message").textContent = (data && data.error)
    ? data.error
    : "Could not extract a document number from the image.";
  $("checks").innerHTML = "";
}

async function extractAndValidate() {
  if (!selectedFile) return;
  const seq = ++inFlight;
  clearExtraction();
  renderLoading();
  try {
    const res = await fetch("/api/extract-and-validate?hint=" + encodeURIComponent(type), {
      method: "POST",
      headers: { "Content-Type": selectedFile.type },
      body: selectedFile
    });
    const data = await res.json();
    if (seq !== inFlight) return;
    if (!res.ok) {
      renderExtractionError(data);
      return;
    }
    render(data);
    const extracted = data.extraction || {};
    $("value").value = extracted.value || data.originalValue || "";
    syncInputState();
    renderExtraction(extracted);
  } catch (e) {
    if (seq === inFlight) renderError();
  }
}

function debounce(fn, ms) {
  return (...args) => { clearTimeout(timer); timer = setTimeout(() => fn(...args), ms); };
}

document.addEventListener("DOMContentLoaded", () => {
  for (const btn of document.querySelectorAll(".seg")) {
    btn.addEventListener("click", () => setType(btn.dataset.type));
  }

  const input = $("value");
  const validateDebounced = debounce(validate, 250);
  input.addEventListener("input", () => {
    syncInputState();
    if (input.value.trim()) {
      validateDebounced();
    } else {
      clearTimeout(timer);
      renderEmpty();
    }
  });

  $("input-clear").addEventListener("click", () => {
    input.value = "";
    syncInputState();
    renderEmpty();
    input.focus();
  });

  $("validate").addEventListener("click", validate);
  $("clear").addEventListener("click", () => {
    input.value = "";
    syncInputState();
    renderEmpty();
    input.focus();
  });

  $("image-file").addEventListener("change", (e) => setImageFile(e.target.files[0]));
  const dz = $("dropzone");
  dz.addEventListener("dragover", (e) => { e.preventDefault(); dz.classList.add("drag"); });
  dz.addEventListener("dragleave", () => dz.classList.remove("drag"));
  dz.addEventListener("drop", (e) => {
    e.preventDefault();
    dz.classList.remove("drag");
    if (e.dataTransfer.files && e.dataTransfer.files.length) {
      setImageFile(e.dataTransfer.files[0]);
    }
  });
  $("extract").addEventListener("click", extractAndValidate);
  $("img-clear").addEventListener("click", () => { clearImage(); });

  setType("pan");
});
