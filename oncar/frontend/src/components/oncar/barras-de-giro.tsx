import Link from "next/link";
import { formatarMoeda, formatarQuantidade } from "@/lib/api";
import type { ProdutoGiro } from "@/lib/tipos";

/**
 * Barras horizontais das pecas que mais sairam no mes. Uma serie so, entao sem
 * legenda: o titulo do cartao diz o que e medido e o valor vai na ponta da barra.
 */
export function BarrasDeGiro({ produtos }: { produtos: ProdutoGiro[] }) {
  const maior = Math.max(...produtos.map((produto) => Number(produto.quantidade)), 0);

  return (
    <ol className="mt-5 grid gap-4">
      {produtos.map((produto) => {
        const largura = maior > 0 ? Math.max((Number(produto.quantidade) / maior) * 100, 2) : 0;
        return (
          <li key={produto.produtoId}>
            <Link
              href={`/produtos/${produto.produtoId}`}
              className="group -mx-2 block rounded-lg px-2 py-1 transition-colors hover:bg-accent"
            >
              <span className="flex items-baseline gap-2 text-sm">
                <span className="num shrink-0 text-xs text-muted-foreground">{produto.sku}</span>
                <span className="truncate">{produto.descricao}</span>
              </span>
              <span className="mt-1.5 flex items-center gap-2">
                {/* a barra ocupa no maximo 55% da linha para o rotulo da ponta caber ao lado */}
                <span
                  aria-hidden="true"
                  className="block h-3 shrink-0 rounded-r-[4px] bg-grafico-receita transition-[filter] group-hover:brightness-110"
                  style={{ width: `${largura * 0.55}%` }}
                />
                <span className="num min-w-0 text-xs leading-tight text-muted-foreground">
                  <span className="text-foreground">
                    {formatarQuantidade(produto.quantidade)} {produto.unidade}
                  </span>
                  {" · "}
                  {produto.saidas} {produto.saidas === 1 ? "saída" : "saídas"}
                  {produto.receita !== null && ` · ${formatarMoeda(produto.receita)}`}
                </span>
              </span>
            </Link>
          </li>
        );
      })}
    </ol>
  );
}
