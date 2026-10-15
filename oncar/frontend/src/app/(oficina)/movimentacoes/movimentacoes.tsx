"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import CurrencyTransfer, { type LadoDaTransferencia } from "@/components/kokonutui/currency-transfer";
import ParticleButton, { type ControleDeParticulas } from "@/components/kokonutui/particle-button";
import { Erro } from "@/components/oncar/avisos";
import { AREA_DE_TEXTO, CAMPO, Rotulo } from "@/components/oncar/campo";
import { Chip } from "@/components/oncar/chip";
import { useOficina, useUsuario } from "@/components/oncar/contexto";
import { EstadoVazio } from "@/components/oncar/estado-vazio";
import { TipoDeMovimentacao } from "@/components/oncar/etiquetas";
import { useToast } from "@/components/oncar/toasts";
import { Button } from "@/components/ui/button";
import {
  api,
  ErroDaApi,
  formatarDataHora,
  formatarMoeda,
  formatarQuantidade,
} from "@/lib/api";
import {
  podeGerenciarEstoque,
  podeRegistrarSaida,
  type Cliente,
  type Fornecedor,
  type Movimentacao,
  type Opcao,
  type Produto,
} from "@/lib/tipos";
import { cn } from "@/lib/utils";

export type Aba = "entrada" | "saida" | "ajuste";

type Lados = { de: LadoDaTransferencia; para: LadoDaTransferencia };

type Confirmacao = Lados & {
  aba: Aba;
  concluido: boolean;
  resultado?: string;
  saldo?: string;
};

type Registrar = (
  aba: Aba,
  caminho: string,
  corpo: Record<string, unknown>,
  lados: Lados,
  mensagem: string,
  unidade: string,
) => Promise<boolean>;

const ABAS: { chave: Aba; rotulo: string; icone: string }[] = [
  { chave: "entrada", rotulo: "Entrada", icone: "bi-box-arrow-in-down" },
  { chave: "saida", rotulo: "Saída", icone: "bi-box-arrow-up" },
  { chave: "ajuste", rotulo: "Ajuste", icone: "bi-sliders" },
];

const TEXTOS: Record<Aba, { processando: string; concluido: string; conferindo: string }> = {
  entrada: {
    processando: "Registrando entrada…",
    concluido: "Entrada registrada",
    conferindo: "Recalculando o custo médio (RN004)…",
  },
  saida: {
    processando: "Dando baixa no estoque…",
    concluido: "Saída registrada",
    conferindo: "Conferindo o saldo disponível (RN002)…",
  },
  ajuste: {
    processando: "Registrando o ajuste…",
    concluido: "Ajuste registrado",
    conferindo: "Aplicando a contagem ao saldo…",
  },
};

// Depois da confirmacao a bancada volta sozinha para o formulario.
const VOLTA_AO_FORMULARIO_MS = 2600;

function icone(nome: string) {
  return <i aria-hidden="true" className={cn("bi", nome)} />;
}

