# PAN / Aadhaar Format Validator

A **Core Java** web application that validates the **format** of Indian **PAN** and **Aadhaar**
numbers. The validation engine, HTTP server, and API are all plain Java. There is **no Spring
Boot, no database, and no external runtime dependency**. The frontend is static HTML/CSS/vanilla
JS served by the Java server.

> **Disclaimer:** Format validation is **not** identity verification. This tool cannot confirm
> whether a PAN or Aadhaar was actually issued by the Income Tax Department or UIDAI. It only
> checks structural rules and (for Aadhaar) the Verhoeff checksum.

---

## Why Core Java (not Spring Boot)?

The problem is deterministic string validation: there is no I/O state, no persistence, and no
need for a web framework. A Core Java solution using the built-in
`com.sun.net.httpserver.HttpServer` keeps the project honest — **Java owns the validation logic,
the API, and the runtime**, with zero application-framework overhead. It is also
**privacy-friendly**: numbers are never stored or logged.

---

## Tech stack

| Layer | Choice |
|------|--------|
| Language / runtime | Java 21 (OpenJDK / Temurin-compatible) |
| HTTP server | `com.sun.net.httpserver.HttpServer` (JDK built-in) |
| Build | Maven |
| Tests | JUnit 5 |
| Frontend | Static HTML + CSS + vanilla JS |
| Database | **None** |

---

## Project structure

```
pan-aadhaar-validator/
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/validator
    │   │   ├── Main.java
    │   │   ├── core/    (Normalizer, Verhoeff, PanValidator, AadhaarValidator, DocumentValidator)
    │   │   ├── model/   (DocumentType, ReasonCode, ValidationResult)
    │   │   ├── json/    (Json)
    │   │   └── server/  (ValidationServer)
    │   └── resources/public
    │       ├── index.html
    │       ├── styles.css
    │       └── app.js
    └── test/java/com/validator
        ├── VerhoeffTest.java
        ├── NormalizerTest.java
        ├── PanValidatorTest.java
        └── AadhaarValidatorTest.java
```

---

## How to run

Prerequisites: JDK 21 and Maven.

### Local setup on macOS (Homebrew OpenJDK 21)

On Apple Silicon macOS, Homebrew's `openjdk@21` is **not** linked onto the default `PATH`
(it is keg-only). Export `JAVA_HOME` and put its `bin` on your `PATH` before running Maven
or the jar:

```bash
brew install openjdk@21 maven
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# verify
java -version     # openjdk version "21..."
javac -version
mvn -v
```

> Tip: add the two `export` lines to your `~/.zshrc` so they persist across terminals.

### Build and run

```bash
# 1. Run the tests
mvn test

# 2. Build the runnable jar
mvn package

# 3. Start the server (default port 8080)
java -jar target/pan-aadhaar-validator-1.0.0.jar

# Open the app
#   http://localhost:8080
```

Use a custom port:

```bash
PORT=9000 java -jar target/pan-aadhaar-validator-1.0.0.jar
# or
java -jar target/pan-aadhaar-validator-1.0.0.jar 9000
```

---

## API

### Health

```bash
curl 'http://localhost:8080/api/health'
# {"status":"UP","service":"pan-aadhaar-validator","version":"1.0.0"}
```

### Validate

The API supports **two methods**. `POST` is preferred — it keeps PAN/Aadhaar values out of
URLs and server access logs (stronger privacy/KYC hygiene). `GET` is retained for demos and
backward compatibility.

**`POST /api/validate`** (preferred) — `Content-Type: application/json`:

```bash
# PAN (valid government-style example)
curl -X POST http://localhost:8080/api/validate \
  -H 'Content-Type: application/json' \
  -d '{"type":"pan","value":"AFZPK7190K"}'

# Aadhaar (valid-format sample)
curl -X POST http://localhost:8080/api/validate \
  -H 'Content-Type: application/json' \
  -d '{"type":"aadhaar","value":"234567890124"}'

# Invalid PAN (4th character 'D' is not a valid category)
curl -X POST http://localhost:8080/api/validate \
  -H 'Content-Type: application/json' \
  -d '{"type":"pan","value":"ABCDE1234F"}'
```

**`GET /api/validate?type=pan|aadhaar&value=...`** (demo / backward compat):

```bash
curl 'http://localhost:8080/api/validate?type=pan&value=AFZPK7190K'
curl 'http://localhost:8080/api/validate?type=aadhaar&value=234567890124'
```

Error responses: invalid `type` → `400`; non-JSON `Content-Type` on POST → `415`; body too
large (>8 KB) → `400`; any other method → `405` (`Allow: GET, POST`).

Response shape:

```json
{
  "valid": true,
  "documentType": "PAN",
  "originalValue": "AFZPK7190K",
  "normalizedValue": "AFZPK7190K",
  "reasonCode": "VALID",
  "message": "Valid PAN format. Format validation is not identity verification.",
  "checks": [
    { "label": "Input provided", "status": "pass", "detail": "Value received" },
    { "label": "Only allowed characters (A-Z, 0-9)", "status": "pass", "detail": "Letters and digits only" },
    { "label": "Length is 10 characters", "status": "pass", "detail": "10 characters" },
    { "label": "Positions 1-5 are letters", "status": "pass", "detail": "A-Z in positions 1-5" },
    { "label": "Positions 6-9 are digits", "status": "pass", "detail": "0-9 in positions 6-9" },
    { "label": "Position 10 is a letter", "status": "pass", "detail": "Check letter" },
    { "label": "4th character is a valid category", "status": "pass", "detail": "'P' = Individual (Person)" }
  ]
}
```

