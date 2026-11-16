"use client";

import { useEffect, useMemo, useState } from "react";
import { Erro } from "@/components/oncar/avisos";
import { AREA_DE_TEXTO, CAMPO, Rotulo } from "@/components/oncar/campo";
import { useOficina, useUsuario } from "@/components/oncar/contexto";
import { Dialogo } from "@/components/oncar/dialogo";
import { EstadoVazio } from "@/components/oncar/estado-vazio";
import { useToast } from "@/components/oncar/toasts";
import { Button } from "@/components/ui/button";
import { api, ErroDaApi, formatarData, formatarMes, formatarMoeda } from "@/lib/api";
import { podeVerFinanceiro, type Despesa, type Fornecedor, type Opcao } from "@/lib/tipos";
import { cn } from "@/lib/utils";

type Formulario = {
  descricao: string;
  categoria: string;
  valor: string;
  competencia: string;
  fornecedorId: string;
  documento: string;
  observacao: string;
};

function hojeLocal(): string {
  const agora = new Date();
  return `${agora.getFullYear()}-${String(agora.getMonth() + 1).padStart(2, "0")}-${String(agora.getDate()).padStart(2, "0")}`;
}

function formularioDe(despesa: Despesa | null): Formulario {
  return {
    descricao: despesa?.descricao ?? "",
    categoria: despesa?.categoria ?? "",
    valor: despesa ? String(Number(despesa.valor)) : "",
    competencia: despesa?.competencia ?? hojeLocal(),
    fornecedorId: despesa?.fornecedorId ? String(despesa.fornecedorId) : "",
    documento: despesa?.documento ?? "",
    observacao: despesa?.observacao ?? "",
  };
}

function mensagemDe(excecao: unknown): { mensagem: string; detalhes: string[] } {
  return excecao instanceof ErroDaApi
    ? { mensagem: excecao.message, detalhes: excecao.detalhes }
    : { mensagem: "Não foi possível falar com o servidor. Tente novamente.", detalhes: [] };
}