export function Movimentacoes({
  abaInicial,
  clienteInicial,
  documentoInicial,
}: {
  abaInicial: Aba | null;
  clienteInicial: number | null;
  documentoInicial: string | null;
}) {
  const usuario = useUsuario();
  const gerenciaEstoque = podeGerenciarEstoque(usuario);
  const registraSaida = podeRegistrarSaida(usuario);
  const { movimentacoes, produtos, carregado, recarregar } = useOficina();
  const avisar = useToast();

  const abasVisiveis = ABAS.filter((item) => (item.chave === "saida" ? registraSaida : gerenciaEstoque));
  const [aba, setAba] = useState<Aba>(() =>
    abaInicial && abasVisiveis.some((item) => item.chave === abaInicial)
      ? abaInicial
      : (abasVisiveis[0]?.chave ?? "saida"),
  );
  const [fornecedores, setFornecedores] = useState<Fornecedor[]>([]);
  const [clientes, setClientes] = useState<Cliente[]>([]);
  const [motivos, setMotivos] = useState<Opcao[]>([]);
  const [erro, setErro] = useState<string | null>(null);
  const [detalhes, setDetalhes] = useState<string[]>([]);
  const [confirmacao, setConfirmacao] = useState<Confirmacao | null>(null);
  const bancada = useRef<HTMLDivElement>(null);
  const retorno = useRef<number | undefined>(undefined);

  const produtosAtivos = useMemo(() => produtos.filter((produto) => produto.ativo), [produtos]);

  useEffect(() => {
    api.get<Opcao[]>("/movimentacoes/motivos").then(setMotivos).catch(() => undefined);
    api.get<Cliente[]>("/clientes").then(setClientes).catch(() => undefined);
    if (gerenciaEstoque) {
      api.get<Fornecedor[]>("/fornecedores").then(setFornecedores).catch(() => undefined);
    }
  }, [gerenciaEstoque]);

  useEffect(() => () => window.clearTimeout(retorno.current), []);

  function voltarAoFormulario() {
    window.clearTimeout(retorno.current);
    setConfirmacao(null);
    requestAnimationFrame(() =>
      bancada.current?.querySelector<HTMLElement>("[data-primeiro-campo]")?.focus(),
    );
  }

  const registrar: Registrar = async (abaDoEnvio, caminho, corpo, lados, mensagem, unidade) => {
    setErro(null);
    setDetalhes([]);
    setConfirmacao({ aba: abaDoEnvio, ...lados, concluido: false });
    try {
      const nova = await api.post<Movimentacao>(caminho, corpo);
      setConfirmacao((atual) =>
        atual && {
          ...atual,
          concluido: true,
          resultado: `Movimentação #${nova.id} · ${formatarDataHora(nova.dataHora)}`,
          saldo: `Saldo resultante: ${formatarQuantidade(nova.saldoResultante)} ${unidade}`,
        },
      );
      avisar(mensagem);
      // O shell recarrega painel, historico e produtos: o wallpaper reage na hora.
      void recarregar();
      retorno.current = window.setTimeout(voltarAoFormulario, VOLTA_AO_FORMULARIO_MS);
      return true;
    } catch (excecao) {
      setConfirmacao(null);
      if (excecao instanceof ErroDaApi) {
        setErro(excecao.message);
        setDetalhes(excecao.detalhes);
      } else {
        setErro("Não foi possível falar com o servidor. Tente novamente.");
      }
      return false;
    }
  };

  async function estornar(movimentacao: Movimentacao, justificativa: string): Promise<string | null> {
    try {
      await api.post(`/movimentacoes/${movimentacao.id}/estorno`, { justificativa });
      avisar("Movimentação estornada.");
      void recarregar();
      return null;
    } catch (excecao) {
      return excecao instanceof ErroDaApi
        ? [excecao.message, ...excecao.detalhes].join(" ")
        : "Não foi possível falar com o servidor. Tente novamente.";
    }
  }

  return (
    <section aria-labelledby="titulo-movimentacoes">
      <header className="mb-10 md:mb-14">
        <p className="rotulo">RF08 · RF09 · RF11 · Estoque</p>
        <h1 id="titulo-movimentacoes" className="fonte-display mt-3 text-display">
          Movimentações
        </h1>
        <p className="mt-4 max-w-2xl text-muted-foreground">
          Lançamentos confirmados não se editam nem se excluem (RN008). Para corrigir um lançamento,
          registre um estorno.
        </p>
      </header>

      {abasVisiveis.length > 0 && (
        <section aria-labelledby="titulo-bancada" className="vidro rounded-3xl border p-6 sm:p-8">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <p className="rotulo">Bancada</p>
              <h2 id="titulo-bancada" className="fonte-display mt-1 text-2xl sm:text-3xl">
                Novo lançamento
              </h2>
            </div>
            <Abas abas={abasVisiveis} ativa={aba} aoMudar={setAba} desativadas={confirmacao !== null} />
          </div>

          <div ref={bancada} className="mt-8 grid">
            <div
              id={`painel-${aba}`}
              role="tabpanel"
              aria-labelledby={`aba-${aba}`}
              inert={confirmacao ? true : undefined}
              className={cn("col-start-1 row-start-1", confirmacao && "invisible")}
            >
              <Erro mensagem={erro} detalhes={detalhes} />
              {aba === "entrada" && gerenciaEstoque && (
                <FormularioDeEntrada produtos={produtosAtivos} fornecedores={fornecedores} aoRegistrar={registrar} />
              )}
              {aba === "saida" && registraSaida && (
                <FormularioDeSaida
                  produtos={produtosAtivos}
                  clientes={clientes}
                  motivos={motivos}
                  clienteInicial={clienteInicial}
                  aoRegistrar={registrar}
                />
              )}
              {aba === "ajuste" && gerenciaEstoque && (
                <FormularioDeAjuste produtos={produtosAtivos} aoRegistrar={registrar} />
              )}
            </div>

            {confirmacao && (
              <div className="col-start-1 row-start-1 flex flex-col items-center justify-center">
                <CurrencyTransfer
                  concluido={confirmacao.concluido}
                  titulo={TEXTOS[confirmacao.aba]}
                  subtitulo={{ processando: "Atualizando o saldo (RF10)", concluido: confirmacao.resultado ?? "" }}
                  rodape={{ processando: TEXTOS[confirmacao.aba].conferindo, concluido: confirmacao.saldo ?? "" }}
                  de={confirmacao.de}
                  para={confirmacao.para}
                />
                {confirmacao.concluido && (
                  <button
                    type="button"
                    onClick={voltarAoFormulario}
                    className="mt-5 inline-flex h-9 items-center gap-2 rounded-lg border px-4 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
                  >
                    {icone("bi-plus-lg")}
                    Novo lançamento
                  </button>
                )}
              </div>
            )}
          </div>
        </section>
      )}

      <Historico
        movimentacoes={movimentacoes}
        carregado={carregado}
        gerenciaEstoque={gerenciaEstoque}
        documentoInicial={documentoInicial}
        aoEstornar={estornar}
      />
    </section>
  );
}