---

## Deployment (Docker / Fly.io)

> Once the app is containerized, **deployment does not depend on your local `JAVA_HOME` /
> `PATH`**. The JDK/JRE is provided by the Docker image; you do not need Java installed locally
> to build or run the image on Fly (Fly builds remotely with `fly deploy --remote-only`).

### Docker image

The repo ships a multi-stage `Dockerfile` (Maven+JDK 21 build stage, JRE 21 runtime stage,
non-root user, container-aware JVM flags). The app reads `PORT` (default `8080`) and binds
`0.0.0.0`, which is what Fly's proxy expects.

```bash
# Build (needs a local Docker daemon)
docker build -t pan-aadhaar-validator .

# Run on http://localhost:8080
docker run --rm -p 8080:8080 -e PORT=8080 pan-aadhaar-validator
```

### Fly.io

```bash
fly auth login
fly apps create pan-aadhaar-validator        # if the name is taken, append a suffix
fly deploy                                    # builds + deploys (remote builder if no local Docker)
fly apps open                                 # opens https://pan-aadhaar-validator.fly.dev
curl https://pan-aadhaar-validator.fly.dev/api/health
```

`fly.toml` is preconfigured: `internal_port = 8080`, `force_https = true`,
`auto_stop_machines = true`, `auto_start_machines = true`, `min_machines_running = 0`, with a
health check on `/api/health`. Region defaults to `bom` (Mumbai); change `primary_region` to
suit your audience.

---

## Validation rules

### PAN — 10 characters: 5 letters, 4 digits, 1 letter

- Positions 1-3: alphabetic series (AAA–ZZZ).
- Position 4 (the 4th character): holder status. Must be one of:

| Letter | Meaning |
|--------|---------|
| P | Individual (Person) |
| C | Company |
| H | Hindu Undivided Family (HUF) |
| A | Association of Persons (AOP) |
| B | Body of Individuals (BOI) |
| G | Government Agency |
| J | Artificial Juridical Person |
| L | Local Authority |
| F | Firm / LLP |
| T | Trust (AOP) |

- Position 5: first letter of surname (individual) or entity name.
- Positions 6-9: sequential digits 0001–9999.
- Position 10: alphabetic check letter.
- Normalization: spaces/hyphens removed, letters upper-cased.

### Aadhaar — 12 digits

- Exactly 12 ASCII digits (`0`–`9`).
- The first digit must **not** be `0` or `1` (UIDAI never issues citizen Aadhaars starting with 0/1).
- The 12th digit is a **Verhoeff checksum** computed from the first 11; the full number is valid
  only if the Verhoeff result is `0`.
- Locale digits (e.g. Devanagari `२३४५`) are rejected — only ASCII digits are accepted.

### Reason codes

| Code | Meaning |
|------|---------|
| `VALID` | Format is valid (still not proof of issuance). |
| `EMPTY` | Input was empty. |
| `INVALID_CHARACTER` | An unexpected character was found. |
| `PAN_WRONG_LENGTH` | PAN is not 10 characters after normalization. |
| `PAN_EXPECTED_LETTER` | A letter was expected at a position. |
| `PAN_EXPECTED_DIGIT` | A digit was expected at a position. |
| `PAN_INVALID_CATEGORY` | 4th character is not a valid holder category. |
| `AADHAAR_WRONG_LENGTH` | Aadhaar is not 12 digits. |
| `AADHAAR_NON_ASCII_DIGIT` | A locale / non-ASCII digit was used. |
| `AADHAAR_LEADING_DIGIT` | Number starts with 0 or 1. |
| `AADHAAR_VERHOEFF_FAILED` | Verhoeff checksum did not pass. |

---

## Verhoeff algorithm

Aadhaar uses the Verhoeff check-digit algorithm (chosen by UIDAI because it detects all
single-digit errors and all adjacent transpositions). It is implemented in `Verhoeff.java` using
three lookup tables — the dihedral-D5 multiplication table `D`, the permutation table `P`, and
the inverse table `INV`.

Validation processes digits right-to-left: `c = D[c][P[i % 8][digit]]`; the number is valid iff
`c == 0`.

Verified vectors (see `VerhoeffTest`): `236` → check digit `3` → `2363` validates; `12345` → `1`;
`123456789012` → `0`.

---

## Limitations

- **No identity verification.** A "valid format" result does **not** mean the number was issued to
  a real person. Only the Income Tax Department / UIDAI can confirm issuance.
- **No storage.** PAN/Aadhaar inputs are never written to disk or a database; they live only in the
  in-flight request.
- **Format-only.** No de-duplication, no demographic checks, no biometrics.

---

## Source references

- Income Tax India — *How PAN is formed*:
  https://www.incometaxindia.gov.in/w/how-pan-is-formed-and-how-it-gets-its-unique-identity-
- Wikipedia — *Permanent account number*: https://en.wikipedia.org/wiki/Permanent_account_number
- UIDAI — *Aadhaar generation / API error handling*: https://uidai.gov.in/
- Wikipedia — *Verhoeff algorithm*: https://en.wikipedia.org/wiki/Verhoeff_algorithm
