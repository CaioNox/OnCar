import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

/**
 * Testes unitarios do front-end (Vitest + Testing Library).
 *
 * - O alias "@/..." espelha o "paths" do tsconfig.json.
 * - O ambiente padrao e o jsdom (componentes). Os testes de src/lib que nao tocam no
 *   DOM declaram `// @vitest-environment node` no topo do arquivo.
 * - restoreMocks/unstubGlobals desfazem espioes e globais entre um teste e outro,
 *   para nenhum teste depender da ordem de execucao.
 */
export default defineConfig({
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./vitest.setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    restoreMocks: true,
    unstubGlobals: true,
    css: false,
  },
});