/** Abas com setas do teclado (tabindex movel), no padrao ARIA de tabs. */
function Abas({
  abas,
  ativa,
  aoMudar,
  desativadas,
}: {
  abas: typeof ABAS;
  ativa: Aba;
  aoMudar: (aba: Aba) => void;
  desativadas: boolean;
}) {
  const botoes = useRef<(HTMLButtonElement | null)[]>([]);

  function aoTeclar(evento: React.KeyboardEvent, indice: number) {
    if (evento.key !== "ArrowRight" && evento.key !== "ArrowLeft") return;
    evento.preventDefault();
    const proximo = (indice + (evento.key === "ArrowRight" ? 1 : -1) + abas.length) % abas.length;
    aoMudar(abas[proximo].chave);
    botoes.current[proximo]?.focus();
  }

  return (
    <div role="tablist" aria-label="Tipo de lançamento" className="inline-flex rounded-full border bg-background/40 p-1">
      {abas.map((item, indice) => (
        <button
          key={item.chave}
          ref={(elemento) => {
            botoes.current[indice] = elemento;
          }}
          id={`aba-${item.chave}`}
          role="tab"
          type="button"
          aria-selected={ativa === item.chave}
          aria-controls={`painel-${item.chave}`}
          tabIndex={ativa === item.chave ? 0 : -1}
          disabled={desativadas}
          onClick={() => aoMudar(item.chave)}
          onKeyDown={(evento) => aoTeclar(evento, indice)}
          className={cn(
            "flex h-9 items-center gap-2 rounded-full px-4 text-sm transition-colors disabled:cursor-not-allowed",
            ativa === item.chave
              ? "bg-primary text-primary-foreground"
              : "text-muted-foreground hover:text-foreground disabled:hover:text-muted-foreground",
          )}
        >
          {icone(item.icone)}
          {item.rotulo}
        </button>
      ))}
    </div>
  );
}

function OpcoesDeProduto({ produtos, comSaldo }: { produtos: Produto[]; comSaldo?: boolean }) {
  return (
    <>
      <option value="">Selecione</option>
      {produtos.map((produto) => (
        <option key={produto.id} value={produto.id}>
          {produto.sku} - {produto.descricao}
          {comSaldo ? ` (saldo ${formatarQuantidade(produto.saldo)} ${produto.unidade})` : ""}
        </option>
      ))}
    </>
  );
}

function Dica({ children, tom }: { children: React.ReactNode; tom?: "atencao" }) {
  return (
    <p className={cn("num mt-1.5 text-xs", tom === "atencao" ? "text-atencao-texto" : "text-muted-foreground")}>
      {children}
    </p>
  );
}

