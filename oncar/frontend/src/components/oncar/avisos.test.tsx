import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Erro } from "@/components/oncar/avisos";

describe("<Erro>", () => {
  it("sem mensagem nao renderiza nada (a tela nao reserva espaco para o aviso)", () => {
    const { container } = render(<Erro mensagem={null} />);

    expect(container).toBeEmptyDOMElement();
  });

  it("mostra a mensagem num alerta, que o leitor de tela anuncia na hora", () => {
    render(<Erro mensagem="Saldo insuficiente." />);

    expect(screen.getByRole("alert")).toHaveTextContent("Saldo insuficiente.");
    expect(screen.queryByRole("list")).not.toBeInTheDocument();
  });

  it("lista os detalhes de cada campo invalido devolvidos pela API", () => {
    render(<Erro mensagem="Confira os dados." detalhes={["Informe a quantidade", "Justifique o ajuste"]} />);

    const itens = screen.getAllByRole("listitem").map((item) => item.textContent);
    expect(itens).toEqual(["Informe a quantidade", "Justifique o ajuste"]);
  });

  it("lista de detalhes vazia nao desenha uma lista vazia", () => {
    render(<Erro mensagem="Falhou." detalhes={[]} />);

    expect(screen.queryByRole("list")).not.toBeInTheDocument();
  });
});
