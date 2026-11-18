import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CartaoPeca } from "@/components/oncar/cartao-peca";
import { produto } from "@/lib/fixtures.test-utils";

const regua = () => screen.getByRole("img", { name: /^Saldo/ });
const preenchimento = () => parseFloat((regua().firstElementChild as HTMLElement).style.width);

describe("<CartaoPeca>", () => {
  it("o cartao inteiro e um link para o extrato da peca", () => {
    render(<CartaoPeca produto={produto({ id: 9, descricao: "Pastilha" })} exibeCusto={false} ordem={0} />);

    expect(screen.getByRole("link")).toHaveAttribute("href", "/produtos/9");
    expect(screen.getByRole("heading", { name: "Pastilha" })).toBeInTheDocument();
  });

  it("o custo medio so aparece para quem pode ver custos", () => {
    const { rerender } = render(
      <CartaoPeca produto={produto({ precoCusto: "20.0000" })} exibeCusto={false} ordem={0} />,
    );
    expect(screen.queryByText(/^custo/)).not.toBeInTheDocument();

    rerender(<CartaoPeca produto={produto({ precoCusto: "20.0000" })} exibeCusto ordem={0} />);
    expect(screen.getByText(/^custo/)).toBeInTheDocument();
  });

  it("perfil com permissao, mas sem custo enviado pela API, nao mostra um custo vazio", () => {
    render(<CartaoPeca produto={produto({ precoCusto: null })} exibeCusto ordem={0} />);

    expect(screen.queryByText(/^custo/)).not.toBeInTheDocument();
  });

  it("produto inativo mostra a etiqueta Inativo no lugar do nivel", () => {
    render(<CartaoPeca produto={produto({ ativo: false, abaixoDoMinimo: true })} exibeCusto={false} ordem={0} />);

    expect(screen.getByText("Inativo")).toBeInTheDocument();
    expect(screen.queryByText("Repor")).not.toBeInTheDocument();
  });

  it("a regua descreve saldo e minimo em texto, para quem nao enxerga a barra", () => {
    render(<CartaoPeca produto={produto({ saldo: "3.000", estoqueMinimo: "4.000" })} exibeCusto={false} ordem={0} />);

    expect(regua()).toHaveAccessibleName("Saldo 3 de mínimo 4");
  });

  it.each([
    ["metade do minimo enche um quarto", "2", "4", 25],
    ["no minimo enche metade", "4", "4", 50],
    ["o dobro do minimo ou mais enche tudo", "20", "4", 100],
    ["sem minimo e com saldo enche tudo", "1", "0", 100],
    ["sem minimo e sem saldo fica vazia", "0", "0", 0],
  ])("regua: %s", (_caso, saldo, estoqueMinimo, esperado) => {
    render(<CartaoPeca produto={produto({ saldo, estoqueMinimo })} exibeCusto={false} ordem={0} />);

    expect(preenchimento()).toBeCloseTo(esperado);
  });

  it("a entrada em cascata para de crescer depois do 12o cartao", () => {
    const { container, rerender } = render(<CartaoPeca produto={produto()} exibeCusto={false} ordem={3} />);
    expect((container.firstElementChild as HTMLElement).style.animationDelay).toBe("90ms");

    rerender(<CartaoPeca produto={produto()} exibeCusto={false} ordem={40} />);
    expect((container.firstElementChild as HTMLElement).style.animationDelay).toBe("360ms");
  });
});
