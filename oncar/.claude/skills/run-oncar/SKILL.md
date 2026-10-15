---
name: run-oncar
description: Run, start, build, test, log in to, and screenshot OnCar (Spring Boot backend + Next.js frontend). Use when asked to run the app, check that a screen like painel/produtos/movimentacoes works, or take a screenshot.
---

# Run OnCar

OnCar has two parts. `backend/` is Spring Boot on :8080 with H2 in memory and demo data. `frontend/` is Next.js 16 on :3000, and it proxies `/api/*` to the backend. You drive the app with `driver.mjs`, a headless Playwright script. It logs in through the real form, opens the routes you give it, saves full-page PNGs and prints any console errors.
Paths below are relative to `oncar/`. Tested on Windows 11 with Git Bash.

## Prerequisites

- JDK (26 was used) and Node 24. Maven comes through `backend/mvnw`.
- Google Chrome installed. The driver uses `channel: "chrome"`, so it doesn't need a Playwright browser download.
- `playwright-core` in the frontend (not saved to `package.json`):

```bash
cd frontend && npm i --no-save playwright-core
```

## Start

```bash
cd backend && ./mvnw -q spring-boot:run > "$TEMP/oncar-be.log" 2>&1 &
timeout 180 bash -c 'until curl -s localhost:8080/api/sessao -o /dev/null; do sleep 2; done'
cd frontend && npx next dev > "$TEMP/oncar-fe.log" 2>&1 &
timeout 120 bash -c 'until curl -sf localhost:3000/login -o /dev/null; do sleep 2; done'
```

Demo logins all use the password `oncar2026`: `proprietario@`, `gerente@`, `atendente@` and `mecanico@oncar.com.br`. Use `atendente` to check that costs are hidden for that role.

## Drive (agent path)

```bash
ONCAR_OUT="$TEMP/oncar-shots" node .claude/skills/run-oncar/driver.mjs painel produtos movimentacoes
```

- Pass routes **without the leading slash**, as shown above. Git Bash rewrites `/painel` into `C:/Program Files/Git/painel`.
- Output: `ok /painel -> <png> | h1: Boa noite, Caio.`, then any console errors. **Open the PNG.** If the h1 is `?`, the page didn't render normally (error screen or skeleton).
- Env vars: `ONCAR_URL` (default `http://localhost:3000`), `ONCAR_EMAIL`/`ONCAR_SENHA`, `ONCAR_OUT` (default `./shots`), `ONCAR_API` (see below).
- One `401` in the console is normal. It's the `GET /api/sessao` from before login.

### Testing a backend change without restarting the user's :8080

Start a second backend on another port, then point the driver's `/api` at it with `ONCAR_API`:

```bash
cd backend && ./mvnw -q spring-boot:run -Dspring-boot.run.arguments=--server.port=8081 > "$TEMP/oncar-be.log" 2>&1 &
ONCAR_API=http://localhost:8081 ONCAR_OUT="$TEMP/oncar-shots" node .claude/skills/run-oncar/driver.mjs painel
```

## API with curl

```bash
J=/tmp/oc.jar; rm -f $J
curl -s -c $J -b $J localhost:3000/api/sessao -o /dev/null            # issues XSRF-TOKEN
T=$(grep XSRF $J | awk '{print $7}')
curl -s -c $J -b $J -H "X-XSRF-TOKEN: $T" -H "Content-Type: application/json" \
  -d '{"email":"proprietario@oncar.com.br","senha":"oncar2026"}' localhost:3000/api/sessao
curl -s -b $J "localhost:3000/api/produtos?somenteAtivos=false" -w " %{http_code}\n"
```

The painel loads `/api/painel`, `/api/movimentacoes` and `/api/produtos?somenteAtivos=false`. If **any one** of them fails, the whole screen shows "Não deu para abrir o painel".

## Test

```bash
cd backend && ./mvnw -q test
cd frontend && npx tsc --noEmit && npx eslint src
```

## Stop

Stop it by PID. Avoid a broad `pkill`.
```bash
netstat -ano | grep ":8081 .*LISTEN" | awk '{print $5}' | head -1 | xargs -I{} taskkill //PID {} //F //T
```

## Gotchas

- **The Next 16 dev server runs one instance per project folder.** A second `next dev -p 3001` exits with "You can access the existing server at http://localhost:3000". Use `ONCAR_API` instead.
- **`spring.jpa.open-in-view=false`.** If a query returns `Produto` without `join fetch p.fornecedorPadrao`, the DTO conversion throws `LazyInitializationException` and the endpoint returns a bare 500. The UI only shows "Nao foi possivel concluir a operacao". The real cause is in `$TEMP/oncar-be.log`, which only exists for a backend you started yourself.
- `/painel` without `/api` returns 302: that's the backend's old Thymeleaf UI, not the REST API.
- `playwright-core` is loaded with `createRequire` from `frontend/package.json`, because ESM ignores `NODE_PATH`.
- H2 runs in memory, so data resets every time the backend restarts.

## Troubleshooting

- `ERR_MODULE_NOT_FOUND playwright-core` → run `cd frontend && npm i --no-save playwright-core`.
- `navigating to "http://localhost:3000C:/Program Files/Git/painel"` → pass the route without the `/`.
- Painel shows "Não deu para abrir o painel" → curl the three endpoints above, find the one returning 500, and read the backend log.
