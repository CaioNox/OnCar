# OnCar · front-end "Oficina Noturna"

Next.js 16 + React 19 + Tailwind CSS v4. O front-end conversa com o back-end Spring Boot
(porta 8080) pelo proxy `/api` do `next.config.ts`. **Nenhum contrato da API foi alterado:**
as telas chamam os mesmos endpoints de antes.

```bash
npm ci          # dependências
npm run dev     # http://localhost:3000
npm run lint
```

## Sistema visual

| Item | Onde | Resumo |
| --- | --- | --- |
| Tokens e temas | `src/app/globals.css` | Paleta 60/30/10 em `@theme`: marinho (base), musgo (apoio) e carmesim (sinal, só para alerta, ação crítica e estoque baixo). Tema padrão **Noturna** (`.dark`) e claro **Papel**, trocados sem piscar por um script inline no `layout.tsx`. |
| Fontes | `src/app/layout.tsx` | Fraunces (títulos e KPIs, eixos `opsz` e `SOFT`), Geist (texto, 15 px) e JetBrains Mono (números, SKUs, placas, com `tabular-nums`). |
| Ícones | `bootstrap-icons` + `lucide-react` | Do Bootstrap entra **só a fonte de ícones** (`bi-car-front`, `bi-wrench`...); o `bootstrap.css` ficaria em conflito com o preflight do Tailwind. |
| Utilitários | `globals.css` | `fonte-display`, `num`, `rotulo`, `vidro`, `esqueleto` e a tabela densa `.tabela` (zebrada, cabeçalho fixo). |

Contrastes conferidos (WCAG AA): o musgo `#6B7D4E` serve para foco e bordas, mas não para
texto pequeno sobre o marinho; texto de status usa o tom "líquen" `#A7B98A`. O carmesim sobre o
marinho dá 2,35:1, por isso só aparece como preenchimento, com texto osso por cima (5,7:1).

## `<GarageWallpaper />`

`src/components/oncar/garage-wallpaper.tsx`, isolado e guiado por props:

```tsx
<GarageWallpaper osAbertas={2} estoqueCritico={1} saldo={-850} />
```

- **Base:** geometria do `@kokonutui/background-paths` recolorida em musgo, com 14 a 20% de
  opacidade, como um chicote elétrico sobre a planta técnica, com grão de 3% por cima.
- **Lanterna do mecânico:** um halo segue o cursor por mola do Motion e revela a grade técnica
  (`mask-image` radial). Desliga em telas de toque e abaixo de 768 px.
- **Reage aos dados:**
  - `osAbertas`: cada OS acelera o fluxo em 8%, até 1,8×;
  - `estoqueCritico > 0`: uma linha pulsa em carmesim a cada 6 s;
  - `saldo`: puxa as linhas para o musgo (positivo) ou para um marinho frio (negativo).
- **Modo foco:** entra depois de 8 s sem mexer o ponteiro, ou enquanto um campo de texto está
  focado. O wallpaper desacelera para 20% e escurece 30%, e volta suave ao mexer o mouse.
- **Movimento reduzido:** respeita `prefers-reduced-motion` e fica estático, mantendo a linha
  de alerta acesa se houver estoque crítico.
- **Performance:** pausa com `visibilitychange`. A deriva das camadas usa WAAPI em `transform`
  (compositor) e a velocidade muda por `updatePlaybackRate`, sem reiniciar a animação.

**Bancada de testes:** `/demo` simula OS, estoque crítico e saldo com controles deslizantes.

### De onde vêm os dados

O shell (`src/components/oncar/app-shell.tsx`) carrega `/painel`, `/movimentacoes` e `/produtos`
a cada navegação e após cada lançamento. Não há polling, porque requisições periódicas
impediriam a sessão de expirar por inatividade (RN015). O cálculo fica em `src/lib/sinais.ts`.

