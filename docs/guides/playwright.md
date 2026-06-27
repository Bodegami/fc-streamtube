# Playwright CLI Guide

This guide covers how to use `playwright-cli` for browser automation, UI inspection, frontend development, and E2E testing in this project.

---

## Installation

`playwright-cli` is installed globally via npm (managed by NVM):

- **Binary:** `/Users/devbodegami/.nvm/versions/node/v24.18.0/bin/playwright-cli`
- **Package:** `@playwright/cli` v0.1.14 (`github.com/microsoft/playwright-cli`)
- **Command:** `playwright-cli` (available in PATH when NVM node is active)

If the command is not found, ensure NVM is loaded:

```bash
nvm use   # activates the correct Node version
playwright-cli --version
```

---

## When to Use playwright-cli vs Figma MCP

| Scenario | Use |
|---|---|
| Inspect or screenshot a Figma design | **Figma MCP** (preferred — API-based, no browser needed) |
| Figma MCP is rate-limited for today | **playwright-cli** + Chrome CDP (see below) |
| E2E tests on the frontend | **playwright-cli** |
| UI review / design feedback loop | **playwright-cli** `show --annotate` |
| Frontend debugging (console, requests) | **playwright-cli** `console` / `requests` |

---

## Quick Start

```bash
# Open a new headless browser and navigate
playwright-cli open https://localhost:3000

# Take a snapshot of the page (accessibility tree with refs)
playwright-cli snapshot

# Interact using refs from the snapshot
playwright-cli click e5
playwright-cli fill e3 "test@example.com" --submit

# Take a screenshot
playwright-cli screenshot --filename=page.png

# Close the browser
playwright-cli close
```

---

## Attaching to a Real Chrome Browser (Required for Authenticated Sites)

Headless browsers have no session data. Sites like Figma detect them via CloudFront and return **403**. The solution is to attach playwright-cli to a real Chrome instance with your cookies.

### Why you cannot attach to an already-running Chrome

Chrome only exposes its DevTools Protocol (CDP) if it was **started** with `--remote-debugging-port`. Adding the flag to a running process has no effect.

### Solution: Open a second Chrome instance (does NOT close the existing one)

This opens a fresh Chrome on a separate user-data directory alongside your current Chrome:

```bash
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome \
  --remote-debugging-port=9222 \
  --user-data-dir=/tmp/chrome-debug-profile &
```

Then attach playwright-cli to it:

```bash
playwright-cli attach --cdp=http://localhost:9222
```

Verify it worked — you should see `Session created, attached to http://localhost:9222`.

> **Note:** this new Chrome instance starts without your existing session cookies. If the target site requires login, log in manually in that window first, or use the Figma MCP instead.

### Verify the port is open before attaching

```bash
curl -s http://localhost:9222/json/version | head -3
```

If it returns JSON with `"Browser": "Chrome/..."`, the port is active.

---

## Accessing Figma Designs via playwright-cli

When the **Figma MCP is rate-limited**, use this sequence:

```bash
# 1. Start a second Chrome with debugging (does not close your existing Chrome)
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome \
  --remote-debugging-port=9222 \
  --user-data-dir=/tmp/chrome-debug-profile &

# 2. Wait for it to start, then verify
curl -s http://localhost:9222/json/version

# 3. Attach playwright-cli
playwright-cli attach --cdp=http://localhost:9222

# 4. Navigate to the Figma file (use & to escape on some shells)
playwright-cli goto "https://www.figma.com/design/<fileKey>/<fileName>?node-id=<nodeId>"

# 5. Take a screenshot or snapshot
playwright-cli screenshot --filename=figma-design.png
playwright-cli snapshot
```

> Figma loads in viewer mode (no login). You can still see the canvas and all frames.

---

## E2E Testing

Before writing any E2E test, read `./docs/guides/testing-strategy.md`.

E2E tests go in `frontend/tests/e2e/` (or `backend/tests/e2e/` for API flows). Use the Claude Code skill `/e2e-nav-test` to auto-generate test cases from the running app.

### Common E2E patterns

```bash
# Open app, interact, assert
playwright-cli open https://localhost:3000
playwright-cli snapshot
playwright-cli click e12
playwright-cli fill e5 "user@example.com" --submit
playwright-cli snapshot

# UI review with annotations (user marks elements and adds comments)
playwright-cli open https://localhost:3000
playwright-cli show --annotate
```

---

## Useful Commands Reference

```bash
# Navigation
playwright-cli goto <url>
playwright-cli go-back
playwright-cli reload

# Inspection
playwright-cli snapshot               # full page accessibility tree
playwright-cli snapshot e34           # partial snapshot of element
playwright-cli screenshot
playwright-cli screenshot e5          # screenshot of specific element
playwright-cli console                # show console errors/warnings
playwright-cli requests               # show network requests

# Interaction
playwright-cli click e5
playwright-cli fill e3 "value" --submit
playwright-cli select e9 "option-value"
playwright-cli press Enter
playwright-cli hover e4

# Sessions
playwright-cli list                   # list all open sessions
playwright-cli close                  # close default session
playwright-cli close-all              # close all browsers
playwright-cli -s=mysession open <url>  # named session

# Storage
playwright-cli cookie-list
playwright-cli localstorage-get <key>
playwright-cli state-save auth.json   # save session cookies
playwright-cli state-load auth.json   # restore session cookies
```

---

## Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| `403 CloudFront error` on Figma | Headless browser has no session | Use CDP attach to real Chrome (see above) |
| `DevToolsActivePort file not found` | Chrome not started with `--remote-debugging-port` | Open a new Chrome instance with the flag |
| `ECONNREFUSED localhost:9222` | Chrome not running yet or flag not set | Wait a few seconds after starting Chrome, then retry |
| `playwright-cli: command not found` | NVM node not activated | Run `nvm use` first |