/** RF08: entrada de mercadoria, com custo unitario que alimenta a RN004. */
function FormularioDeEntrada({
  produtos,
  fornecedores,
  aoRegistrar,
}: {
  produtos: Produto[];
  fornecedores: Fornecedor[];
  aoRegistrar: Registrar;
}) {
  const [produtoId, setProdutoId] = useState("");
  const [quantidade, setQuantidade] = useState("");
  const [custoUnitario, setCustoUnitario] = useState("");
  const [fornecedorId, setFornecedorId] = useState("");
  const [documento, setDocumento] = useState("");
  const produto = produtos.find((item) => String(item.id) === produtoId);

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    if (!produto) return;
    const fornecedor = fornecedores.find((item) => String(item.id) === fornecedorId);
    const enviado = await aoRegistrar(
      "entrada",
      "/movimentacoes/entrada",
      {
        produtoId: Number(produtoId),
        quantidade,
        custoUnitario,
        fornecedorId: fornecedorId ? Number(fornecedorId) : null,
        documento: documento || null,
      },
      {
        de: {
          rotulo: "De",
          titulo: fornecedor?.razaoSocial ?? "Fornecedor não informado",
          detalhe: documento || "sem nota fiscal",
          simbolo: icone("bi-truck"),
        },
        para: {
          rotulo: "Para",
          titulo: `${produto.sku} · ${produto.descricao}`,
          detalhe: `+${formatarQuantidade(quantidade)} ${produto.unidade} · ${formatarMoeda(Number(quantidade) * Number(custoUnitario))}`,
          simbolo: icone("bi-box-seam"),
        },
      },
      "Entrada registrada e saldo atualizado.",
      produto.unidade,
    );
    if (enviado) {
      setQuantidade("");
      setCustoUnitario("");
      setDocumento("");
    }
  }

  return (
    <form className="grid gap-5 md:grid-cols-2 lg:grid-cols-4" onSubmit={enviar}>
      <div className="lg:col-span-2">
        <Rotulo htmlFor="entrada-produto">Produto</Rotulo>
        <select id="entrada-produto" data-primeiro-campo required className={CAMPO} value={produtoId}
                onChange={(evento) => setProdutoId(evento.target.value)}>
          <OpcoesDeProduto produtos={produtos} />
        </select>
        {produto && (
          <Dica>
            saldo {formatarQuantidade(produto.saldo)} {produto.unidade}
            {produto.precoCusto !== null && ` · custo médio ${formatarMoeda(produto.precoCusto)}`}
          </Dica>
        )}
      </div>
      <div>
        <Rotulo htmlFor="entrada-quantidade" dica={produto?.unidade}>Quantidade</Rotulo>
        <input id="entrada-quantidade" type="number" inputMode="decimal" step="0.001" min="0.001" required className={CAMPO}
               value={quantidade} onChange={(evento) => setQuantidade(evento.target.value)} />
      </div>
      <div>
        <Rotulo htmlFor="entrada-custo">Custo unitário (R$)</Rotulo>
        <input id="entrada-custo" type="number" inputMode="decimal" step="0.0001" min="0" required className={CAMPO}
               value={custoUnitario} onChange={(evento) => setCustoUnitario(evento.target.value)} />
      </div>
      <div className="lg:col-span-2">
        <Rotulo htmlFor="entrada-fornecedor">Fornecedor</Rotulo>
        <select id="entrada-fornecedor" className={CAMPO} value={fornecedorId}
                onChange={(evento) => setFornecedorId(evento.target.value)}>
          <option value="">Não informado</option>
          {fornecedores.map((fornecedor) => (
            <option key={fornecedor.id} value={fornecedor.id}>{fornecedor.razaoSocial}</option>
          ))}
        </select>
      </div>
      <div>
        <Rotulo htmlFor="entrada-documento" dica="opcional">NF-e</Rotulo>
        <input id="entrada-documento" className={CAMPO} value={documento}
               onChange={(evento) => setDocumento(evento.target.value)} placeholder="Ex.: NF-e 10021" />
      </div>
      <div className="flex items-end">
        <Button type="submit" className="h-11 w-full text-sm">
          {icone("bi-box-arrow-in-down")}
          Registrar entrada
        </Button>
      </div>
    </form>
  );
}

