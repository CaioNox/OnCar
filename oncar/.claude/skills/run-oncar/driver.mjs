// Driver do OnCar: faz login pelo formulario e tira prints das telas.
// Uso: node driver.mjs [rota ...]   (ex.: painel produtos movimentacoes; padrao: painel)
// Env: ONCAR_URL (http://localhost:3000), ONCAR_EMAIL, ONCAR_SENHA, ONCAR_OUT (./shots)
import { createRequire } from "node:module";
// playwright-core vem do node_modules do frontend (npm i --no-save playwright-core).
const { chromium } = createRequire(new URL("../../../frontend/package.json", import.meta.url))("playwright-core");
import { mkdirSync } from "node:fs";

const base = process.env.ONCAR_URL ?? "http://localhost:3000";
const email = process.env.ONCAR_EMAIL ?? "proprietario@oncar.com.br";
const senha = process.env.ONCAR_SENHA ?? "oncar2026";
const out = process.env.ONCAR_OUT ?? "shots";
// Rotas sem barra inicial ("painel"): o Git Bash reescreve "/painel" para "C:/Program Files/Git/painel".
const rotas = (process.argv.slice(2).length ? process.argv.slice(2) : ["painel"]).map((r) => "/" + r.replace(/^.*\/Git\//, "").replace(/^\//, ""));
mkdirSync(out, { recursive: true });

const browser = await chromium.launch({ channel: "chrome", headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const erros = [];
// ONCAR_API=http://localhost:8081 manda o /api para outro backend sem reiniciar o next dev
// (o Next 16 recusa um segundo `next dev` na mesma pasta).
if (process.env.ONCAR_API) {
  await page.route("**/api/**", (rota) => {
    const url = new URL(rota.request().url());
    return rota.fetch({ url: process.env.ONCAR_API + url.pathname + url.search }).then((r) => rota.fulfill({ response: r }));
  });
}
page.on("console", (m) => m.type() === "error" && erros.push(m.text()));
page.on("pageerror", (e) => erros.push(String(e)));

await page.goto(`${base}/login`);
await page.locator('input[type="email"]').fill(email);
await page.locator('input[type="password"]').fill(senha);
await page.locator('button[type="submit"]').click();
await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 20000 });

for (const rota of rotas) {
  await page.goto(`${base}${rota}`);
  await page.waitForLoadState("networkidle");
  await page.waitForFunction(() => !document.querySelector('[aria-busy="true"]'), null, { timeout: 20000 });
  const arquivo = `${out}/${rota.replace(/\W+/g, "_") || "root"}.png`;
  await page.screenshot({ path: arquivo, fullPage: true });
  console.log(`ok ${rota} -> ${arquivo} | h1: ${await page.locator("h1").first().textContent().catch(() => "?")}`);
}
if (erros.length) console.log("console errors:\n" + erros.join("\n"));
await browser.close();
