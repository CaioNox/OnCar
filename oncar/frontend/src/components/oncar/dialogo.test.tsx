import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Dialogo } from "@/components/oncar/dialogo";

/** O <dialog> fechado fica fora da arvore acessivel; hidden:true o encontra mesmo assim. */
const dialogo = () => screen.getByRole("dialog", { hidden: true }) as HTMLDialogElement;

describe("<Dialogo>", () => {
  it("fechado, nao monta o conteudo (cada abertura comeca do zero)", () => {
    render(
      <Dialogo aberto={false} aoFechar={() => undefined} rotulo="Busca global">
        <input aria-label="Buscar" />
      </Dialogo>,
    );

    expect(dialogo()).not.toHaveAttribute("open");
    expect(screen.queryByLabelText("Buscar")).not.toBeInTheDocument();
  });

  it("aberto, abre como modal com o rotulo acessivel e mostra o conteudo", () => {
    render(
      <Dialogo aberto aoFechar={() => undefined} rotulo="Busca global">
        <input aria-label="Buscar" />
      </Dialogo>,
    );

    expect(screen.getByRole("dialog", { name: "Busca global" })).toHaveAttribute("open");
    expect(screen.getByLabelText("Buscar")).toBeInTheDocument();
  });

  it("acompanha a prop: aberto -> fechado fecha o dialogo e desmonta o conteudo", () => {
    const { rerender } = render(
      <Dialogo aberto aoFechar={() => undefined} rotulo="Painel">
        <p>conteudo</p>
      </Dialogo>,
    );

    rerender(
      <Dialogo aberto={false} aoFechar={() => undefined} rotulo="Painel">
        <p>conteudo</p>
      </Dialogo>,
    );

    expect(dialogo()).not.toHaveAttribute("open");
    expect(screen.queryByText("conteudo")).not.toBeInTheDocument();
  });

  it("clique no fundo escurecido (o proprio <dialog>) fecha", async () => {
    const aoFechar = vi.fn();
    render(
      <Dialogo aberto aoFechar={aoFechar} rotulo="Painel">
        <p>conteudo</p>
      </Dialogo>,
    );

    await userEvent.click(dialogo());

    expect(aoFechar).toHaveBeenCalledTimes(1);
  });

  it("clique dentro do painel nao fecha", async () => {
    const aoFechar = vi.fn();
    render(
      <Dialogo aberto aoFechar={aoFechar} rotulo="Painel">
        <button type="button">Dentro</button>
      </Dialogo>,
    );

    await userEvent.click(screen.getByRole("button", { name: "Dentro" }));

    expect(aoFechar).not.toHaveBeenCalled();
  });

  it("o evento close do navegador (Esc) avisa quem abriu, para o estado nao ficar dessincronizado", () => {
    const aoFechar = vi.fn();
    render(
      <Dialogo aberto aoFechar={aoFechar} rotulo="Painel">
        <p>conteudo</p>
      </Dialogo>,
    );

    fireEvent(dialogo(), new Event("close"));

    expect(aoFechar).toHaveBeenCalledTimes(1);
  });
});