/** RF09: saida com motivo obrigatorio; o saldo negativo e barrado pela RN002. */
function FormularioDeSaida({
  produtos,
  clientes,
  motivos,
  clienteInicial,
  aoRegistrar,
}: {
  produtos: Produto[];
  clientes: Cliente[];
  motivos: Opcao[];
  clienteInicial: number | null;
  aoRegistrar: Registrar;
}) {
  const [produtoId, setProdutoId] = useState("");
  const [quantidade, setQuantidade] = useState("");
  const [motivo, setMotivo] = useState("");
  const [clienteId, setClienteId] = useState(clienteInicial ? String(clienteInicial) : "");
  const [documento, setDocumento] = useState("");
  const particulas = useRef<ControleDeParticulas>(null);
  const produto = produtos.find((item) => String(item.id) === produtoId);
  const excedeSaldo = produto !== undefined && Number(quantidade) > Number(produto.saldo);

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    if (!produto) return;
    const cliente = clientes.find((item) => String(item.id) === clienteId);
    const descricaoDoMotivo = motivos.find((item) => item.valor === motivo)?.descricao ?? "Saída";
    const enviado = await aoRegistrar(
      "saida",
      "/movimentacoes/saida",
      {
        produtoId: Number(produtoId),
        quantidade,
        motivo,
        clienteId: clienteId ? Number(clienteId) : null,
        documento: documento || null,
      },
      {
        de: {
          rotulo: "De",
          titulo: `${produto.sku} · ${produto.descricao}`,
          detalhe: `saldo ${formatarQuantidade(produto.saldo)} ${produto.unidade}`,
          simbolo: icone("bi-box-seam"),
        },
        para: {
          rotulo: "Para",
          titulo: cliente?.nome ?? descricaoDoMotivo,
          detalhe: [
            documento,
            `−${formatarQuantidade(quantidade)} ${produto.unidade}`,
            formatarMoeda(Number(quantidade) * Number(produto.precoVenda)),
          ]
            .filter(Boolean)
            .join(" · "),
          simbolo: icone(motivo === "ORDEM_SERVICO" ? "bi-wrench" : cliente ? "bi-person" : "bi-shop"),
        },
      },
      "Saída registrada e saldo atualizado.",
      produto.unidade,
    );
    if (enviado) {
      particulas.current?.explodir();
      setQuantidade("");
      setDocumento("");
    }
  }

  return (
    <form className="grid gap-5 md:grid-cols-2 lg:grid-cols-4" onSubmit={enviar}>
      <div className="lg:col-span-2">
        <Rotulo htmlFor="saida-produto">Produto</Rotulo>
        <select id="saida-produto" data-primeiro-campo required className={CAMPO} value={produtoId}
                onChange={(evento) => setProdutoId(evento.target.value)}>
          <OpcoesDeProduto produtos={produtos} comSaldo />
        </select>
        {produto && (
          <Dica>
            disponível {formatarQuantidade(produto.saldo)} {produto.unidade} · venda {formatarMoeda(produto.precoVenda)}
          </Dica>
        )}
      </div>
      <div>
        <Rotulo htmlFor="saida-quantidade" dica={produto?.unidade}>Quantidade</Rotulo>
        <input id="saida-quantidade" type="number" inputMode="decimal" step="0.001" min="0.001" required
               aria-invalid={excedeSaldo || undefined} aria-describedby={excedeSaldo ? "saida-excede" : undefined}
               className={CAMPO} value={quantidade} onChange={(evento) => setQuantidade(evento.target.value)} />
        {excedeSaldo && (
          <p id="saida-excede" className="mt-1.5 text-xs text-atencao-texto">
            Maior que o saldo: a API vai recusar (RN002).
          </p>
        )}
      </div>
      <div>
        <Rotulo htmlFor="saida-motivo">Motivo</Rotulo>
        <select id="saida-motivo" required className={CAMPO} value={motivo}
                onChange={(evento) => setMotivo(evento.target.value)}>
          <option value="">Selecione</option>
          {motivos.map((opcao) => (
            <option key={opcao.valor} value={opcao.valor}>{opcao.descricao}</option>
          ))}
        </select>
      </div>
      <div className="lg:col-span-2">
        <Rotulo htmlFor="saida-cliente" dica="opcional">Cliente</Rotulo>
        <select id="saida-cliente" className={CAMPO} value={clienteId}
                onChange={(evento) => setClienteId(evento.target.value)}>
          <option value="">Não informado</option>
          {clientes.map((cliente) => (
            <option key={cliente.id} value={cliente.id}>{cliente.nome}</option>
          ))}
        </select>
      </div>
      <div>
        <Rotulo htmlFor="saida-documento" dica="OS ou pedido">Documento</Rotulo>
        <input id="saida-documento" className={CAMPO} value={documento}
               onChange={(evento) => setDocumento(evento.target.value)} placeholder="Ex.: OS 458" />
      </div>
      <div className="flex items-end">
        {/* Baixa no estoque e acao critica: o unico lugar do formulario em carmesim. */}
        <ParticleButton type="submit" controle={particulas} className="h-11 w-full text-sm">
          {icone("bi-box-arrow-up")}
          Dar baixa no estoque
        </ParticleButton>
      </div>
    </form>
  );
}

