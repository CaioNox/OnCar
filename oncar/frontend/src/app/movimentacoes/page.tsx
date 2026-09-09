"use client";

import { useCallback, useEffect, useState } from "react";
import { AppShell, useUsuario } from "@/components/oncar/app-shell";
import { Erro, Sucesso, Vazio } from "@/components/oncar/avisos";
import { api, ErroDaApi, formatarDataHora, formatarNumero } from "@/lib/api";
import {
  podeGerenciarEstoque,
  podeRegistrarSaida,
  type Cliente,
  type Fornecedor,
  type Movimentacao,
  type Opcao,
  type Produto,
} from "@/lib/tipos";

export default function PaginaDeMovimentacoes() {
  return (
    <AppShell>
      <Movimentacoes />
    </AppShell>
  );
}

type Aba = "entrada" | "saida" | "ajuste";

function Movimentacoes() {
  const usuario = useUsuario();
  const gerenciaEstoque = podeGerenciarEstoque(usuario);
  const registraSaida = podeRegistrarSaida(usuario);

  const [movimentacoes, setMovimentacoes] = useState<Movimentacao[]>([]);
  const [produtos, setProdutos] = useState<Produto[]>([]);
  const [fornecedores, setFornecedores] = useState<Fornecedor[]>([]);
  const [clientes, setClientes] = useState<Cliente[]>([]);
  const [motivos, setMotivos] = useState<Opcao[]>([]);
  const [aba, setAba] = useState<Aba>(gerenciaEstoque ? "entrada" : "saida");
  const [erro, setErro] = useState<string | null>(null);
  const [detalhes, setDetalhes] = useState<string[]>([]);
  const [sucesso, setSucesso] = useState<string | null>(null);

  /** Recarrega o que muda a cada lancamento: o extrato recente e o saldo dos produtos. */
  const recarregar = useCallback(async () => {
    const [lista, produtosAtivos] = await Promise.all([
      api.get<Movimentacao[]>("/movimentacoes"),
      api.get<Produto[]>("/produtos?somenteAtivos=true"),
    ]);
    setMovimentacoes(lista);
    setProdutos(produtosAtivos);
  }, []);

  useEffect(() => {
    let ativo = true;
    async function carregar() {
      try {
        await recarregar();
      } catch (excecao) {
        if (!ativo) return;
        setErro(
          excecao instanceof ErroDaApi ? excecao.message : "Falha ao carregar as movimentacoes.",
        );
      }
    }
    carregar();
    api.get<Opcao[]>("/movimentacoes/motivos").then(setMotivos).catch(() => undefined);
    api.get<Cliente[]>("/clientes").then(setClientes).catch(() => undefined);
    if (gerenciaEstoque) {
      api.get<Fornecedor[]>("/fornecedores").then(setFornecedores).catch(() => undefined);
    }
    return () => {
      ativo = false;
    };
  }, [recarregar, gerenciaEstoque]);

  async function enviar(caminho: string, corpo: Record<string, unknown>, mensagem: string) {
    setErro(null);
    setDetalhes([]);
    setSucesso(null);
    try {
      await api.post(caminho, corpo);
      setSucesso(mensagem);
      await recarregar();
      return true;
    } catch (excecao) {
      if (excecao instanceof ErroDaApi) {
        setErro(excecao.message);
        setDetalhes(excecao.detalhes);
      } else {
        setErro("Nao foi possivel falar com o servidor. Tente novamente.");
      }
      return false;
    }
  }

  const abas: { chave: Aba; rotulo: string; visivel: boolean }[] = [
    { chave: "entrada", rotulo: "Registrar entrada", visivel: gerenciaEstoque },
    { chave: "saida", rotulo: "Registrar saida", visivel: registraSaida },
    { chave: "ajuste", rotulo: "Ajuste de inventario", visivel: gerenciaEstoque },
  ];

  return (
    <section>
      <h1 className="mb-1 text-xl font-semibold tracking-tight">Movimentacoes de estoque</h1>
      <p className="mb-4 text-sm text-muted-foreground">
        Movimentacoes confirmadas nao podem ser editadas nem excluidas (RN008). Para corrigir um
        lancamento, registre um estorno.
      </p>

      <Erro mensagem={erro} detalhes={detalhes} />
      <Sucesso mensagem={sucesso} />

      {abas.some((item) => item.visivel) && (
        <div className="mb-6 rounded-lg border bg-background">
          <div className="flex flex-wrap gap-1 border-b p-2">
            {abas
              .filter((item) => item.visivel)
              .map((item) => (
                <button
                  key={item.chave}
                  type="button"
                  onClick={() => setAba(item.chave)}
                  className={`rounded-md px-3 py-1.5 text-sm ${
                    aba === item.chave ? "bg-muted font-medium" : "text-muted-foreground hover:bg-muted/60"
                  }`}
                >
                  {item.rotulo}
                </button>
              ))}
          </div>

          <div className="p-4">
            {aba === "entrada" && gerenciaEstoque && (
              <FormularioDeEntrada produtos={produtos} fornecedores={fornecedores} aoEnviar={enviar} />
            )}
            {aba === "saida" && registraSaida && (
              <FormularioDeSaida
                produtos={produtos}
                clientes={clientes}
                motivos={motivos}
                aoEnviar={enviar}
              />
            )}
            {aba === "ajuste" && gerenciaEstoque && (
              <FormularioDeAjuste produtos={produtos} aoEnviar={enviar} />
            )}
          </div>
        </div>
      )}

      <div className="overflow-x-auto rounded-lg border bg-background">
        {movimentacoes.length === 0 ? (
          <Vazio>Nenhuma movimentacao registrada.</Vazio>
        ) : (
          <table className="w-full text-sm">
            <thead className="text-left text-muted-foreground">
              <tr>
                <th className="px-4 py-2 font-medium">Data</th>
                <th className="px-4 py-2 font-medium">Produto</th>
                <th className="px-4 py-2 font-medium">Tipo</th>
                <th className="px-4 py-2 font-medium">Motivo</th>
                <th className="px-4 py-2 text-right font-medium">Quantidade</th>
                <th className="px-4 py-2 text-right font-medium">Saldo</th>
                <th className="px-4 py-2 font-medium">Usuario</th>
                {gerenciaEstoque && <th className="px-4 py-2 font-medium">Estorno</th>}
              </tr>
            </thead>
            <tbody>
              {movimentacoes.map((movimentacao) => (
                <tr key={movimentacao.id} className="border-t">
                  <td className="px-4 py-2">{formatarDataHora(movimentacao.dataHora)}</td>
                  <td className="px-4 py-2">
                    {movimentacao.produtoSku} · {movimentacao.produtoDescricao}
                  </td>
                  <td className="px-4 py-2">{movimentacao.tipoDescricao}</td>
                  <td className="px-4 py-2">{movimentacao.motivo}</td>
                  <td className="px-4 py-2 text-right">{formatarNumero(movimentacao.quantidade, 3)}</td>
                  <td className="px-4 py-2 text-right">
                    {formatarNumero(movimentacao.saldoResultante, 3)}
                  </td>
                  <td className="px-4 py-2">{movimentacao.usuario}</td>
                  {gerenciaEstoque && (
                    <td className="px-4 py-2">
                      <Estorno movimentacao={movimentacao} aoEnviar={enviar} />
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}

type Enviar = (
  caminho: string,
  corpo: Record<string, unknown>,
  mensagem: string,
) => Promise<boolean>;

const CAMPO = "w-full rounded-md border px-3 py-2 text-sm";
const ROTULO = "mb-1 block text-sm font-medium";

/** RF08: entrada de mercadoria, com custo unitario que alimenta a RN004. */
function FormularioDeEntrada({
  produtos,
  fornecedores,
  aoEnviar,
}: {
  produtos: Produto[];
  fornecedores: Fornecedor[];
  aoEnviar: Enviar;
}) {
  const [produtoId, setProdutoId] = useState("");
  const [quantidade, setQuantidade] = useState("");
  const [custoUnitario, setCustoUnitario] = useState("");
  const [fornecedorId, setFornecedorId] = useState("");
  const [documento, setDocumento] = useState("");

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    const enviado = await aoEnviar(
      "/movimentacoes/entrada",
      {
        produtoId: Number(produtoId),
        quantidade,
        custoUnitario,
        fornecedorId: fornecedorId ? Number(fornecedorId) : null,
        documento: documento || null,
      },
      "Entrada registrada e saldo atualizado.",
    );
    if (enviado) {
      setQuantidade("");
      setCustoUnitario("");
      setDocumento("");
    }
  }

  return (
    <form className="grid gap-3 md:grid-cols-2 lg:grid-cols-4" onSubmit={enviar}>
      <div className="lg:col-span-2">
        <label className={ROTULO} htmlFor="entrada-produto">Produto</label>
        <select id="entrada-produto" required className={CAMPO} value={produtoId}
                onChange={(evento) => setProdutoId(evento.target.value)}>
          <option value="">Selecione</option>
          {produtos.map((produto) => (
            <option key={produto.id} value={produto.id}>
              {produto.sku} - {produto.descricao}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className={ROTULO} htmlFor="entrada-quantidade">Quantidade</label>
        <input id="entrada-quantidade" type="number" step="0.001" min="0.001" required className={CAMPO}
               value={quantidade} onChange={(evento) => setQuantidade(evento.target.value)} />
      </div>
      <div>
        <label className={ROTULO} htmlFor="entrada-custo">Custo unitario (R$)</label>
        <input id="entrada-custo" type="number" step="0.0001" min="0" required className={CAMPO}
               value={custoUnitario} onChange={(evento) => setCustoUnitario(evento.target.value)} />
      </div>
      <div className="lg:col-span-2">
        <label className={ROTULO} htmlFor="entrada-fornecedor">Fornecedor</label>
        <select id="entrada-fornecedor" className={CAMPO} value={fornecedorId}
                onChange={(evento) => setFornecedorId(evento.target.value)}>
          <option value="">Nao informado</option>
          {fornecedores.map((fornecedor) => (
            <option key={fornecedor.id} value={fornecedor.id}>{fornecedor.razaoSocial}</option>
          ))}
        </select>
      </div>
      <div>
        <label className={ROTULO} htmlFor="entrada-documento">NF-e (opcional)</label>
        <input id="entrada-documento" className={CAMPO} value={documento}
               onChange={(evento) => setDocumento(evento.target.value)} placeholder="Ex: NF-e 10021" />
      </div>
      <div className="flex items-end">
        <button type="submit"
                className="w-full rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground">
          Registrar entrada
        </button>
      </div>
    </form>
  );
}

/** RF09: saida com motivo obrigatorio; o saldo negativo e barrado pela RN002. */
function FormularioDeSaida({
  produtos,
  clientes,
  motivos,
  aoEnviar,
}: {
  produtos: Produto[];
  clientes: Cliente[];
  motivos: Opcao[];
  aoEnviar: Enviar;
}) {
  const [produtoId, setProdutoId] = useState("");
  const [quantidade, setQuantidade] = useState("");
  const [motivo, setMotivo] = useState("");
  const [clienteId, setClienteId] = useState("");
  const [documento, setDocumento] = useState("");

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    const enviado = await aoEnviar(
      "/movimentacoes/saida",
      {
        produtoId: Number(produtoId),
        quantidade,
        motivo,
        clienteId: clienteId ? Number(clienteId) : null,
        documento: documento || null,
      },
      "Saida registrada e saldo atualizado.",
    );
    if (enviado) {
      setQuantidade("");
      setDocumento("");
    }
  }

  return (
    <form className="grid gap-3 md:grid-cols-2 lg:grid-cols-4" onSubmit={enviar}>
      <div className="lg:col-span-2">
        <label className={ROTULO} htmlFor="saida-produto">Produto</label>
        <select id="saida-produto" required className={CAMPO} value={produtoId}
                onChange={(evento) => setProdutoId(evento.target.value)}>
          <option value="">Selecione</option>
          {produtos.map((produto) => (
            <option key={produto.id} value={produto.id}>
              {produto.sku} - {produto.descricao} (saldo {formatarNumero(produto.saldo, 3)})
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className={ROTULO} htmlFor="saida-quantidade">Quantidade</label>
        <input id="saida-quantidade" type="number" step="0.001" min="0.001" required className={CAMPO}
               value={quantidade} onChange={(evento) => setQuantidade(evento.target.value)} />
      </div>
      <div>
        <label className={ROTULO} htmlFor="saida-motivo">Motivo</label>
        <select id="saida-motivo" required className={CAMPO} value={motivo}
                onChange={(evento) => setMotivo(evento.target.value)}>
          <option value="">Selecione</option>
          {motivos.map((opcao) => (
            <option key={opcao.valor} value={opcao.valor}>{opcao.descricao}</option>
          ))}
        </select>
      </div>
      <div className="lg:col-span-2">
        <label className={ROTULO} htmlFor="saida-cliente">Cliente (opcional)</label>
        <select id="saida-cliente" className={CAMPO} value={clienteId}
                onChange={(evento) => setClienteId(evento.target.value)}>
          <option value="">Nao informado</option>
          {clientes.map((cliente) => (
            <option key={cliente.id} value={cliente.id}>{cliente.nome}</option>
          ))}
        </select>
      </div>
      <div>
        <label className={ROTULO} htmlFor="saida-documento">Documento</label>
        <input id="saida-documento" className={CAMPO} value={documento}
               onChange={(evento) => setDocumento(evento.target.value)} placeholder="Ex: OS 458" />
      </div>
      <div className="flex items-end">
        <button type="submit"
                className="w-full rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground">
          Registrar saida
        </button>
      </div>
    </form>
  );
}

/** RF11 e RN006: ajuste apos contagem, com justificativa de no minimo dez caracteres. */
function FormularioDeAjuste({ produtos, aoEnviar }: { produtos: Produto[]; aoEnviar: Enviar }) {
  const [produtoId, setProdutoId] = useState("");
  const [saldoApurado, setSaldoApurado] = useState("");
  const [justificativa, setJustificativa] = useState("");

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    const enviado = await aoEnviar(
      "/movimentacoes/ajuste",
      { produtoId: Number(produtoId), saldoApurado, justificativa },
      "Ajuste de inventario registrado.",
    );
    if (enviado) {
      setSaldoApurado("");
      setJustificativa("");
    }
  }

  return (
    <form className="grid gap-3 md:grid-cols-2" onSubmit={enviar}>
      <div>
        <label className={ROTULO} htmlFor="ajuste-produto">Produto</label>
        <select id="ajuste-produto" required className={CAMPO} value={produtoId}
                onChange={(evento) => setProdutoId(evento.target.value)}>
          <option value="">Selecione</option>
          {produtos.map((produto) => (
            <option key={produto.id} value={produto.id}>
              {produto.sku} - {produto.descricao} (saldo {formatarNumero(produto.saldo, 3)})
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className={ROTULO} htmlFor="ajuste-saldo">Saldo apurado na contagem</label>
        <input id="ajuste-saldo" type="number" step="0.001" min="0" required className={CAMPO}
               value={saldoApurado} onChange={(evento) => setSaldoApurado(evento.target.value)} />
      </div>
      <div className="md:col-span-2">
        <label className={ROTULO} htmlFor="ajuste-justificativa">Justificativa</label>
        <textarea id="ajuste-justificativa" required minLength={10} rows={2} className={CAMPO}
                  value={justificativa} onChange={(evento) => setJustificativa(evento.target.value)}
                  placeholder="Descreva o motivo da divergencia apurada na contagem" />
      </div>
      <div>
        <button type="submit"
                className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground">
          Registrar ajuste
        </button>
      </div>
    </form>
  );
}

/** RN008: correcao por estorno, sempre com justificativa. */
function Estorno({
  movimentacao,
  aoEnviar,
}: {
  movimentacao: Movimentacao;
  aoEnviar: Enviar;
}) {
  const [justificativa, setJustificativa] = useState("");

  if (movimentacao.estornada) {
    return <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">Estornada</span>;
  }
  if (!movimentacao.estornavel) {
    return <span className="text-xs text-muted-foreground">Estorno</span>;
  }

  return (
    <form
      className="flex gap-1"
      onSubmit={async (evento) => {
        evento.preventDefault();
        const enviado = await aoEnviar(
          `/movimentacoes/${movimentacao.id}/estorno`,
          { justificativa },
          "Movimentacao estornada.",
        );
        if (enviado) setJustificativa("");
      }}
    >
      <input
        required
        minLength={10}
        value={justificativa}
        onChange={(evento) => setJustificativa(evento.target.value)}
        placeholder="Justificativa"
        className="w-40 rounded-md border px-2 py-1 text-xs"
      />
      <button type="submit" className="rounded-md border px-2 py-1 text-xs hover:bg-muted">
        Estornar
      </button>
    </form>
  );
}
