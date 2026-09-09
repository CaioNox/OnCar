"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppShell, useUsuario } from "@/components/oncar/app-shell";
import { Erro, Vazio } from "@/components/oncar/avisos";
import { api, ErroDaApi, formatarMoeda, formatarNumero } from "@/lib/api";
import { podeGerenciarEstoque, type Opcao, type Produto } from "@/lib/tipos";

export default function PaginaDeProdutos() {
  return (
    <AppShell>
      <ListaDeProdutos />
    </AppShell>
  );
}

type Filtros = { termo: string; categoria: string; somenteAtivos: boolean };

/** RF07: busca por descricao, SKU, marca ou categoria, exibindo saldo e preco. */
function ListaDeProdutos() {
  const usuario = useUsuario();
  const exibeCusto = podeGerenciarEstoque(usuario);

  const [produtos, setProdutos] = useState<Produto[]>([]);
  const [categorias, setCategorias] = useState<Opcao[]>([]);
  const [termo, setTermo] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(true);
  // Os filtros aplicados sao guardados a parte do que esta sendo digitado: a busca
  // roda quando eles mudam, e o campo de texto so entra ao enviar o formulario.
  const [filtros, setFiltros] = useState<Filtros>({ termo: "", categoria: "", somenteAtivos: true });

  useEffect(() => {
    api.get<Opcao[]>("/categorias").then(setCategorias).catch(() => undefined);
  }, []);

  useEffect(() => {
    let ativo = true;
    async function buscar() {
      const parametros = new URLSearchParams();
      if (filtros.termo) parametros.set("termo", filtros.termo);
      if (filtros.categoria) parametros.set("categoria", filtros.categoria);
      parametros.set("somenteAtivos", String(filtros.somenteAtivos));
      try {
        const encontrados = await api.get<Produto[]>(`/produtos?${parametros}`);
        if (!ativo) return;
        setProdutos(encontrados);
        setErro(null);
      } catch (excecao) {
        if (!ativo) return;
        setErro(excecao instanceof ErroDaApi ? excecao.message : "Falha ao buscar produtos.");
      } finally {
        if (ativo) setCarregando(false);
      }
    }
    buscar();
    return () => {
      ativo = false;
    };
  }, [filtros]);

  function aplicarFiltros(alteracao: Partial<Filtros>) {
    setCarregando(true);
    setFiltros((atuais) => ({ ...atuais, ...alteracao }));
  }

  return (
    <section>
      <h1 className="mb-4 text-xl font-semibold tracking-tight">Produtos</h1>
      <Erro mensagem={erro} />

      <form
        className="mb-4 flex flex-wrap items-end gap-3 rounded-lg border bg-background p-4"
        onSubmit={(evento) => {
          evento.preventDefault();
          aplicarFiltros({ termo: termo.trim() });
        }}
      >
        <div className="min-w-56 flex-1">
          <label className="mb-1 block text-sm font-medium" htmlFor="termo">
            Descricao, SKU ou marca
          </label>
          <input
            id="termo"
            value={termo}
            onChange={(evento) => setTermo(evento.target.value)}
            placeholder="Ex: filtro de oleo"
            className="w-full rounded-md border px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium" htmlFor="categoria">
            Categoria
          </label>
          <select
            id="categoria"
            value={filtros.categoria}
            onChange={(evento) => aplicarFiltros({ categoria: evento.target.value })}
            className="rounded-md border px-3 py-2 text-sm"
          >
            <option value="">Todas</option>
            {categorias.map((opcao) => (
              <option key={opcao.valor} value={opcao.valor}>
                {opcao.descricao}
              </option>
            ))}
          </select>
        </div>
        <label className="flex items-center gap-2 pb-2 text-sm">
          <input
            type="checkbox"
            checked={filtros.somenteAtivos}
            onChange={(evento) => aplicarFiltros({ somenteAtivos: evento.target.checked })}
          />
          Somente ativos
        </label>
        <button
          type="submit"
          className="rounded-md border px-3 py-2 text-sm hover:bg-muted"
          disabled={carregando}
        >
          {carregando ? "Buscando..." : "Buscar"}
        </button>
      </form>

      <div className="overflow-x-auto rounded-lg border bg-background">
        {produtos.length === 0 ? (
          <Vazio>Nenhum produto encontrado.</Vazio>
        ) : (
          <table className="w-full text-sm">
            <thead className="text-left text-muted-foreground">
              <tr>
                <th className="px-4 py-2 font-medium">SKU</th>
                <th className="px-4 py-2 font-medium">Descricao</th>
                <th className="px-4 py-2 font-medium">Categoria</th>
                <th className="px-4 py-2 text-right font-medium">Saldo</th>
                <th className="px-4 py-2 text-right font-medium">Minimo</th>
                {exibeCusto && <th className="px-4 py-2 text-right font-medium">Custo medio</th>}
                <th className="px-4 py-2 text-right font-medium">Preco de venda</th>
                <th className="px-4 py-2 font-medium">Situacao</th>
              </tr>
            </thead>
            <tbody>
              {produtos.map((produto) => (
                <tr
                  key={produto.id}
                  className={`border-t ${produto.abaixoDoMinimo ? "bg-amber-500/5" : ""}`}
                >
                  <td className="px-4 py-2">{produto.sku}</td>
                  <td className="px-4 py-2">
                    <Link className="underline-offset-2 hover:underline" href={`/produtos/${produto.id}`}>
                      {produto.descricao}
                    </Link>
                  </td>
                  <td className="px-4 py-2">{produto.categoria}</td>
                  <td className="px-4 py-2 text-right">
                    {formatarNumero(produto.saldo, 3)} {produto.unidade}
                  </td>
                  <td className="px-4 py-2 text-right">{formatarNumero(produto.estoqueMinimo, 3)}</td>
                  {exibeCusto && (
                    <td className="px-4 py-2 text-right">{formatarMoeda(produto.precoCusto)}</td>
                  )}
                  <td className="px-4 py-2 text-right">{formatarMoeda(produto.precoVenda)}</td>
                  <td className="px-4 py-2">
                    {produto.ativo ? (
                      <span className="rounded bg-emerald-600/10 px-2 py-0.5 text-xs text-emerald-700 dark:text-emerald-400">
                        Ativo
                      </span>
                    ) : (
                      <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                        Inativo
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}