/** RF11 e RN006: ajuste apos contagem, com justificativa de no minimo dez caracteres. */
function FormularioDeAjuste({ produtos, aoRegistrar }: { produtos: Produto[]; aoRegistrar: Registrar }) {
  const [produtoId, setProdutoId] = useState("");
  const [saldoApurado, setSaldoApurado] = useState("");
  const [justificativa, setJustificativa] = useState("");
  const produto = produtos.find((item) => String(item.id) === produtoId);
  const diferenca = produto && saldoApurado !== "" ? Number(saldoApurado) - Number(produto.saldo) : null;

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    if (!produto) return;
    const enviado = await aoRegistrar(
      "ajuste",
      "/movimentacoes/ajuste",
      { produtoId: Number(produtoId), saldoApurado, justificativa },
      {
        de: {
          rotulo: "Sistema",
          titulo: `${produto.sku} · ${produto.descricao}`,
          detalhe: `saldo ${formatarQuantidade(produto.saldo)} ${produto.unidade}`,
          simbolo: icone("bi-cpu"),
        },
        para: {
          rotulo: "Contagem",
          titulo: `${formatarQuantidade(saldoApurado)} ${produto.unidade}`,
          detalhe: justificativa,
          simbolo: icone("bi-clipboard-check"),
        },
      },
      "Ajuste de inventário registrado.",
      produto.unidade,
    );
    if (enviado) {
      setSaldoApurado("");
      setJustificativa("");
    }
  }

  return (
    <form className="grid gap-5 md:grid-cols-2" onSubmit={enviar}>
      <div>
        <Rotulo htmlFor="ajuste-produto">Produto</Rotulo>
        <select id="ajuste-produto" data-primeiro-campo required className={CAMPO} value={produtoId}
                onChange={(evento) => setProdutoId(evento.target.value)}>
          <OpcoesDeProduto produtos={produtos} comSaldo />
        </select>
      </div>
      <div>
        <Rotulo htmlFor="ajuste-saldo" dica={produto?.unidade}>Saldo apurado na contagem</Rotulo>
        <input id="ajuste-saldo" type="number" inputMode="decimal" step="0.001" min="0" required className={CAMPO}
               value={saldoApurado} onChange={(evento) => setSaldoApurado(evento.target.value)} />
        {produto && diferenca !== null && (
          <Dica tom={diferenca !== 0 ? "atencao" : undefined}>
            sistema {formatarQuantidade(produto.saldo)} · diferença {diferenca > 0 ? "+" : diferenca < 0 ? "−" : ""}
            {formatarQuantidade(Math.abs(diferenca))} {produto.unidade}
          </Dica>
        )}
      </div>
      <div className="md:col-span-2">
        <Rotulo htmlFor="ajuste-justificativa" dica="mínimo de 10 caracteres (RN006)">Justificativa</Rotulo>
        <textarea id="ajuste-justificativa" required minLength={10} rows={2} className={AREA_DE_TEXTO}
                  value={justificativa} onChange={(evento) => setJustificativa(evento.target.value)}
                  placeholder="Descreva o motivo da divergência apurada na contagem" />
      </div>
      <div>
        <Button type="submit" className="h-11 px-5 text-sm">
          {icone("bi-sliders")}
          Registrar ajuste
        </Button>
      </div>
    </form>
  );
}

const FILTROS_DE_TIPO: { tipo: Movimentacao["tipo"]; rotulo: string }[] = [
  { tipo: "ENTRADA", rotulo: "Entradas" },
  { tipo: "SAIDA", rotulo: "Saídas" },
  { tipo: "AJUSTE", rotulo: "Ajustes" },
  { tipo: "ESTORNO", rotulo: "Estornos" },
];