export function Despesas({
  inicioInicial,
  fimInicial,
  abrirFormulario,
}: {
  inicioInicial: string;
  fimInicial: string;
  abrirFormulario: boolean;
}) {
  const usuario = useUsuario();
  const { recarregar } = useOficina();
  const avisar = useToast();
  const autorizado = podeVerFinanceiro(usuario);

  const [inicio, setInicio] = useState(inicioInicial);
  const [fim, setFim] = useState(fimInicial);
  const [despesas, setDespesas] = useState<Despesa[]>([]);
  const [carregado, setCarregado] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [categorias, setCategorias] = useState<Opcao[]>([]);
  const [fornecedores, setFornecedores] = useState<Fornecedor[]>([]);
  // null: fechado; "nova": lancamento; Despesa: edicao
  const [editando, setEditando] = useState<Despesa | "nova" | null>(abrirFormulario ? "nova" : null);
  const [excluindo, setExcluindo] = useState<Despesa | null>(null);
  // incrementada depois de salvar ou excluir, para buscar a lista de novo
  const [versao, setVersao] = useState(0);

  useEffect(() => {
    if (!autorizado) return;
    let ativo = true;
    api
      .get<Despesa[]>(`/despesas?inicio=${inicio}&fim=${fim}`)
      .then((lista) => {
        if (!ativo) return;
        setDespesas(lista);
        setErro(null);
      })
      .catch((excecao) => ativo && setErro(mensagemDe(excecao).mensagem))
      .finally(() => ativo && setCarregado(true));
    return () => {
      ativo = false;
    };
  }, [autorizado, inicio, fim, versao]);

  useEffect(() => {
    if (!autorizado) return;
    api.get<Opcao[]>("/despesas/categorias").then(setCategorias).catch(() => undefined);
    api.get<Fornecedor[]>("/fornecedores").then(setFornecedores).catch(() => undefined);
  }, [autorizado]);

  const total = useMemo(() => despesas.reduce((soma, despesa) => soma + Number(despesa.valor), 0), [despesas]);
  const porCategoria = useMemo(() => {
    const somas = new Map<string, number>();
    for (const despesa of despesas) {
      somas.set(despesa.categoriaDescricao, (somas.get(despesa.categoriaDescricao) ?? 0) + Number(despesa.valor));
    }
    return [...somas.entries()].sort((a, b) => b[1] - a[1]);
  }, [despesas]);

  if (!autorizado) {
    return (
      <EstadoVazio ilustracao="engrenagem" titulo="Área do proprietário" className="py-24">
        As despesas e o resultado da oficina ficam visíveis apenas para o proprietário.
      </EstadoVazio>
    );
  }

  async function aoSalvar() {
    setEditando(null);
    setVersao((atual) => atual + 1);
    // o painel recalcula receita x despesa e o wallpaper reage ao novo resultado
    void recarregar();
  }

  async function excluir(despesa: Despesa): Promise<string | null> {
    try {
      await api.delete(`/despesas/${despesa.id}`);
      avisar("Despesa excluída.");
      setExcluindo(null);
      setVersao((atual) => atual + 1);
      void recarregar();
      return null;
    } catch (excecao) {
      return mensagemDe(excecao).mensagem;
    }
  }

  const periodo = inicio === fim ? formatarMes(inicio) : `${formatarMes(inicio)} a ${formatarMes(fim).toLowerCase()}`;

  return (
    <section aria-labelledby="titulo-despesas">
      <header className="mb-10 flex flex-wrap items-end justify-between gap-6 md:mb-14">
        <div>
          <p className="rotulo">Financeiro</p>
          <h1 id="titulo-despesas" className="fonte-display mt-3 text-display">
            Despesas
          </h1>
          <p className="mt-4 max-w-2xl text-muted-foreground">
            Aluguel, energia, ferramentas e outros gastos da oficina. Cada despesa entra no mês de
            competência, o mês a que o gasto pertence, mesmo que seja paga depois.
          </p>
        </div>
        <Button type="button" className="h-11 px-5 text-sm" onClick={() => setEditando("nova")}>
          <i aria-hidden="true" className="bi bi-plus-lg" />
          Lançar despesa
        </Button>
      </header>

      <div role="group" aria-label="Período" className="mb-6 flex flex-wrap items-end gap-4">
        <div>
          <Rotulo htmlFor="despesas-inicio">De</Rotulo>
          <input
            id="despesas-inicio"
            type="month"
            className={cn(CAMPO, "w-44")}
            value={inicio}
            max={fim}
            onChange={(evento) => evento.target.value && setInicio(evento.target.value)}
          />
        </div>
        <div>
          <Rotulo htmlFor="despesas-fim">Até</Rotulo>
          <input
            id="despesas-fim"
            type="month"
            className={cn(CAMPO, "w-44")}
            value={fim}
            min={inicio}
            onChange={(evento) => evento.target.value && setFim(evento.target.value)}
          />
        </div>
      </div>

      <Erro mensagem={erro} />

      <div className="mb-6 grid gap-4 lg:grid-cols-12">
        <div className="rounded-3xl border bg-card/85 p-6 backdrop-blur-md lg:col-span-4">
          <p className="rotulo">{periodo}</p>
          <p className="mt-3 text-3xl font-semibold">{formatarMoeda(total)}</p>
          <p className="mt-1 text-sm text-muted-foreground">
            {despesas.length} {despesas.length === 1 ? "lançamento" : "lançamentos"}
          </p>
        </div>
        <div className="rounded-3xl border bg-card/85 p-6 backdrop-blur-md lg:col-span-8">
          <p className="rotulo">Por categoria</p>
          {porCategoria.length === 0 ? (
            <p className="mt-4 text-sm text-muted-foreground">Nenhuma despesa no período.</p>
          ) : (
            <ul className="mt-4 grid gap-3">
              {porCategoria.map(([categoria, valor]) => (
                <li key={categoria} className="grid grid-cols-[minmax(0,10rem)_1fr_auto] items-center gap-3 text-sm">
                  <span className="truncate">{categoria}</span>
                  <span aria-hidden="true" className="h-2 rounded-r-[4px] bg-grafico-despesa" style={{ width: `${(valor / total) * 100}%` }} />
                  <span className="num text-muted-foreground">
                    <span className="text-foreground">{formatarMoeda(valor)}</span> · {Math.round((valor / total) * 100)}%
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      <div className="overflow-hidden rounded-2xl border bg-card/90">
        {!carregado ? (
          <div aria-busy="true" aria-label="Carregando as despesas">
            <div className="esqueleto h-10 rounded-none" />
            {Array.from({ length: 4 }, (_, indice) => (
              <div key={indice} className="flex items-center gap-6 border-t px-4 py-3.5">
                <div className="esqueleto h-3 w-24" />
                <div className="esqueleto h-3 flex-1" />
                <div className="esqueleto h-3 w-20" />
              </div>
            ))}
          </div>
        ) : despesas.length === 0 ? (
          <EstadoVazio
            ilustracao="prancheta"
            titulo="Nenhuma despesa no período"
            acao={
              <Button type="button" variant="outline" onClick={() => setEditando("nova")}>
                Lançar a primeira
              </Button>
            }
          >
            Ex.: aluguel do galpão, conta de energia, jogo de chaves novo.
          </EstadoVazio>
        ) : (
          <div className="tabela-rolagem">
            <table className="tabela">
              <caption className="sr-only">Despesas do período, da competência mais recente para a mais antiga</caption>
              <thead>
                <tr>
                  <th>Competência</th>
                  <th>Despesa</th>
                  <th>Categoria</th>
                  <th>Fornecedor</th>
                  <th>Documento</th>
                  <th className="direita">Valor</th>
                  <th>Lançada por</th>
                  <th>
                    <span className="sr-only">Ações</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {despesas.map((despesa) => (
                  <tr key={despesa.id}>
                    <td className="num whitespace-nowrap text-muted-foreground">{formatarData(despesa.competencia)}</td>
                    <td>
                      {despesa.descricao}
                      {despesa.observacao && <span className="block text-xs text-muted-foreground">{despesa.observacao}</span>}
                    </td>
                    <td className="whitespace-nowrap">{despesa.categoriaDescricao}</td>
                    <td>{despesa.fornecedor ?? "-"}</td>
                    <td className="num">{despesa.documento ?? "-"}</td>
                    <td className="direita num whitespace-nowrap">{formatarMoeda(despesa.valor)}</td>
                    <td className="whitespace-nowrap">{despesa.usuario}</td>
                    <td className="whitespace-nowrap">
                      <button
                        type="button"
                        onClick={() => setEditando(despesa)}
                        className="rounded-md px-2 py-1 text-xs text-muted-foreground hover:bg-accent hover:text-foreground"
                      >
                        Editar<span className="sr-only"> {despesa.descricao}</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => setExcluindo(despesa)}
                        className="rounded-md px-2 py-1 text-xs text-muted-foreground hover:bg-accent hover:text-sinal-texto"
                      >
                        Excluir<span className="sr-only"> {despesa.descricao}</span>
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={5} className="font-medium">
                    Total
                  </td>
                  <td className="direita num font-medium">{formatarMoeda(total)}</td>
                  <td colSpan={2} />
                </tr>
              </tfoot>
            </table>
          </div>
        )}
      </div>

      <Dialogo
        aberto={editando !== null}
        aoFechar={() => setEditando(null)}
        rotulo={editando === "nova" ? "Lançar despesa" : "Editar despesa"}
        className="w-[min(40rem,calc(100vw-2rem))]"
      >
        {editando !== null && (
          <FormularioDeDespesa
            despesa={editando === "nova" ? null : editando}
            categorias={categorias}
            fornecedores={fornecedores}
            aoCancelar={() => setEditando(null)}
            aoSalvar={aoSalvar}
          />
        )}
      </Dialogo>

      <Dialogo
        aberto={excluindo !== null}
        aoFechar={() => setExcluindo(null)}
        rotulo="Excluir despesa"
        className="w-[min(28rem,calc(100vw-2rem))]"
      >
        {excluindo && <ConfirmarExclusao despesa={excluindo} aoCancelar={() => setExcluindo(null)} aoConfirmar={excluir} />}
      </Dialogo>
    </section>
  );
}

function FormularioDeDespesa({
  despesa,
  categorias,
  fornecedores,
  aoCancelar,
  aoSalvar,
}: {
  despesa: Despesa | null;
  categorias: Opcao[];
  fornecedores: Fornecedor[];
  aoCancelar: () => void;
  aoSalvar: () => Promise<void>;
}) {
  const avisar = useToast();
  const [dados, setDados] = useState<Formulario>(() => formularioDe(despesa));
  const [erro, setErro] = useState<{ mensagem: string; detalhes: string[] } | null>(null);
  const [enviando, setEnviando] = useState(false);

  // a edicao mantem o fornecedor ja vinculado, mesmo que tenha sido inativado
  const opcoesDeFornecedor =
    despesa?.fornecedorId && !fornecedores.some((fornecedor) => fornecedor.id === despesa.fornecedorId)
      ? [...fornecedores, { id: despesa.fornecedorId, razaoSocial: `${despesa.fornecedor} (inativo)`, cnpj: "", ativo: false }]
      : fornecedores;

  function campo<K extends keyof Formulario>(chave: K) {
    return {
      value: dados[chave],
      onChange: (evento: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
        setDados((atuais) => ({ ...atuais, [chave]: evento.target.value })),
    };
  }

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    setEnviando(true);
    setErro(null);
    const corpo = {
      descricao: dados.descricao,
      categoria: dados.categoria,
      valor: dados.valor,
      competencia: dados.competencia,
      fornecedorId: dados.fornecedorId ? Number(dados.fornecedorId) : null,
      documento: dados.documento || null,
      observacao: dados.observacao || null,
    };
    try {
      if (despesa) {
        await api.put(`/despesas/${despesa.id}`, corpo);
        avisar("Despesa atualizada.");
      } else {
        await api.post("/despesas", corpo);
        avisar("Despesa lançada.");
      }
      await aoSalvar();
    } catch (excecao) {
      setErro(mensagemDe(excecao));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form onSubmit={enviar} className="p-6 sm:p-8">
      <p className="rotulo">Financeiro</p>
      <h2 className="fonte-display mt-1 text-2xl">{despesa ? "Editar despesa" : "Lançar despesa"}</h2>

      <div className="mt-6">
        <Erro mensagem={erro?.mensagem ?? null} detalhes={erro?.detalhes} />
      </div>

      <div className="grid gap-5 sm:grid-cols-2">
        <div className="sm:col-span-2">
          <Rotulo htmlFor="despesa-descricao">Descrição</Rotulo>
          <input id="despesa-descricao" required minLength={3} maxLength={120} autoFocus className={CAMPO}
                 placeholder="Ex.: Conta de energia de outubro" {...campo("descricao")} />
        </div>
        <div>
          <Rotulo htmlFor="despesa-categoria">Categoria</Rotulo>
          <select id="despesa-categoria" required className={CAMPO} {...campo("categoria")}>
            <option value="">Selecione</option>
            {categorias.map((categoria) => (
              <option key={categoria.valor} value={categoria.valor}>
                {categoria.descricao}
              </option>
            ))}
          </select>
        </div>
        <div>
          <Rotulo htmlFor="despesa-valor" dica="R$">Valor</Rotulo>
          <input id="despesa-valor" type="number" inputMode="decimal" step="0.01" min="0.01" required className={CAMPO}
                 {...campo("valor")} />
        </div>
        <div>
          <Rotulo htmlFor="despesa-competencia" dica="mês a que o gasto pertence">Competência</Rotulo>
          <input id="despesa-competencia" type="date" required className={CAMPO} {...campo("competencia")} />
        </div>
        <div>
          <Rotulo htmlFor="despesa-fornecedor" dica="opcional">Fornecedor</Rotulo>
          <select id="despesa-fornecedor" className={CAMPO} {...campo("fornecedorId")}>
            <option value="">Nenhum</option>
            {opcoesDeFornecedor.map((fornecedor) => (
              <option key={fornecedor.id} value={fornecedor.id}>
                {fornecedor.razaoSocial}
              </option>
            ))}
          </select>
        </div>
        <div>
          <Rotulo htmlFor="despesa-documento" dica="opcional">Documento</Rotulo>
          <input id="despesa-documento" maxLength={60} className={CAMPO} placeholder="Ex.: Fatura 2026/10"
                 {...campo("documento")} />
        </div>
        <div className="sm:col-span-2">
          <Rotulo htmlFor="despesa-observacao" dica="opcional">Observação</Rotulo>
          <textarea id="despesa-observacao" rows={2} maxLength={500} className={AREA_DE_TEXTO} {...campo("observacao")} />
        </div>
      </div>

      <div className="mt-8 flex flex-wrap justify-end gap-3">
        <Button type="button" variant="outline" className="h-11 px-5" onClick={aoCancelar}>
          Cancelar
        </Button>
        <Button type="submit" className="h-11 px-5" disabled={enviando}>
          <i aria-hidden="true" className="bi bi-check2" />
          {enviando ? "Salvando…" : "Salvar"}
        </Button>
      </div>
    </form>
  );
}

function ConfirmarExclusao({
  despesa,
  aoCancelar,
  aoConfirmar,
}: {
  despesa: Despesa;
  aoCancelar: () => void;
  aoConfirmar: (despesa: Despesa) => Promise<string | null>;
}) {
  const [erro, setErro] = useState<string | null>(null);
  const [excluindo, setExcluindo] = useState(false);

  return (
    <div className="p-6 sm:p-8">
      <h2 className="fonte-display text-2xl">Excluir despesa?</h2>
      <p className="mt-3 text-sm text-muted-foreground">
        <span className="text-foreground">{despesa.descricao}</span> · {formatarMoeda(despesa.valor)} ·
        competência {formatarData(despesa.competencia)}. A exclusão fica registrada na auditoria.
      </p>
      <div className="mt-5">
        <Erro mensagem={erro} />
      </div>
      <div className="mt-6 flex justify-end gap-3">
        <Button type="button" variant="outline" className="h-10 px-4" onClick={aoCancelar}>
          Manter
        </Button>
        <Button
          type="button"
          variant="destructive"
          className="h-10 px-4"
          disabled={excluindo}
          onClick={async () => {
            setExcluindo(true);
            setErro(await aoConfirmar(despesa));
            setExcluindo(false);
          }}
        >
          {excluindo ? "Excluindo…" : "Excluir"}
        </Button>
      </div>
    </div>
  );
}
