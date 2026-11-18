import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ListaDeProdutos } from "@/app/(oficina)/produtos/lista";
import { renderizarNaOficina, usuario } from "@/components/oncar/oficina.test-utils";
import { produto } from "@/lib/fixtures.test-utils";
import type { Perfil, Produto } from "@/lib/tipos";

const CATEGORIAS = [
  { valor: "FILTRO", descricao: "Filtro" },
  { valor: "PECA", descricao: "Peça" },
];

const ESTOQUE: Produto[] = [
  produto({ id: 1, sku: "FL-001", descricao: "Filtro de oleo", saldo: "1", estoqueMinimo: "2", abaixoDoMinimo: true }),
  produto({ id: 2, sku: "VL-1", descricao: "Vela de ignicao", saldo: "3", estoqueMinimo: "2" }),
  produto({ id: 3, sku: "PF-9", descricao: "Pastilha de freio", saldo: "50", estoqueMinimo: "2", precoCusto: "41.5000" }),
];

/** Pedidos feitos a /api/produtos, para verificar os filtros enviados ao servidor. */
let buscas: URLSearchParams[];
let respostaDosProdutos: () => Promise<Response>;

beforeEach(() => {
  localStorage.clear();
  buscas = [];
  respostaDosProdutos = async () => new Response(JSON.stringify(ESTOQUE), { status: 200 });
  vi.spyOn(globalThis, "fetch").mockImplementation(async (endereco) => {
    const url = new URL(String(endereco), "http://oncar.local");
    if (url.pathname === "/api/categorias") return new Response(JSON.stringify(CATEGORIAS), { status: 200 });
    if (url.pathname === "/api/produtos") {
      buscas.push(url.searchParams);
      return respostaDosProdutos();
    }
    throw new Error(`rota nao simulada: ${url.pathname}`);
  });
});

function abrir(perfil: Perfil = "PROPRIETARIO", nivelInicial: Parameters<typeof ListaDeProdutos>[0]["nivelInicial"] = null) {
  renderizarNaOficina(<ListaDeProdutos nivelInicial={nivelInicial} />, { sessao: usuario(perfil) });
  return userEvent.setup();
}

const linhas = () => within(screen.getByRole("table")).getAllByRole("row").slice(1);
const contagem = () => screen.getByText(/produtos?( ·|$)|Buscando/);

describe("<ListaDeProdutos>", () => {
  it("antes da primeira resposta mostra o esqueleto de carregamento", () => {
    respostaDosProdutos = () => new Promise(() => undefined);
    abrir();

    expect(screen.getByLabelText("Carregando produtos")).toHaveAttribute("aria-busy", "true");
    expect(screen.getByText("Buscando no estoque…")).toBeInTheDocument();
  });

  it("lista os produtos ativos e resume quantos precisam de reposicao", async () => {
    abrir();

    expect(await screen.findByRole("table")).toBeInTheDocument();
    expect(linhas()).toHaveLength(3);
    expect(contagem()).toHaveTextContent("3 produtos · 1 para repor");
    expect(buscas[0].get("somenteAtivos")).toBe("true");
  });

  it("a coluna de custo medio so existe para quem administra o estoque", async () => {
    abrir("GERENTE_ESTOQUE");
    expect(await screen.findByRole("columnheader", { name: "Custo médio" })).toBeInTheDocument();
  });

  it("o atendente nao ve a coluna de custo", async () => {
    abrir("ATENDENTE");

    await screen.findByRole("table");
    expect(screen.queryByRole("columnheader", { name: "Custo médio" })).not.toBeInTheDocument();
  });

  it("a busca espera o usuario parar de digitar e manda um pedido so", async () => {
    const digitar = abrir();
    await screen.findByRole("table");

    await digitar.type(screen.getByRole("searchbox"), "  vela ");

    await waitFor(() => expect(buscas.at(-1)?.get("termo")).toBe("vela"));
    expect(buscas.filter((busca) => busca.has("termo"))).toHaveLength(1);
  });

  it("categoria e situacao viram filtros na URL da API", async () => {
    const clicar = abrir();
    await screen.findByRole("table");

    await clicar.click(await screen.findByRole("button", { name: "Filtro" }));
    await waitFor(() => expect(buscas.at(-1)?.get("categoria")).toBe("FILTRO"));

    await clicar.click(screen.getByRole("button", { name: "Somente ativos" }));
    await waitFor(() => expect(buscas.at(-1)?.get("somenteAtivos")).toBe("false"));
  });

  it("o filtro de nivel e local: mostra so o que precisa repor, sem nova busca", async () => {
    const clicar = abrir();
    await screen.findByRole("table");
    const pedidosAntes = buscas.length;

    await clicar.click(screen.getByRole("button", { name: "Repor" }));

    expect(linhas()).toHaveLength(1);
    expect(linhas()[0]).toHaveTextContent("Filtro de oleo");
    expect(buscas).toHaveLength(pedidosAntes);
  });

  it("nivel inicial vindo da URL (?nivel=critico) ja chega filtrado", async () => {
    abrir("PROPRIETARIO", "critico");

    await screen.findByRole("table");
    expect(screen.getByRole("button", { name: "Repor" })).toHaveAttribute("aria-pressed", "true");
    expect(linhas()).toHaveLength(1);
  });

  it("sem resultados oferece limpar os filtros, que voltam ao padrao", async () => {
    respostaDosProdutos = async () => new Response("[]", { status: 200 });
    const clicar = abrir("PROPRIETARIO", "critico");

    await clicar.click(await screen.findByRole("button", { name: "Limpar filtros" }));

    expect(screen.getByRole("button", { name: "Repor" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "Somente ativos" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Todas as categorias" })).toHaveAttribute("aria-pressed", "true");
  });

  it("erro da API aparece como alerta e a tela continua utilizavel", async () => {
    respostaDosProdutos = async () =>
      new Response(JSON.stringify({ mensagem: "Sua sessao expirou." }), { status: 401 });
    abrir();

    expect(await screen.findByRole("alert")).toHaveTextContent("Sua sessao expirou.");
    expect(screen.getByRole("searchbox")).toBeEnabled();
  });

  it("a visao em cartoes e lembrada para a proxima visita", async () => {
    const clicar = abrir();
    await screen.findByRole("table");

    await clicar.click(screen.getByRole("button", { name: "Cartões" }));

    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: /Filtro de oleo|Vela|Pastilha/ })).toHaveLength(3);
    expect(localStorage.getItem("oncar-produtos-visao")).toBe("cartoes");
  });

  it("sem preferencia salva, telas estreitas comecam em cartoes", async () => {
    vi.spyOn(window, "matchMedia").mockReturnValue({ matches: true } as MediaQueryList);
    abrir();

    expect(await screen.findByRole("button", { name: "Cartões" })).toHaveAttribute("aria-pressed", "true");
  });
});