function Historico({
  movimentacoes,
  carregado,
  gerenciaEstoque,
  documentoInicial,
  aoEstornar,
}: {
  movimentacoes: Movimentacao[];
  carregado: boolean;
  gerenciaEstoque: boolean;
  documentoInicial: string | null;
  aoEstornar: (movimentacao: Movimentacao, justificativa: string) => Promise<string | null>;
}) {
  const [tipo, setTipo] = useState<Movimentacao["tipo"] | null>(null);
  const [documento, setDocumento] = useState<string | null>(documentoInicial);
  const [estornando, setEstornando] = useState<number | null>(null);

  const visiveis = movimentacoes.filter(
    (movimentacao) =>
      (!tipo || movimentacao.tipo === tipo) &&
      (!documento || movimentacao.documento?.trim().toUpperCase() === documento.toUpperCase()),
  );
  const colunas = gerenciaEstoque ? 9 : 8;

  function fecharEstorno(id: number) {
    setEstornando(null);
    // Depois de um estorno o botao da linha some; o foco vai para o titulo do historico.
    requestAnimationFrame(() =>
      (
        document.querySelector<HTMLElement>(`[aria-controls="estorno-${id}"]`) ??
        document.getElementById("titulo-historico")
      )?.focus(),
    );
  }

  return (
    <section aria-labelledby="titulo-historico" className="mt-16 md:mt-24">
      <div className="mb-5">
        <p className="rotulo">RN008 · lançamentos imutáveis</p>
        <h2 id="titulo-historico" tabIndex={-1} className="fonte-display mt-2 text-2xl outline-none sm:text-3xl">
          Histórico recente
        </h2>
        <p className="mt-2 text-sm text-muted-foreground">As 50 movimentações mais recentes da oficina.</p>
      </div>

      <div role="group" aria-label="Filtrar o histórico" className="mb-5 flex flex-wrap items-center gap-2">
        <Chip ativo={!tipo} aoAlternar={() => setTipo(null)}>
          Todos
        </Chip>
        {FILTROS_DE_TIPO.map((filtro) => (
          <Chip
            key={filtro.tipo}
            ativo={tipo === filtro.tipo}
            aoAlternar={() => setTipo((atual) => (atual === filtro.tipo ? null : filtro.tipo))}
          >
            {filtro.rotulo}
          </Chip>
        ))}
        {documento && (
          <>
            <span aria-hidden="true" className="mx-1 h-5 w-px bg-border" />
            <Chip ativo aoAlternar={() => setDocumento(null)}>
              {icone("bi-file-earmark-text")}
              <span className="num">{documento}</span>
              <span className="sr-only">(remover filtro)</span>
              <i aria-hidden="true" className="bi bi-x-lg text-[0.6rem]" />
            </Chip>
          </>
        )}
      </div>

      <div className="overflow-hidden rounded-2xl border bg-card/90">
        {!carregado ? (
          <div aria-busy="true" aria-label="Carregando o histórico">
            <div className="esqueleto h-10 rounded-none" />
            {Array.from({ length: 6 }, (_, indice) => (
              <div key={indice} className="flex items-center gap-6 border-t px-4 py-3.5">
                <div className="esqueleto h-3 w-28" />
                <div className="esqueleto h-3 flex-1" />
                <div className="esqueleto h-3 w-16" />
              </div>
            ))}
          </div>
        ) : visiveis.length === 0 ? (
          <EstadoVazio ilustracao="prancheta" titulo="Nada por aqui">
            {movimentacoes.length === 0
              ? "Nenhuma movimentação registrada ainda."
              : "Nenhuma movimentação recente combina com o filtro."}
          </EstadoVazio>
        ) : (
          <div className="tabela-rolagem">
            <table className="tabela">
              <caption className="sr-only">Movimentações recentes, da mais nova para a mais antiga</caption>
              <thead>
                <tr>
                  <th>Data</th>
                  <th>Produto</th>
                  <th>Tipo</th>
                  <th>Motivo</th>
                  <th className="direita">Quantidade</th>
                  <th className="direita">Saldo</th>
                  <th>Documento</th>
                  <th>Usuário</th>
                  {gerenciaEstoque && <th>Estorno</th>}
                </tr>
              </thead>
              <tbody>
                {visiveis.map((movimentacao) => (
                  <LinhaDoHistorico
                    key={movimentacao.id}
                    movimentacao={movimentacao}
                    gerenciaEstoque={gerenciaEstoque}
                    colunas={colunas}
                    estornando={estornando === movimentacao.id}
                    aoAbrirEstorno={() =>
                      setEstornando((atual) => (atual === movimentacao.id ? null : movimentacao.id))
                    }
                    aoFecharEstorno={() => fecharEstorno(movimentacao.id)}
                    aoEstornar={aoEstornar}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}

function LinhaDoHistorico({
  movimentacao,
  gerenciaEstoque,
  colunas,
  estornando,
  aoAbrirEstorno,
  aoFecharEstorno,
  aoEstornar,
}: {
  movimentacao: Movimentacao;
  gerenciaEstoque: boolean;
  colunas: number;
  estornando: boolean;
  aoAbrirEstorno: () => void;
  aoFecharEstorno: () => void;
  aoEstornar: (movimentacao: Movimentacao, justificativa: string) => Promise<string | null>;
}) {
  return (
    <>
      <tr className={cn(movimentacao.estornada && "text-muted-foreground line-through")}>
        <td className="num whitespace-nowrap text-muted-foreground">{formatarDataHora(movimentacao.dataHora)}</td>
        <td>
          <span className="num mr-2 text-xs text-muted-foreground">{movimentacao.produtoSku}</span>
          {movimentacao.produtoDescricao}
        </td>
        <td>
          <TipoDeMovimentacao movimentacao={movimentacao} />
        </td>
        <td>{movimentacao.motivo}</td>
        <td className="direita num">{formatarQuantidade(movimentacao.quantidade)}</td>
        <td className="direita num">{formatarQuantidade(movimentacao.saldoResultante)}</td>
        <td className="num text-xs">{movimentacao.documento ?? "—"}</td>
        <td className="whitespace-nowrap">{movimentacao.usuario}</td>
        {gerenciaEstoque && (
          <td>
            {movimentacao.estornada ? (
              <span className="inline-block rounded-full border px-2 py-0.5 text-[0.6875rem] text-muted-foreground">
                Estornada
              </span>
            ) : movimentacao.estornavel ? (
              <button
                type="button"
                onClick={aoAbrirEstorno}
                aria-expanded={estornando}
                aria-controls={`estorno-${movimentacao.id}`}
                className="inline-flex h-8 items-center gap-1.5 rounded-lg border px-3 text-xs text-muted-foreground transition-colors hover:border-foreground/25 hover:text-foreground"
              >
                {icone("bi-arrow-counterclockwise")}
                Estornar
              </button>
            ) : (
              <span className="text-muted-foreground">—</span>
            )}
          </td>
        )}
      </tr>
      {estornando && (
        <LinhaDeEstorno
          movimentacao={movimentacao}
          colunas={colunas}
          aoFechar={aoFecharEstorno}
          aoEstornar={aoEstornar}
        />
      )}
    </>
  );
}

/** RN008: correcao por estorno, sempre com justificativa. */
function LinhaDeEstorno({
  movimentacao,
  colunas,
  aoFechar,
  aoEstornar,
}: {
  movimentacao: Movimentacao;
  colunas: number;
  aoFechar: () => void;
  aoEstornar: (movimentacao: Movimentacao, justificativa: string) => Promise<string | null>;
}) {
  const [justificativa, setJustificativa] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const particulas = useRef<ControleDeParticulas>(null);
  const campo = `justificativa-estorno-${movimentacao.id}`;

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    setEnviando(true);
    setErro(null);
    const falha = await aoEstornar(movimentacao, justificativa);
    if (falha) {
      setErro(falha);
      setEnviando(false);
      return;
    }
    particulas.current?.explodir();
    window.setTimeout(aoFechar, 900);
  }

  return (
    <tr id={`estorno-${movimentacao.id}`} className="!bg-sinal/[0.07]">
      <td colSpan={colunas}>
        <form onSubmit={enviar} className="flex flex-col gap-3 py-2 md:flex-row md:items-end">
          <div className="flex-1">
            <Rotulo htmlFor={campo} dica="mínimo de 10 caracteres">
              Estornar {movimentacao.tipoDescricao.toLowerCase()} de {movimentacao.produtoSku}
            </Rotulo>
            <input
              id={campo}
              required
              minLength={10}
              autoFocus
              value={justificativa}
              onChange={(evento) => setJustificativa(evento.target.value)}
              placeholder="Ex.: saída lançada em duplicidade"
              className={CAMPO}
            />
            {erro && (
              <p role="alert" className="mt-2 text-xs text-sinal-texto">
                {erro}
              </p>
            )}
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={aoFechar}
              className="h-11 rounded-lg border px-4 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
            >
              Cancelar
            </button>
            <ParticleButton type="submit" controle={particulas} disabled={enviando} className="h-11 px-4 text-sm">
              {icone("bi-arrow-counterclockwise")}
              {enviando ? "Estornando…" : "Confirmar estorno"}
            </ParticleButton>
          </div>
        </form>
      </td>
    </tr>
  );
}
