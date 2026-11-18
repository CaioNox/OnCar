import path from "path";
import type { NextConfig } from "next";

// O back-end do OnCar responde em /api. O Next encaminha essas chamadas para o
// Spring Boot, de modo que o navegador fale sempre com a mesma origem - assim o
// cookie de sessao e o token CSRF funcionam sem configuracao extra.
// Os rewrites sao resolvidos no `next build`: mudar ONCAR_BACKEND_URL exige novo build.
const backend = process.env.ONCAR_BACKEND_URL ?? "http://127.0.0.1:8080";

const nextConfig: NextConfig = {
  // Fixa a raiz do Turbopack no front-end, ignorando lockfiles fora do projeto.
  turbopack: { root: path.resolve(__dirname) },
  // A imagem Docker roda o servidor enxuto (.next/standalone); fora dela, `next start` continua igual.
  output: process.env.ONCAR_STANDALONE === "1" ? "standalone" : undefined,
  rewrites() {
    return [
      {
        source: "/api/:caminho*",
        destination: `${backend}/api/:caminho*`,
      },
    ];
  },
};

export default nextConfig;
