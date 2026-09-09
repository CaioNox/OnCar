"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppShell } from "@/components/oncar/app-shell";
import { Erro, Vazio } from "@/components/oncar/avisos";
import { api, ErroDaApi, formatarDataHora, formatarNumero } from "@/lib/api";
import type { Movimentacao, Produto } from "@/lib/tipos";

export function ExtratoDoProduto({ id }: { id: string }) {
  return (
    <AppShell>
      <Conteudo id={id} />
    </AppShell>
  );
}

function Conteudo({ id }: { id: string }) {
  const [produto, setProduto] = useState<Produto | null>(null);
  const [movimentacoes, setMovimentacoes] = useState<Movimentacao[]>([]);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([
      api.get<Produto>(`/produtos/${id}`),
      api.get<Movimentacao[]>(`/produtos/${id}/extrato`),
    ])
      .then(([produtoCarregado, extrato]) => {
        setProduto(produtoCarregado);
        setMovimentacoes(extrato);
      })
      .catch((excecao) =>
        setErro(excecao instanceof ErroDaApi ? excecao.message : "Falha ao carregar o extrato."),
      );
  }, [id]);

  return (
    <section>
      <Link className="text-sm text-muted-foreground hover:underline" href="/produtos">
        ← Voltar para produtos
      </Link>
      <h1 className="mt-2 text-xl font-semibold tracking-tight">Extrato de movimentacoes</h1>
      {produto && (
        <p className="mb-4 text-sm text-muted-foreground">
          {produto.sku} · {produto.descricao} · saldo atual{" "}
          {formatarNumero(produto.saldo, 3)} {produto.unidade}
        </p>
      )}
      <Erro mensagem={erro} />

      <div className="overflow-x-auto rounded-lg border bg-background">
        {movimentacoes.length === 0 ? (
          <Vazio>Nenhuma movimentacao para este produto.</Vazio>
        ) : (
          <table className="w-full text-sm">
            <thead className="text-left text-muted-foreground">
              <tr>
                <th className="px-4 py-2 font-medium">Data</th>
                <th className="px-4 py-2 font-medium">Tipo</th>
                <th className="px-4 py-2 font-medium">Motivo</th>
                <th className="px-4 py-2 text-right font-medium">Quantidade</th>
                <th className="px-4 py-2 text-right font-medium">Saldo resultante</th>
                <th className="px-4 py-2 font-medium">Documento</th>
                <th className="px-4 py-2 font-medium">Usuario</th>
                <th className="px-4 py-2 font-medium">Justificativa</th>
              </tr>
            </thead>
            <tbody>
              {movimentacoes.map((movimentacao) => (
                <tr
                  key={movimentacao.id}
                  className={`border-t ${
                    movimentacao.estornada ? "text-muted-foreground line-through" : ""
                  }`}
                >
                  <td className="px-4 py-2">{formatarDataHora(movimentacao.dataHora)}</td>
                  <td className="px-4 py-2">{movimentacao.tipoDescricao}</td>
                  <td className="px-4 py-2">{movimentacao.motivo}</td>
                  <td className="px-4 py-2 text-right">{formatarNumero(movimentacao.quantidade, 3)}</td>
                  <td className="px-4 py-2 text-right">
                    {formatarNumero(movimentacao.saldoResultante, 3)}
                  </td>
                  <td className="px-4 py-2">{movimentacao.documento ?? "-"}</td>
                  <td className="px-4 py-2">{movimentacao.usuario}</td>
                  <td className="px-4 py-2">{movimentacao.justificativa ?? "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}
