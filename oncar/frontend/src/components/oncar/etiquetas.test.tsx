import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { NivelDeEstoque, nivelDoEstoque, NumeroEmReais, TipoDeMovimentacao } from "@/components/oncar/etiquetas";

describe("nivelDoEstoque", () => {
  const nivel = (saldo: string, estoqueMinimo: string, abaixoDoMinimo = false) =>
    nivelDoEstoque({ saldo, estoqueMinimo, abaixoDoMinimo });

  it("o servidor decide o critico: abaixoDoMinimo vence qualquer conta local", () => {
    expect(nivel("100", "1", true)).toBe("critico");
  });

  it("ate 50% acima do minimo e atencao; o limite exato de 1,5x ainda e atencao", () => {
    expect(nivel("11", "10")).toBe("atencao");
    expect(nivel("15", "10")).toBe("atencao");
    expect(nivel("15.001", "10")).toBe("ok");
  });

  it("sem estoque minimo definido nunca e atencao", () => {
    expect(nivel("0", "0")).toBe("ok");
  });
});

describe("<NivelDeEstoque>", () => {
  it.each([
    ["ok", "Em dia"],
    ["atencao", "Perto do mínimo"],
    ["critico", "Repor"],
  ] as const)("o nivel %s aparece como texto (%s), e nao so pela cor", (nivel, rotulo) => {
    render(<NivelDeEstoque nivel={nivel} />);

    expect(screen.getByText(rotulo)).toBeInTheDocument();
  });
});

describe("<TipoDeMovimentacao>", () => {
  it("usa a descricao que a API manda", () => {
    render(<TipoDeMovimentacao movimentacao={{ tipo: "ESTORNO", tipoDescricao: "Estorno" }} />);

    expect(screen.getByText("Estorno")).toBeInTheDocument();
  });
});

describe("<NumeroEmReais>", () => {
  /** Junta o texto das tres partes; o Intl usa espaco nao separavel. */
  const texto = (elemento: HTMLElement) => elemento.textContent?.replace(/ /g, " ");

  it("separa simbolo, inteiro e centavos para o estilo de KPI", () => {
    const { container } = render(<NumeroEmReais valor={1234.5} />);
    const [simbolo, valor] = container.firstElementChild!.children;

    expect(simbolo).toHaveTextContent("R$");
    expect(valor.childNodes[0].textContent).toBe("1.234");
    expect(valor.querySelector("span")).toHaveTextContent(",50");
  });

  it("valor negativo mantem o sinal no inteiro", () => {
    const { container } = render(<NumeroEmReais valor={-80} />);

    expect(texto(container.firstElementChild as HTMLElement)).toBe("R$-80,00");
  });
});
