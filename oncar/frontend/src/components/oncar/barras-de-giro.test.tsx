import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BarrasDeGiro } from "@/components/oncar/barras-de-giro";
import type { ProdutoGiro } from "@/lib/tipos";

function giro(campos: Partial<ProdutoGiro>): ProdutoGiro {
  return {
    produtoId: 1,
    sku: "FL-001",
    descricao: "Filtro de oleo",
    unidade: "UN",
    quantidade: "10.000",
    saidas: 1,
    receita: null,
    ...campos,
  };
}

/** Largura da barra desenhada (style inline), em numero. */
const larguraDa = (link: HTMLElement) =>
  parseFloat((link.querySelector("[aria-hidden='true']") as HTMLElement).style.width);

describe("<BarrasDeGiro>", () => {
  it("cada peca leva ao proprio extrato", () => {
    render(<BarrasDeGiro produtos={[giro({ produtoId: 42 })]} />);

    expect(screen.getByRole("link")).toHaveAttribute("href", "/produtos/42");
  });

  it("concorda saida/saidas com a quantidade de lancamentos", () => {
    render(<BarrasDeGiro produtos={[giro({ produtoId: 1, saidas: 1 }), giro({ produtoId: 2, saidas: 3 })]} />);

    const [uma, varias] = screen.getAllByRole("link");
    expect(uma).toHaveTextContent("1 saída");
    expect(varias).toHaveTextContent("3 saídas");
  });

  it("a receita so aparece para quem pode ver o financeiro (a API manda null para os demais)", () => {
    render(<BarrasDeGiro produtos={[giro({ produtoId: 1, receita: "250.00" }), giro({ produtoId: 2, receita: null })]} />);

    const [comReceita, semReceita] = screen.getAllByRole("link");
    expect(comReceita.textContent?.replace(/ /g, " ")).toContain("R$ 250,00");
    expect(semReceita).not.toHaveTextContent("R$");
  });

  it("a maior barra ocupa 55% da linha e as outras sao proporcionais, com minimo visivel", () => {
    render(
      <BarrasDeGiro
        produtos={[
          giro({ produtoId: 1, quantidade: "10" }),
          giro({ produtoId: 2, quantidade: "5" }),
          giro({ produtoId: 3, quantidade: "0.01" }),
        ]}
      />,
    );

    const larguras = screen.getAllByRole("link").map(larguraDa);
    expect(larguras[0]).toBeCloseTo(55);
    expect(larguras[1]).toBeCloseTo(27.5);
    expect(larguras[2]).toBeCloseTo(2 * 0.55, 5);
  });

  it("sem nenhuma saida as barras ficam zeradas, sem dividir por zero", () => {
    render(<BarrasDeGiro produtos={[giro({ quantidade: "0" })]} />);

    expect(larguraDa(screen.getByRole("link"))).toBe(0);
  });
});