| Prop | Origem hoje | Quando a API evoluir |
| --- | --- | --- |
| `estoqueCritico` | `painel.itensAbaixoDoMinimo` (real) | já é o dado final |
| `osAbertas` | aproximação: OS distintas que receberam peças hoje (saídas por "Aplicação em ordem de serviço") | trocar pelo total de OS abertas do RF14 |
| `saldo` | aproximação: fluxo das 50 movimentações recentes (vendas e OS pelo preço de venda, menos compras e perdas pelo custo médio). Sem acesso a custos (RN007), fica neutro | trocar pelo saldo do financeiro (RF15/RF16) |

## Componentes KokonutUI

Instalados com `npx shadcn@latest add @kokonutui/<nome>`. Os arquivos ficam em
`src/components/kokonutui/` e são nossos. Os ajustes estão anotados no cabeçalho de cada um.

| Componente | Onde é usado | Ajustes locais |
| --- | --- | --- |
| `background-paths` | geometria do `GarageWallpaper` | exporta `generatePathPoints` e `generateAestheticPath` |
| `beams-background` | `/login` | musgo e marinho; renderiza `children`; modo foco; movimento reduzido; pausa com a aba oculta; canvas em meia resolução |
| `bento-grid` | `/painel` (painel de instrumentos) | vira `BentoGrid`/`BentoCard` reutilizáveis; demos removidas |
| `liquid-glass-card` | KPIs do `/painel` (efeito líquido só no cartão focal) | vidro em marinho translúcido; sem o player de demonstração; sombra mais leve no tema Papel |
| `apple-activity-card` | tacômetros do `/painel` | anéis alimentados por props, com cores de alerta por token |
| `currency-transfer` | confirmação animada de entrada, saída e ajuste em `/movimentacoes` | conteúdo "de → para" por props; sem o tooltip de câmbio |
| `action-search-bar` | busca global Ctrl+K (peças, clientes, OS/documentos, placa) | controlado por props; grupos; ações desativadas; padrão ARIA de combobox |
| `ai-prompt` | campo do assistente | sem seletor de modelos nem anexo; o botão de enviar funciona |
| `shimmer-text` | "Pensando…" do assistente | texto em linha; parado com movimento reduzido |
| `particle-button` | **só** ações críticas: "Dar baixa no estoque" e "Confirmar estorno" | carmesim; repassa `onClick`; `controle.explodir()` dispara depois da confirmação da API |

Ficaram sem uso depois do redesign e podem ser removidos: `anthropic*`, `open-ai*`, `gemini`,
`mistral`, `deepseek`, `scroll-text` e `ui/tooltip`.

## Telas e o que ainda depende da API

| Rota | Destaque |
| --- | --- |
| `/login` | Beams em musgo e marinho; mostrar/ocultar senha |
| `/painel` | Capital em estoque (ponto focal), abaixo do mínimo, lançamentos em 7 dias, tacômetros, reposição e kardex recente |
| `/produtos` | Tabela densa ou cartões com tilt (até 4°), chips de categoria, nível e situação; `?nivel=critico` |
| `/produtos/[id]` | Curva do saldo em degraus, ficha e kardex com variação assinada |
| `/movimentacoes` | Bancada com abas, confirmação animada, histórico filtrável e estorno inline; aceita `?aba=`, `?cliente=` e `?documento=` |
| `/demo` | Bancada de testes do wallpaper |

A API ainda não expõe **ordens de serviço (RF14), financeiro e relatórios (RF15/RF16), veículos
e o assistente de IA (RF19)**. Enquanto isso:
- os tacômetros mostram leituras reais de estoque (acima do mínimo, sem ruptura, sem estorno);
- a busca avisa que placas ainda não têm endpoint;
- o assistente responde localmente com os endpoints de estoque (`src/lib/assistente.ts`),
  pronto para trocar por uma chamada de IA.

## Acessibilidade

- Foco visível com contorno musgo de 2 px e link "Pular para o conteúdo".
- Ctrl+K e diálogos nativos (`<dialog>`), que prendem o foco e fecham com Esc.
- Abas com setas e rótulos em todos os campos.
- Toasts em `aria-live`, erros com `role="alert"` e esqueletos no lugar de spinners.
- Tudo respeita `prefers-reduced-motion`.
