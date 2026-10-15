import path from "path";
import type { NextConfig } from "next";

// O back-end do OnCar responde em /api. Em desenvolvimento o Next encaminha essas
// chamadas para o Spring Boot, de modo que o navegador fale sempre com a mesma
// origem - assim o cookie de sessao e o token CSRF funcionam sem configuracao extra.
const backend = process.env.ONCAR_BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // Fixa a raiz do Turbopack no front-end, ignorando lockfiles fora do projeto.
  turbopack: { root: path.resolve(__dirname) },
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
