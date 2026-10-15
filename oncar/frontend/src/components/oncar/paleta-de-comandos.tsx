"use client";

import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import ActionSearchBar, { type Action } from "@/components/kokonutui/action-search-bar";
import { useOficina, useUsuario } from "@/components/oncar/contexto";
import { Dialogo } from "@/components/oncar/dialogo";
import { alternarTema } from "@/components/oncar/seletor-de-tema";
import { api, formatarNumero } from "@/lib/api";
import { normalizar, produtoCasa } from "@/lib/assistente";
import { podeGerenciarEstoque, podeRegistrarSaida, type Cliente } from "@/lib/tipos";

type Comando = Action & { executar: () => void };

// Padrao antigo (ABC-1234) e Mercosul (ABC1D23).
const PLACA = /^[a-z]{3}-?\d[a-z0-9]\d{2}$/i;

// Clientes mudam pouco e so por outra tela; a lista vale para a sessao inteira.
let clientesEmCache: Cliente[] | null = null;

function icone(nome: string) {
  return <i aria-hidden="true" className={`bi ${nome} text-[0.95rem]`} />;
}

/** Busca global (Ctrl+K): pecas, clientes, OS/documentos e atalhos da oficina. */
export function PaletaDeComandos({ aberta, aoFechar }: { aberta: boolean; aoFechar: () => void }) {
  return (
    <Dialogo
      aberto={aberta}
      aoFechar={aoFechar}
      rotulo="Busca global"
      className="mt-[10vh] mb-auto w-[min(40rem,calc(100vw-2rem))] open:zoom-in-95"
    >
      <ConteudoDaPaleta aoFechar={aoFechar} />
    </Dialogo>
  );
}

function ConteudoDaPaleta({ aoFechar }: { aoFechar: () => void }) {
  const router = useRouter();
  const usuario = useUsuario();
  const { produtos, movimentacoes, abrirAssistente } = useOficina();
  const [consulta, setConsulta] = useState("");
  const [clientes, setClientes] = useState<Cliente[]>(() => clientesEmCache ?? []);

  useEffect(() => {
    if (clientesEmCache) return;
    let ativo = true;
    api
      .get<Cliente[]>("/clientes")
      .then((lista) => {
        clientesEmCache = lista;
        if (ativo) setClientes(lista);
      })
      .catch(() => undefined);
    return () => {
      ativo = false;
    };
  }, []);

  const comandos = useMemo<Comando[]>(() => {
    const ir = (href: string) => () => {
      aoFechar();
      router.push(href);
    };
    const palavras = normalizar(consulta).split(/\s+/).filter(Boolean);
    const casa = (texto: string) => palavras.every((palavra) => normalizar(texto).includes(palavra));
    const gerencia = podeGerenciarEstoque(usuario);

    const atalhos: Comando[] = [
      { id: "ir-painel", label: "Painel", icon: icone("bi-speedometer2"), executar: ir("/painel") },
      { id: "ir-produtos", label: "Produtos", icon: icone("bi-box-seam"), executar: ir("/produtos") },
      { id: "ir-movimentacoes", label: "Movimentações", icon: icone("bi-arrow-left-right"), executar: ir("/movimentacoes") },
      ...(gerencia
        ? [{ id: "nova-entrada", label: "Registrar entrada", description: "Compra de fornecedor", icon: icone("bi-box-arrow-in-down"), executar: ir("/movimentacoes?aba=entrada") }]
        : []),
      ...(podeRegistrarSaida(usuario)
        ? [{ id: "nova-saida", label: "Registrar saída", description: "Venda, OS, perda ou devolução", icon: icone("bi-box-arrow-up"), executar: ir("/movimentacoes?aba=saida") }]
        : []),
      ...(gerencia
        ? [{ id: "novo-ajuste", label: "Ajuste de inventário", description: "Depois da contagem", icon: icone("bi-sliders"), executar: ir("/movimentacoes?aba=ajuste") }]
        : []),
      {
        id: "assistente",
        label: "Perguntar ao assistente",
        icon: icone("bi-stars"),
        executar: () => {
          aoFechar();
          abrirAssistente();
        },
      },
      {
        id: "tema",
        label: "Alternar tema Noturna / Papel",
        icon: icone("bi-moon-stars"),
        executar: () => {
          aoFechar();
          alternarTema();
        },
      },
    ].map((comando) => ({ ...comando, group: "Ir para" }));

    if (!palavras.length) return atalhos;

    const pecas: Comando[] = produtos
      .filter((produto) => produtoCasa(produto, palavras))
      .sort((a, b) => Number(normalizar(b.sku) === palavras.join(" ")) - Number(normalizar(a.sku) === palavras.join(" ")))
      .slice(0, 6)
      .map((produto) => ({
        id: `peca-${produto.id}`,
        group: "Peças",
        label: produto.descricao,
        description: `${produto.sku} · saldo ${formatarNumero(produto.saldo, 3)} ${produto.unidade}`,
        end: !produto.ativo ? "inativo" : produto.abaixoDoMinimo ? "repor" : undefined,
        icon: icone("bi-nut"),
        executar: ir(`/produtos/${produto.id}`),
      }));

    const clientesEncontrados: Comando[] = clientes
      .filter((cliente) => casa(`${cliente.nome} ${cliente.documento}`))
      .slice(0, 4)
      .map((cliente) => ({
        id: `cliente-${cliente.id}`,
        group: "Clientes",
        label: cliente.nome,
        description: `${cliente.documento} · registrar saída para este cliente`,
        icon: icone("bi-person"),
        executar: ir(`/movimentacoes?aba=saida&cliente=${cliente.id}`),
      }));

    const documentos = [
      ...new Set(
        movimentacoes
          .map((movimentacao) => movimentacao.documento?.trim())
          .filter((documento): documento is string => Boolean(documento) && casa(documento ?? "")),
      ),
    ]
      .slice(0, 4)
      .map<Comando>((documento) => ({
        id: `documento-${documento}`,
        group: "OS e documentos",
        label: documento,
        description: `${movimentacoes.filter((m) => m.documento?.trim() === documento).length} lançamento(s) recentes`,
        icon: icone("bi-file-earmark-text"),
        executar: ir(`/movimentacoes?documento=${encodeURIComponent(documento)}`),
      }));

    const placa: Comando[] = PLACA.test(consulta.trim())
      ? [
          {
            id: "placa",
            group: "Veículos",
            label: `Placa ${consulta.trim().toUpperCase()}`,
            description: "Consulta de veículos em breve.",
            icon: icone("bi-car-front"),
            disabled: true,
            executar: () => undefined,
          },
        ]
      : [];

    return [...pecas, ...clientesEncontrados, ...documentos, ...placa, ...atalhos.filter((atalho) => casa(`${atalho.label} ${atalho.description ?? ""}`))];
  }, [consulta, produtos, clientes, movimentacoes, usuario, router, aoFechar, abrirAssistente]);

  const porId = useMemo(() => new Map(comandos.map((comando) => [comando.id, comando])), [comandos]);

  return (
    <ActionSearchBar
      actions={comandos}
      autoFocus
      emptyMessage={`Nada encontrado para "${consulta}".`}
      footer={<span>Busca nos dados da oficina</span>}
      label="Buscar peça, cliente, placa ou OS"
      onQueryChange={setConsulta}
      onSelect={(acao) => porId.get(acao.id)?.executar()}
      placeholder="Buscar peça, cliente, placa ou OS…"
      query={consulta}
    />
  );
}
