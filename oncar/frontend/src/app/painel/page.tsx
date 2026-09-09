"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppShell, useUsuario } from "@/components/oncar/app-shell";
import { Cartao } from "@/components/oncar/cartao";
import { Erro, Vazio } from "@/components/oncar/avisos";
import { api, ErroDaApi, formatarDataHora, formatarMoeda, formatarNumero } from "@/lib/api";
import type { Painel } from "@/lib/tipos";

export default function PaginaDoPainel() {
  return (
    <AppShell>
      <ConteudoDoPainel />
    </AppShell>
  );
}

/** RF12 e RN005: indicadores, lista de reposicao e ultimas movimentacoes. */
function ConteudoDoPainel() {
  const usuario = useUsuario();
  const [painel, setPainel] = useState<Painel | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<Painel>("/painel")
      .then(setPainel)
      .catch((excecao) =>
        setErro(excecao instanceof ErroDaApi ? excecao.message : "Falha ao carregar o painel."),
      );
  }, []);

  return (
    <section>
      <h1 className="mb-4 text-xl font-semibold tracking-tight">
        Bem-vindo, {usuario?.nome}
      </h1>
      <Erro mensagem={erro} />

      {painel && (
        <>
          <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <Cartao titulo="Produtos ativos" valor={painel.produtosAtivos} />
            <Cartao
              titulo="Itens abaixo do minimo"
              valor={painel.itensAbaixoDoMinimo}
              destaque={painel.itensAbaixoDoMinimo > 0}
            />
            {painel.valorTotalEmEstoque !== null && (
              <Cartao
                titulo="Valor total em estoque"
                valor={formatarMoeda(painel.valorTotalEmEstoque)}
              />
            )}
          </div>

          <div className="mb-6 rounded-lg border bg-background">
            <h2 className="border-b px-4 py-3 text-sm font-medium">
              Lista de reposicao (saldo igual ou abaixo do minimo)
            </h2>
            {painel.listaDeReposicao.length === 0 ? (
              <Vazio>Nenhum item precisa de reposicao.</Vazio>
            ) : (
              <table className="w-full text-sm">
                <thead className="text-left text-muted-foreground">
                  <tr>
                    <th className="px-4 py-2 font-medium">SKU</th>
                    <th className="px-4 py-2 font-medium">Descricao</th>
                    <th className="px-4 py-2 text-right font-medium">Saldo</th>
                    <th className="px-4 py-2 text-right font-medium">Minimo</th>
                  </tr>
                </thead>
                <tbody>
                  {painel.listaDeReposicao.map((produto) => (
                    <tr key={produto.id} className="border-t bg-amber-500/5">
                      <td className="px-4 py-2">{produto.sku}</td>
                      <td className="px-4 py-2">
                        <Link className="underline-offset-2 hover:underline" href={`/produtos/${produto.id}`}>
                          {produto.descricao}
                        </Link>
                      </td>
                      <td className="px-4 py-2 text-right">{formatarNumero(produto.saldo, 3)}</td>
                      <td className="px-4 py-2 text-right">{formatarNumero(produto.estoqueMinimo, 3)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          <div className="rounded-lg border bg-background">
            <h2 className="border-b px-4 py-3 text-sm font-medium">Ultimas movimentacoes</h2>
            {painel.ultimasMovimentacoes.length === 0 ? (
              <Vazio>Nenhuma movimentacao registrada.</Vazio>
            ) : (
              <table className="w-full text-sm">
                <thead className="text-left text-muted-foreground">
                  <tr>
                    <th className="px-4 py-2 font-medium">Data</th>
                    <th className="px-4 py-2 font-medium">Produto</th>
                    <th className="px-4 py-2 font-medium">Tipo</th>
                    <th className="px-4 py-2 text-right font-medium">Quantidade</th>
                    <th className="px-4 py-2 text-right font-medium">Saldo</th>
                    <th className="px-4 py-2 font-medium">Usuario</th>
                  </tr>
                </thead>
                <tbody>
                  {painel.ultimasMovimentacoes.map((movimentacao) => (
                    <tr key={movimentacao.id} className="border-t">
                      <td className="px-4 py-2">{formatarDataHora(movimentacao.dataHora)}</td>
                      <td className="px-4 py-2">{movimentacao.produtoDescricao}</td>
                      <td className="px-4 py-2">{movimentacao.tipoDescricao}</td>
                      <td className="px-4 py-2 text-right">{formatarNumero(movimentacao.quantidade, 3)}</td>
                      <td className="px-4 py-2 text-right">{formatarNumero(movimentacao.saldoResultante, 3)}</td>
                      <td className="px-4 py-2">{movimentacao.usuario}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}
    </section>
  );
}
