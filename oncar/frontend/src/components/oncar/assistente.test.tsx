import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Assistente } from "@/components/oncar/assistente";
import { oficina, renderizarNaOficina } from "@/components/oncar/oficina.test-utils";
import type { Resposta } from "@/lib/assistente";
import { produto } from "@/lib/fixtures.test-utils";

/*
 * As regras de resposta (palavras-chave, chamadas a API) ja sao testadas em
 * src/lib/assistente.test.ts. Aqui `responder` e simulado para testar so o componente:
 * conversa, estado "pensando", erros e navegacao.
 */
const { responder } = vi.hoisted(() => ({ responder: vi.fn<(texto: string, produtos: unknown[]) => Promise<Resposta>>() }));
vi.mock("@/lib/assistente", async (original) => ({
  ...(await original<typeof import("@/lib/assistente")>()),
  responder,
}));

const PRODUTOS = [produto({ id: 7, descricao: "Vela de ignicao" })];

function abrir(aoFechar = vi.fn()) {
  renderizarNaOficina(<Assistente aberto aoFechar={aoFechar} />, { dados: oficina({ produtos: PRODUTOS }) });
  return { aoFechar, usuario: userEvent.setup() };
}

const campo = () => screen.getByRole("textbox", { name: "Pergunta para o assistente" });

beforeEach(() => {
  responder.mockReset();
  responder.mockResolvedValue({ texto: "Nenhum item precisa de reposição agora." });
});

describe("<Assistente>", () => {
  it("fechado, nao monta a conversa", () => {
    renderizarNaOficina(<Assistente aberto={false} aoFechar={() => undefined} />);

    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("conversa vazia oferece as perguntas de exemplo", () => {
    abrir();

    expect(screen.getByRole("button", { name: "O que está abaixo do mínimo?" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Quanto vale o estoque?" })).toBeInTheDocument();
  });

  it("uma sugestao vira pergunta, respondida com os produtos da oficina", async () => {
    const { usuario } = abrir();

    await usuario.click(screen.getByRole("button", { name: "O que está abaixo do mínimo?" }));

    expect(responder).toHaveBeenCalledWith("O que está abaixo do mínimo?", PRODUTOS);
    expect(await screen.findByText("Nenhum item precisa de reposição agora.")).toBeInTheDocument();
    expect(screen.getByText("O que está abaixo do mínimo?", { selector: "p" })).toBeInTheDocument();
  });

  it("Enter envia a pergunta digitada e limpa o campo", async () => {
    const { usuario } = abrir();

    await usuario.type(campo(), "saldo da vela{Enter}");

    expect(responder).toHaveBeenCalledWith("saldo da vela", PRODUTOS);
    expect(campo()).toHaveValue("");
    expect(await screen.findByText("Nenhum item precisa de reposição agora.")).toBeInTheDocument();
  });

  it("enquanto pensa, mostra o aviso e bloqueia novas perguntas (sem respostas cruzadas)", async () => {
    let concluir: (resposta: Resposta) => void = () => undefined;
    responder.mockReturnValue(new Promise((resolver) => (concluir = resolver)));
    const { usuario } = abrir();

    await usuario.type(campo(), "valor do estoque{Enter}");

    expect(screen.getByText("Pensando…")).toBeInTheDocument();
    expect(campo()).toBeDisabled();

    concluir({ texto: "O estoque vale R$ 10,00." });
    expect(await screen.findByText("O estoque vale R$ 10,00.")).toBeInTheDocument();
    expect(campo()).toBeEnabled();
    expect(screen.queryByText("Pensando…")).not.toBeInTheDocument();
  });

  it("falha ao consultar vira uma resposta educada, e nao uma tela quebrada", async () => {
    responder.mockRejectedValue(new Error("rede fora"));
    const { usuario } = abrir();

    await usuario.type(campo(), "ultimas saidas{Enter}");

    expect(await screen.findByText(/Não consegui consultar o estoque agora/)).toBeInTheDocument();
  });

  it("itens com link levam a tela do produto e fecham o painel", async () => {
    responder.mockResolvedValue({
      texto: "Achei este produto:",
      itens: [{ chave: "produto-7", titulo: "VL-1 · Vela de ignicao", detalhe: "saldo 3", href: "/produtos/7", alerta: true }],
    });
    const { usuario, aoFechar } = abrir();
    await usuario.type(campo(), "vela{Enter}");

    const link = await screen.findByRole("link", { name: /Vela de ignicao/ });
    expect(link).toHaveAttribute("href", "/produtos/7");
    expect(within(link).getByLabelText("abaixo do mínimo")).toBeInTheDocument();

    // Fora do App Router o next/link cai na navegacao nativa, que o jsdom nao implementa.
    // O clique continua chegando ao onClick do Link; so a troca de documento e barrada.
    const barrarNavegacao = (evento: Event) => evento.preventDefault();
    document.addEventListener("click", barrarNavegacao);
    await usuario.click(link);
    document.removeEventListener("click", barrarNavegacao);
    expect(aoFechar).toHaveBeenCalled();
  });

  it("resposta sem entendimento devolve as sugestoes para o usuario tentar de novo", async () => {
    responder.mockResolvedValue({ texto: "Ainda não sei responder isso.", sugestoes: ["Últimas saídas"] });
    const { usuario } = abrir();

    await usuario.type(campo(), "bom dia{Enter}");

    expect(await screen.findByText("Ainda não sei responder isso.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Últimas saídas" })).toBeEnabled();
  });

  it("o botao Fechar assistente avisa quem abriu", async () => {
    const { usuario, aoFechar } = abrir();

    await usuario.click(screen.getByRole("button", { name: "Fechar assistente" }));

    expect(aoFechar).toHaveBeenCalledTimes(1);
  });
});
