# 🍳 Smart Recipe Generator

An AI-powered web application that accepts ingredients from your fridge/pantry and generates three distinct, chef-quality recipes using OpenAI's GPT API.

---

## Features

| Requirement | Implementation |
|---|---|
| 3 distinct recipe options | GPT-4o-mini generates exactly 3 creative recipes per request |
| Dietary restriction filters | Vegan, Vegetarian, Gluten-Free, Nut-Free, Dairy-Free, Keto |
| Metric ↔ Imperial toggle | Real-time JS conversion (g↔oz, ml↔fl oz, kg↔lbs, °C↔°F, etc.) |
| < 5 s AI response time | `gpt-4o-mini` typically responds in 2–4 s |
| Input sanitization | Server-side whitelist validation + client-side HTML/injection stripping |
| Web interface | Single-page app served by Akka HTTP |
| LLM API integration | OpenAI Chat Completions (`/v1/chat/completions`) |

---

## Tech Stack

- **Backend**: Scala 2.13 · Akka HTTP 10.5 · Circe JSON
- **LLM**: OpenAI `gpt-4o-mini` (JSON mode)
- **Frontend**: Vanilla HTML5 / CSS3 / JavaScript (no frameworks)

---

## Quick Start

### 1. Prerequisites

- JDK 11+
- sbt 1.x
- An [OpenAI API key](https://platform.openai.com/api-keys)

### 2. Set your API key

```bash
export OPENAI_API_KEY="sk-..."
```

> Alternatively, edit `src/main/resources/application.conf` and replace `YOUR_OPENAI_API_KEY_HERE`.

### 3. Run

```bash
cd Receipe-Generator
sbt run
```

The server starts at **http://localhost:8080/**

### 4. Use

1. Type ingredients into the tag input, pressing **Enter** or **,** after each one.
2. (Optional) Check dietary restrictions.
3. Click **Generate Recipes**.
4. Toggle the **Metric / Imperial** switch to convert all measurements.

---

## API Reference

### `POST /api/generate-recipes`

**Request body (JSON)**

```json
{
  "ingredients": ["chicken", "garlic", "lemon", "thyme"],
  "dietaryRestrictions": ["gluten-free"]
}
```

**Success response (200)**

```json
{
  "recipes": [
    {
      "title": "Lemon Herb Roasted Chicken",
      "description": "...",
      "prepTime": "15 minutes",
      "cookTime": "45 minutes",
      "servings": 4,
      "dietaryInfo": ["gluten-free"],
      "ingredients": [{ "name": "chicken thighs", "amount": "800g" }],
      "steps": [{ "stepNumber": 1, "instruction": "Preheat oven to 200°C." }]
    }
  ],
  "generationTimeMs": 3241
}
```

**Error response (4xx / 5xx)**

```json
{ "error": "Human-readable error message" }
```

### `GET /health`

Returns `OK` — useful for container health checks.

---

## Security

- All user text is sanitized server-side (`InputSanitizer.scala`):
  - HTML tags and entities stripped
  - Only whitelisted characters allowed in ingredient names
  - Dietary restrictions validated against a fixed allowlist
- HTTP security headers applied on every response (`X-Content-Type-Options`, `X-Frame-Options`, `X-XSS-Protection`, `Referrer-Policy`, `Cache-Control: no-store`).
- API key never exposed to the browser.

---

## Configuration

`src/main/resources/application.conf`

| Key | Default | Env override |
|---|---|---|
| `server.host` | `localhost` | `SERVER_HOST` |
| `server.port` | `8080` | `SERVER_PORT` |
| `openai.api-key` | *(required)* | `OPENAI_API_KEY` |
| `openai.model` | `gpt-4o-mini` | — |
| `openai.timeout-seconds` | `45` | — |

