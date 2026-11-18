import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { oficina, usuario } from "@/components/oncar/oficina.test-utils";
import { movimentacao, produto } from "@/lib/fixtures.test-utils";
import type { Cliente, Perfil } from "@/lib/tipos";

/*
 * useRouter so funciona dentro do App Router do Next; nos testes ele e substituido
 * por um objeto que so registra as navegacoes pedidas.
 */
const { roteador } = vi.hoisted(() => ({ roteador: { push: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => roteador }));

const CLIENTES: Cliente[] = [{ id: 31, nome: "Joana Prado", documento: "123.456.789-00", ativo: true }];

let fetchDeClientes: ReturnType<typeof vi.fn<typeof fetch>>;

/**
 * A paleta guarda os clientes num cache de modulo ("vale para a sessao inteira").
 * Para cada teste comecar sem cache, os modulos sao reimportados do zero. O helper de
 * renderizacao tambem e reimportado: assim ele e a paleta usam o MESMO contexto React
 * (um contexto de outra copia do modulo seria invisivel para o componente).
 */
async function abrirPaleta(perfil: Perfil = "PROPRIETARIO", dados = oficina()) {
  vi.resetModules();
  const { PaletaDeComandos } = await import("@/components/oncar/paleta-de-comandos");
  const { renderizarNaOficina: renderizar } = await import("@/components/oncar/oficina.test-utils");
  const aoFechar = vi.fn();
  renderizar(<PaletaDeComandos aberta aoFechar={aoFechar} />, { sessao: usuario(perfil), dados });
  const reabrir = () => renderizar(<PaletaDeComandos aberta aoFechar={() => undefined} />, { dados });
  return { aoFechar, reabrir, digitar: userEvent.setup() };
}

const busca = () => screen.getByRole("combobox", { name: "Buscar peça, cliente, placa ou OS" });
const opcoes = () => screen.queryAllByRole("option").map((opcao) => opcao.textContent ?? "");

beforeEach(() => {
  roteador.push.mockReset();
  fetchDeClientes = vi.fn<typeof fetch>(async () => new Response(JSON.stringify(CLIENTES), { status: 200 }));
  vi.spyOn(globalThis, "fetch").mockImplementation(fetchDeClientes);
});

describe("<PaletaDeComandos> atalhos por perfil", () => {
  it("o proprietario ve todos os atalhos", async () => {
    await abrirPaleta("PROPRIETARIO");

    const atalhos = opcoes().join(" | ");
    for (const rotulo of ["Registrar entrada", "Registrar saída", "Ajuste de inventário", "Lançar despesa", "Relatórios do mês"]) {
      expect(atalhos).toContain(rotulo);
    }
  });

  it("o atendente registra saida, mas nao entrada, ajuste, despesa nem relatorio", async () => {
    await abrirPaleta("ATENDENTE");

    const atalhos = opcoes().join(" | ");
    expect(atalhos).toContain("Registrar saída");
    for (const rotulo of ["Registrar entrada", "Ajuste de inventário", "Lançar despesa", "Relatórios do mês"]) {
      expect(atalhos).not.toContain(rotulo);
    }
  });

  it("o mecanico so consulta: nenhum atalho de lancamento", async () => {
    await abrirPaleta("MECANICO");

    expect(opcoes().join(" | ")).not.toMatch(/Registrar|Ajuste|Lançar|Relatórios/);
    expect(opcoes().join(" | ")).toContain("Produtos");
  });

  it("o gerente ve relatorios operacionais, mas nao lanca despesa (financeiro e do proprietario)", async () => {
    await abrirPaleta("GERENTE_ESTOQUE");

    expect(opcoes().join(" | ")).toContain("Relatórios do mês");
    expect(opcoes().join(" | ")).not.toContain("Lançar despesa");
  });
});

describe("<PaletaDeComandos> busca", () => {
  const PECAS = [
    produto({ id: 1, sku: "FL-0010", descricao: "Filtro de ar" }),
    produto({ id: 2, sku: "FL-001", descricao: "Filtro de oleo", abaixoDoMinimo: true }),
    produto({ id: 3, sku: "FL-002", descricao: "Filtro antigo", ativo: false }),
  ];

  it("o SKU exato vem primeiro e as pecas mostram repor/inativo", async () => {
    const { digitar } = await abrirPaleta("PROPRIETARIO", oficina({ produtos: PECAS }));

    await digitar.type(busca(), "fl-001");

    const pecas = within(screen.getByRole("group", { name: "Peças" })).getAllByRole("option");
    expect(pecas[0]).toHaveTextContent("Filtro de oleo");
    expect(pecas[0]).toHaveTextContent("repor");
    expect(pecas[1]).toHaveTextContent("Filtro de ar");
  });

  it("escolher uma peca fecha a busca e abre o produto", async () => {
    const { digitar, aoFechar } = await abrirPaleta("PROPRIETARIO", oficina({ produtos: PECAS }));
    await digitar.type(busca(), "antigo");

    await digitar.click(screen.getByRole("option", { name: /Filtro antigo/ }));

    expect(screen.getByRole("option", { name: /Filtro antigo/ })).toHaveTextContent("inativo");
    expect(aoFechar).toHaveBeenCalled();
    expect(roteador.push).toHaveBeenCalledWith("/produtos/3");
  });

  it("Enter escolhe o primeiro resultado", async () => {
    const { digitar } = await abrirPaleta("PROPRIETARIO", oficina({ produtos: PECAS }));

    await digitar.type(busca(), "filtro de ar{Enter}");

    expect(roteador.push).toHaveBeenCalledWith("/produtos/1");
  });

  it("clientes vem da API uma vez por sessao e levam a uma saida para o cliente", async () => {
    const { digitar, reabrir } = await abrirPaleta();
    await waitFor(() => expect(fetchDeClientes).toHaveBeenCalledTimes(1));
    expect(String(fetchDeClientes.mock.calls[0][0])).toBe("/api/clientes");

    await digitar.type(busca(), "joana");
    await digitar.click(screen.getByRole("option", { name: /Joana Prado/ }));
    expect(roteador.push).toHaveBeenCalledWith("/movimentacoes?aba=saida&cliente=31");

    // reabrir na mesma sessao usa o cache, sem nova chamada
    reabrir();
    expect(fetchDeClientes).toHaveBeenCalledTimes(1);
  });

  it("falha ao buscar clientes nao impede a busca de pecas", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("rede fora"));
    const { digitar } = await abrirPaleta("PROPRIETARIO", oficina({ produtos: PECAS }));

    await digitar.type(busca(), "filtro de ar");

    expect(screen.getByRole("option", { name: /Filtro de ar/ })).toBeInTheDocument();
  });

  it("documentos repetidos viram uma opcao so, com a contagem e o link codificado", async () => {
    const dados = oficina({
      movimentacoes: [
        movimentacao({ id: 1, documento: "OS 4512" }),
        movimentacao({ id: 2, documento: " OS 4512 " }),
        movimentacao({ id: 3, documento: "NF-e 9" }),
      ],
    });
    const { digitar } = await abrirPaleta("PROPRIETARIO", dados);

    await digitar.type(busca(), "4512");
    const documento = screen.getByRole("option", { name: /OS 4512/ });
    expect(documento).toHaveTextContent("2 lançamento(s) recentes");

    await digitar.click(documento);
    expect(roteador.push).toHaveBeenCalledWith("/movimentacoes?documento=OS%204512");
  });

  it("placa (antiga ou Mercosul) aparece desabilitada ate a consulta de veiculos existir", async () => {
    const { digitar, aoFechar } = await abrirPaleta();

    await digitar.type(busca(), "abc1d23");
    const placa = screen.getByRole("option", { name: /Placa ABC1D23/ });
    expect(placa).toHaveAttribute("aria-disabled", "true");

    await digitar.click(placa);
    expect(aoFechar).not.toHaveBeenCalled();
  });

  it("sem resultados mostra a mensagem com o termo buscado", async () => {
    const { digitar } = await abrirPaleta();

    await digitar.type(busca(), "xyzw");

    expect(screen.getByText('Nada encontrado para "xyzw".')).toBeInTheDocument();
  });

  it("o atalho do assistente fecha a busca e abre o assistente", async () => {
    const dados = oficina();
    const { digitar, aoFechar } = await abrirPaleta("PROPRIETARIO", dados);

    await digitar.click(screen.getByRole("option", { name: /Perguntar ao assistente/ }));

    expect(aoFechar).toHaveBeenCalled();
    expect(dados.abrirAssistente).toHaveBeenCalled();
  });
});
