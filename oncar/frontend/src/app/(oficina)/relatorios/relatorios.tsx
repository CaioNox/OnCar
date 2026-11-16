"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Erro } from "@/components/oncar/avisos";
import { CAMPO, Rotulo } from "@/components/oncar/campo";
import { useUsuario } from "@/components/oncar/contexto";
import { EstadoVazio } from "@/components/oncar/estado-vazio";
import { GraficoReceitaDespesa } from "@/components/oncar/grafico-receita-despesa";
import { api, ErroDaApi, formatarData, formatarMes, formatarMoeda, formatarQuantidade } from "@/lib/api";
import {
  podeEmitirRelatorios,
  podeVerFinanceiro,
  type FiltrosDeRelatorio,
  type RelatorioEntradas,
  type RelatorioGastos,
  type RelatorioPosicao,
  type RelatorioSaidas,
  type TotalPorGrupo,
} from "@/lib/tipos";
import { cn } from "@/lib/utils";

export type TipoDeRelatorio = "entradas" | "saidas" | "gastos" | "posicao";

type Resultado =
  | { tipo: "entradas"; dados: RelatorioEntradas }
  | { tipo: "saidas"; dados: RelatorioSaidas }
  | { tipo: "gastos"; dados: RelatorioGastos }
  | { tipo: "posicao"; dados: RelatorioPosicao };

const ABAS: { chave: TipoDeRelatorio; rotulo: string; icone: string; soProprietario?: boolean }[] = [
  { chave: "entradas", rotulo: "Entradas", icone: "bi-box-arrow-in-down" },
  { chave: "saidas", rotulo: "Saídas", icone: "bi-box-arrow-up" },
  { chave: "gastos", rotulo: "Gastos e resultado", icone: "bi-cash-coin", soProprietario: true },
  { chave: "posicao", rotulo: "Posição de estoque", icone: "bi-boxes" },
];

export function Relatorios({
  tipoInicial,
  inicioInicial,
  fimInicial,
}: {
  tipoInicial: TipoDeRelatorio;
  inicioInicial: string;
  fimInicial: string;
}) {
  const usuario = useUsuario();
  const router = useRouter();
  const autorizado = podeEmitirRelatorios(usuario);
  const abas = ABAS.filter((aba) => !aba.soProprietario || podeVerFinanceiro(usuario));

  const [tipo, setTipo] = useState<TipoDeRelatorio>(() =>
    abas.some((aba) => aba.chave === tipoInicial) ? tipoInicial : "entradas",
  );
  const [inicio, setInicio] = useState(inicioInicial);
  const [fim, setFim] = useState(fimInicial);
  const [categoria, setCategoria] = useState("");
  const [fornecedorId, setFornecedorId] = useState("");
  const [usuarioId, setUsuarioId] = useState("");
  const [filtros, setFiltros] = useState<FiltrosDeRelatorio | null>(null);
  const [resultado, setResultado] = useState<Resultado | null>(null);
  // consulta cuja resposta (ou erro) ja chegou; enquanto difere da atual, a tela esta carregando
  const [consultaRespondida, setConsultaRespondida] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  const parametros = new URLSearchParams({ inicio, fim });
  if (categoria) parametros.set("categoria", categoria);
  if (fornecedorId) parametros.set("fornecedorId", fornecedorId);
  if (usuarioId && tipo !== "posicao") parametros.set("usuarioId", usuarioId);
  const consulta = `/relatorios/${tipo}?${parametros}`;
  const carregando = consultaRespondida !== consulta;

  useEffect(() => {
    if (!autorizado) return;
    api.get<FiltrosDeRelatorio>("/relatorios/filtros").then(setFiltros).catch(() => undefined);
  }, [autorizado]);

  useEffect(() => {
    if (!autorizado) return;
    // so a resposta mais recente vale: trocas rapidas de filtro nao embaralham a tela
    let atual = true;
    const tipoConsultado = consulta.slice("/relatorios/".length).split("?")[0] as TipoDeRelatorio;
    api
      .get<Resultado["dados"]>(consulta)
      .then((dados) => {
        if (!atual) return;
        setResultado({ tipo: tipoConsultado, dados } as Resultado);
        setErro(null);
      })
      .catch((excecao) => {
        if (!atual) return;
        setErro(excecao instanceof ErroDaApi ? excecao.message : "Não foi possível gerar o relatório. Tente novamente.");
      })
      .finally(() => atual && setConsultaRespondida(consulta));
    return () => {
      atual = false;
    };
  }, [autorizado, consulta]);

  // o endereco acompanha a aba e o periodo, para recarregar ou compartilhar o mesmo relatorio
  useEffect(() => {
    if (autorizado) router.replace(`/relatorios?tipo=${tipo}&inicio=${inicio}&fim=${fim}`, { scroll: false });
  }, [autorizado, tipo, inicio, fim, router]);

  if (!autorizado) {
    return (
      <EstadoVazio ilustracao="engrenagem" titulo="Relatórios da gerência" className="py-24">
        Os relatórios ficam disponíveis para o proprietário e o gerente de estoque.
      </EstadoVazio>
    );
  }

  function trocarTipo(novo: TipoDeRelatorio) {
    // categorias de produto e de despesa sao listas diferentes
    if ((novo === "gastos") !== (tipo === "gastos")) setCategoria("");
    setTipo(novo);
  }

  const categorias = tipo === "gastos" ? filtros?.categoriasDeDespesa : filtros?.categoriasDeProduto;
  const periodo = inicio === fim ? formatarMes(inicio) : `${formatarMes(inicio)} a ${formatarMes(fim).toLowerCase()}`;

  return (
    <section aria-labelledby="titulo-relatorios">
      <header className="mb-10 md:mb-14">
        <p className="rotulo">Gestão</p>
        <h1 id="titulo-relatorios" className="fonte-display mt-3 text-display">
          Relatórios
        </h1>
        <p className="mt-4 max-w-2xl text-muted-foreground">
          Valores apurados pelo mês de competência de cada lançamento. Lançamentos estornados não entram
          nos totais.
        </p>
      </header>

      <Abas abas={abas} ativa={tipo} aoMudar={trocarTipo} />

      <div role="group" aria-label="Filtros do relatório" className="mt-6 mb-8 flex flex-wrap items-end gap-4">
        <div>
          <Rotulo htmlFor="relatorio-inicio">De</Rotulo>
          <input id="relatorio-inicio" type="month" className={cn(CAMPO, "w-44")} value={inicio} max={fim}
                 onChange={(evento) => evento.target.value && setInicio(evento.target.value)} />
        </div>
        <div>
          <Rotulo htmlFor="relatorio-fim">Até</Rotulo>
          <input id="relatorio-fim" type="month" className={cn(CAMPO, "w-44")} value={fim} min={inicio}
                 onChange={(evento) => evento.target.value && setFim(evento.target.value)} />
        </div>
        <div>
          <Rotulo htmlFor="relatorio-categoria">Categoria</Rotulo>
          <select id="relatorio-categoria" className={cn(CAMPO, "w-48")} value={categoria}
                  onChange={(evento) => setCategoria(evento.target.value)}>
            <option value="">Todas</option>
            {categorias?.map((opcao) => (
              <option key={opcao.valor} value={opcao.valor}>
                {opcao.descricao}
              </option>
            ))}
          </select>
        </div>
        <div>
          <Rotulo htmlFor="relatorio-fornecedor">Fornecedor</Rotulo>
          <select id="relatorio-fornecedor" className={cn(CAMPO, "w-56")} value={fornecedorId}
                  onChange={(evento) => setFornecedorId(evento.target.value)}>
            <option value="">Todos</option>
            {filtros?.fornecedores.map((fornecedor) => (
              <option key={fornecedor.id} value={fornecedor.id}>
                {fornecedor.razaoSocial}
                {fornecedor.ativo ? "" : " (inativo)"}
              </option>
            ))}
          </select>
        </div>
        {tipo !== "posicao" && (
          <div>
            <Rotulo htmlFor="relatorio-usuario">Lançado por</Rotulo>
            <select id="relatorio-usuario" className={cn(CAMPO, "w-48")} value={usuarioId}
                    onChange={(evento) => setUsuarioId(evento.target.value)}>
              <option value="">Todos</option>
              {filtros?.usuarios.map((opcao) => (
                <option key={opcao.valor} value={opcao.valor}>
                  {opcao.descricao}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      <Erro mensagem={erro} />

      <div
        id={`painel-${tipo}`}
        role="tabpanel"
        aria-labelledby={`aba-${tipo}`}
        aria-busy={carregando}
        // ao refazer a consulta a tela anterior fica esmaecida, sem pular nem piscar
        className={cn("transition-opacity", carregando && resultado && "opacity-55")}
      >
        <p className="rotulo mb-4">{periodo}</p>
        {!resultado ? (
          <EsqueletoDoRelatorio />
        ) : resultado.tipo === "entradas" ? (
          <Entradas dados={resultado.dados} />
        ) : resultado.tipo === "saidas" ? (
          <Saidas dados={resultado.dados} />
        ) : resultado.tipo === "gastos" ? (
          <Gastos dados={resultado.dados} />
        ) : (
          <Posicao dados={resultado.dados} />
        )}
      </div>
    </section>
  );
}

/** Abas com setas do teclado (tabindex movel), no padrao ARIA de tabs. */
function Abas({
  abas,
  ativa,
  aoMudar,
}: {
  abas: typeof ABAS;
  ativa: TipoDeRelatorio;
  aoMudar: (tipo: TipoDeRelatorio) => void;
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
    <div role="tablist" aria-label="Relatório" className="inline-flex max-w-full flex-wrap gap-1 rounded-2xl border bg-background/40 p-1 sm:rounded-full">
      {abas.map((aba, indice) => (
        <button
          key={aba.chave}
          ref={(elemento) => {
            botoes.current[indice] = elemento;
          }}
          id={`aba-${aba.chave}`}
          role="tab"
          type="button"
          aria-selected={ativa === aba.chave}
          aria-controls={`painel-${aba.chave}`}
          tabIndex={ativa === aba.chave ? 0 : -1}
          onClick={() => aoMudar(aba.chave)}
          onKeyDown={(evento) => aoTeclar(evento, indice)}
          className={cn(
            "flex h-9 items-center gap-2 rounded-full px-4 text-sm transition-colors",
            ativa === aba.chave ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground",
          )}
        >
          <i aria-hidden="true" className={cn("bi", aba.icone)} />
          {aba.rotulo}
        </button>
      ))}
    </div>
  );
}

function Indicador({ rotulo, valor, detalhe, tom }: { rotulo: string; valor: string; detalhe?: string; tom?: "negativo" }) {
  return (
    <div className="rounded-2xl border bg-card/85 p-5 backdrop-blur-md">
      <p className="text-xs text-muted-foreground">{rotulo}</p>
      <p className={cn("mt-1.5 text-2xl font-semibold", tom === "negativo" && "text-sinal-texto")}>{valor}</p>
      {detalhe && <p className="mt-1 text-xs text-muted-foreground">{detalhe}</p>}
    </div>
  );
}

function Grupos({ titulo, grupos, vazio = "Nada no período." }: { titulo: string; grupos: TotalPorGrupo[]; vazio?: string }) {
  return (
    <div className="rounded-2xl border bg-card/85 p-5 backdrop-blur-md">
      <p className="rotulo">{titulo}</p>
      {grupos.length === 0 ? (
        <p className="mt-3 text-sm text-muted-foreground">{vazio}</p>
      ) : (
        <ul className="mt-3 divide-y text-sm">
          {grupos.map((grupo) => (
            <li key={grupo.grupo} className="flex items-baseline justify-between gap-4 py-2">
              <span className="min-w-0">
                {grupo.grupo}
                <span className="ml-1.5 text-xs text-muted-foreground">({grupo.lancamentos})</span>
              </span>
              <span className="num shrink-0">{formatarMoeda(grupo.valor)}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Quadro({ children, vazio, legenda }: { children: React.ReactNode; vazio: string | null; legenda?: string }) {
  return (
    <div className="mt-6">
      <div className="overflow-hidden rounded-2xl border bg-card/90">
        {vazio ? (
          <EstadoVazio ilustracao="lupa" titulo="Nada encontrado">
            {vazio}
          </EstadoVazio>
        ) : (
          <div className="tabela-rolagem">{children}</div>
        )}
      </div>
      {legenda && <p className="mt-2 text-xs text-muted-foreground">{legenda}</p>}
    </div>
  );
}

function LinkDoProduto({ id, sku, nome, categoria }: { id: number; sku: string; nome: string; categoria?: string }) {
  return (
    <Link href={`/produtos/${id}`} className="hover:underline">
      <span className="num mr-2 text-xs text-muted-foreground">{sku}</span>
      {nome}
      {categoria && <span className="block text-xs text-muted-foreground">{categoria}</span>}
    </Link>
  );
}

function Entradas({ dados }: { dados: RelatorioEntradas }) {
  return (
    <>
      <div className="grid gap-4 md:grid-cols-3">
        <Indicador rotulo="Valor recebido" valor={formatarMoeda(dados.valorTotal)}
                   detalhe={`${dados.linhas.length} ${dados.linhas.length === 1 ? "entrada" : "entradas"} a custo de compra`} />
        <Grupos titulo="Por fornecedor" grupos={dados.porFornecedor} />
        <Grupos titulo="Por categoria" grupos={dados.porCategoria} />
      </div>
      <Quadro vazio={dados.linhas.length ? null : "Nenhuma entrada no período com estes filtros."}>
        <table className="tabela">
          <caption className="sr-only">Entradas de mercadoria do período</caption>
          <thead>
            <tr>
              <th>Competência</th>
              <th>Produto</th>
              <th>Fornecedor</th>
              <th>Documento</th>
              <th className="direita">Quantidade</th>
              <th className="direita">Custo unitário</th>
              <th className="direita">Valor</th>
              <th>Usuário</th>
            </tr>
          </thead>
          <tbody>
            {dados.linhas.map((linha) => (
              <tr key={linha.movimentacaoId}>
                <td className="num whitespace-nowrap text-muted-foreground">{formatarData(linha.competencia)}</td>
                <td><LinkDoProduto id={linha.produtoId} sku={linha.sku} nome={linha.produto} categoria={linha.categoria} /></td>
                <td>{linha.fornecedor ?? "-"}</td>
                <td className="num">{linha.documento ?? "-"}</td>
                <td className="direita num whitespace-nowrap">{formatarQuantidade(linha.quantidade)} {linha.unidade}</td>
                <td className="direita num">{formatarMoeda(linha.custoUnitario)}</td>
                <td className="direita num">{formatarMoeda(linha.valorTotal)}</td>
                <td className="whitespace-nowrap">{linha.usuario}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={6} className="font-medium">Total</td>
              <td className="direita num font-medium">{formatarMoeda(dados.valorTotal)}</td>
              <td />
            </tr>
          </tfoot>
        </table>
      </Quadro>
    </>
  );
}

function Saidas({ dados }: { dados: RelatorioSaidas }) {
  const margem = Number(dados.margemBruta);
  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Indicador rotulo="Receita (vendas e OS)" valor={formatarMoeda(dados.receita)} />
        <Indicador rotulo="Custo das peças vendidas" valor={formatarMoeda(dados.custoDasVendas)} detalhe="pelo custo médio de cada saída" />
        <Indicador rotulo="Margem bruta" valor={formatarMoeda(margem)} tom={margem < 0 ? "negativo" : undefined} />
        <Indicador rotulo="Custo de todas as saídas" valor={formatarMoeda(dados.custoTotal)} detalhe="inclui devoluções e perdas" />
      </div>
      <div className="mt-4 grid gap-4 md:grid-cols-2">
        <Grupos titulo="Por motivo, a custo médio" grupos={dados.porMotivo} />
        <Grupos titulo="Por categoria, a custo médio" grupos={dados.porCategoria} />
      </div>
      <Quadro
        vazio={dados.linhas.length ? null : "Nenhuma saída no período com estes filtros."}
        legenda="O filtro de fornecedor considera o fornecedor padrão de cada peça."
      >
        <table className="tabela">
          <caption className="sr-only">Saídas de estoque do período</caption>
          <thead>
            <tr>
              <th>Competência</th>
              <th>Produto</th>
              <th>Motivo</th>
              <th>Cliente</th>
              <th>Documento</th>
              <th className="direita">Quantidade</th>
              <th className="direita">Custo</th>
              <th className="direita">Valor de venda</th>
              <th>Usuário</th>
            </tr>
          </thead>
          <tbody>
            {dados.linhas.map((linha) => (
              <tr key={linha.movimentacaoId}>
                <td className="num whitespace-nowrap text-muted-foreground">{formatarData(linha.competencia)}</td>
                <td><LinkDoProduto id={linha.produtoId} sku={linha.sku} nome={linha.produto} categoria={linha.categoria} /></td>
                <td className="whitespace-nowrap">{linha.motivo}</td>
                <td>{linha.cliente ?? "-"}</td>
                <td className="num">{linha.documento ?? "-"}</td>
                <td className="direita num whitespace-nowrap">{formatarQuantidade(linha.quantidade)} {linha.unidade}</td>
                <td className="direita num">{formatarMoeda(linha.custoTotal)}</td>
                <td className="direita num">{linha.valorVenda === null ? "-" : formatarMoeda(linha.valorVenda)}</td>
                <td className="whitespace-nowrap">{linha.usuario}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={6} className="font-medium">Total</td>
              <td className="direita num font-medium">{formatarMoeda(dados.custoTotal)}</td>
              <td className="direita num font-medium">{formatarMoeda(dados.receita)}</td>
              <td />
            </tr>
          </tfoot>
        </table>
      </Quadro>
    </>
  );
}

function Gastos({ dados }: { dados: RelatorioGastos }) {
  const total = dados.consolidado;
  const resultado = Number(total.resultado);
  return (
    <>
      <h2 className="fonte-display text-2xl">Resultado da oficina</h2>
      <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
        Sem os filtros de categoria, fornecedor e usuário. A despesa soma o custo médio das peças vendidas, as
        perdas e as despesas operacionais; compras de mercadoria viram estoque e não reduzem o resultado.
      </p>
      <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Indicador rotulo="Receita" valor={formatarMoeda(total.receita)} detalhe={`margem bruta ${formatarMoeda(total.margemBruta)}`} />
        <Indicador rotulo="Despesa" valor={formatarMoeda(total.despesaTotal)}
                   detalhe={`peças ${formatarMoeda(total.custoDasVendas)} · operacionais ${formatarMoeda(total.despesasOperacionais)}`} />
        <Indicador rotulo={resultado < 0 ? "Resultado (prejuízo)" : "Resultado (lucro)"} valor={formatarMoeda(resultado)}
                   tom={resultado < 0 ? "negativo" : undefined} />
        <Indicador rotulo="Compras de mercadoria" valor={formatarMoeda(total.compras)} detalhe="investimento em estoque" />
      </div>

      {dados.meses.length > 1 && (
        <div className="mt-4 rounded-2xl border bg-card/85 p-5 backdrop-blur-md">
          <p className="rotulo">Receita x despesa por mês</p>
          <GraficoReceitaDespesa meses={dados.meses} className="mt-4" />
        </div>
      )}

      <div className="mt-4 overflow-hidden rounded-2xl border bg-card/90">
        <div className="tabela-rolagem">
          <table className="tabela">
            <caption className="sr-only">Apuração mensal por competência</caption>
            <thead>
              <tr>
                <th>Mês</th>
                <th className="direita">Receita</th>
                <th className="direita">Custo das vendas</th>
                <th className="direita">Margem bruta</th>
                <th className="direita">Perdas</th>
                <th className="direita">Despesas operacionais</th>
                <th className="direita">Resultado</th>
                <th className="direita">Compras</th>
              </tr>
            </thead>
            <tbody>
              {dados.meses.map((mes) => (
                <tr key={mes.mes}>
                  <td className="whitespace-nowrap">{mes.mes ? formatarMes(mes.mes) : "-"}</td>
                  <td className="direita num">{formatarMoeda(mes.receita)}</td>
                  <td className="direita num">{formatarMoeda(mes.custoDasVendas)}</td>
                  <td className="direita num">{formatarMoeda(mes.margemBruta)}</td>
                  <td className="direita num">{formatarMoeda(mes.perdas)}</td>
                  <td className="direita num">{formatarMoeda(mes.despesasOperacionais)}</td>
                  <td className={cn("direita num font-medium", Number(mes.resultado) < 0 && "text-sinal-texto")}>
                    {formatarMoeda(mes.resultado)}
                  </td>
                  <td className="direita num">{formatarMoeda(mes.compras)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <h2 className="fonte-display mt-14 text-2xl">Gastos do período</h2>
      <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
        Com os filtros aplicados. Ao filtrar por categoria de despesa, as compras de mercadoria ficam de fora.{" "}
        <Link href="/despesas" className="text-foreground underline underline-offset-4">Lançar ou corrigir despesas</Link>
      </p>
      <div className="mt-5 grid gap-4 md:grid-cols-3">
        <Indicador rotulo="Total gasto" valor={formatarMoeda(dados.totalGasto)}
                   detalhe={`despesas ${formatarMoeda(dados.totalDespesas)} · compras ${formatarMoeda(dados.totalCompras)}`} />
        <Grupos titulo="Despesas por categoria" grupos={dados.despesasPorCategoria} vazio="Nenhuma despesa." />
        <Grupos titulo="Compras por fornecedor" grupos={dados.comprasPorFornecedor} vazio="Nenhuma compra." />
      </div>
      <Quadro vazio={dados.despesas.length ? null : "Nenhuma despesa no período com estes filtros."}>
        <table className="tabela">
          <caption className="sr-only">Despesas operacionais do período</caption>
          <thead>
            <tr>
              <th>Competência</th>
              <th>Despesa</th>
              <th>Categoria</th>
              <th>Fornecedor</th>
              <th>Documento</th>
              <th className="direita">Valor</th>
              <th>Lançada por</th>
            </tr>
          </thead>
          <tbody>
            {dados.despesas.map((linha) => (
              <tr key={linha.despesaId}>
                <td className="num whitespace-nowrap text-muted-foreground">{formatarData(linha.competencia)}</td>
                <td>{linha.descricao}</td>
                <td className="whitespace-nowrap">{linha.categoria}</td>
                <td>{linha.fornecedor ?? "-"}</td>
                <td className="num">{linha.documento ?? "-"}</td>
                <td className="direita num">{formatarMoeda(linha.valor)}</td>
                <td className="whitespace-nowrap">{linha.usuario}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Quadro>
    </>
  );
}

function variacao(valor: string): string {
  const numero = Number(valor);
  if (numero === 0) return "0";
  return `${numero > 0 ? "+" : "−"}${formatarQuantidade(Math.abs(numero))}`;
}

function Posicao({ dados }: { dados: RelatorioPosicao }) {
  return (
    <>
      <div className="grid gap-4 md:grid-cols-3">
        <Indicador rotulo="Valor em estoque no fechamento" valor={formatarMoeda(dados.valorTotal)}
                   detalhe="a custo médio vigente no fim do período" />
        <Indicador rotulo="Produtos na posição" valor={String(dados.linhas.length)} />
        <Indicador rotulo="No mínimo ou abaixo no fechamento" valor={String(dados.produtosAbaixoDoMinimo)} />
      </div>
      <Quadro
        vazio={dados.linhas.length ? null : "Nenhum produto com estes filtros."}
        legenda="Saldo físico pela data de cada lançamento: saldo inicial + entradas − saídas + ajustes e estornos = saldo final. O filtro de fornecedor considera o fornecedor padrão da peça."
      >
        <table className="tabela">
          <caption className="sr-only">Posição de estoque por produto no período</caption>
          <thead>
            <tr>
              <th>Produto</th>
              <th className="direita">Saldo inicial</th>
              <th className="direita">Entradas</th>
              <th className="direita">Saídas</th>
              <th className="direita">Ajustes e estornos</th>
              <th className="direita">Saldo final</th>
              <th className="direita">Mínimo</th>
              <th className="direita">Custo médio</th>
              <th className="direita">Valor final</th>
            </tr>
          </thead>
          <tbody>
            {dados.linhas.map((linha) => (
              <tr key={linha.produtoId} className={cn(!linha.ativo && "text-muted-foreground")}>
                <td>
                  <LinkDoProduto id={linha.produtoId} sku={linha.sku} nome={linha.produto}
                                 categoria={`${linha.categoria} · ${linha.unidade}${linha.ativo ? "" : " · inativo"}`} />
                </td>
                <td className="direita num">{formatarQuantidade(linha.saldoInicial)}</td>
                <td className="direita num">{formatarQuantidade(linha.entradas)}</td>
                <td className="direita num">{formatarQuantidade(linha.saidas)}</td>
                <td className="direita num">{variacao(linha.ajustes)}</td>
                <td className="direita num font-medium">
                  <span className="inline-flex items-center gap-1.5">
                    {linha.abaixoDoMinimo && (
                      <i role="img" aria-label="No mínimo ou abaixo" className="bi bi-exclamation-triangle-fill text-xs text-sinal-texto" />
                    )}
                    {formatarQuantidade(linha.saldoFinal)}
                  </span>
                </td>
                <td className="direita num">{formatarQuantidade(linha.estoqueMinimo)}</td>
                <td className="direita num">{formatarMoeda(linha.custoMedio)}</td>
                <td className="direita num">{formatarMoeda(linha.valorFinal)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={8} className="font-medium">Total</td>
              <td className="direita num font-medium">{formatarMoeda(dados.valorTotal)}</td>
            </tr>
          </tfoot>
        </table>
      </Quadro>
    </>
  );
}

function EsqueletoDoRelatorio() {
  return (
    <div aria-label="Gerando o relatório">
      <div className="grid gap-4 md:grid-cols-3">
        <div className="esqueleto h-28 rounded-2xl" />
        <div className="esqueleto h-28 rounded-2xl" />
        <div className="esqueleto h-28 rounded-2xl" />
      </div>
      <div className="esqueleto mt-6 h-72 rounded-2xl" />
    </div>
  );
}
