# Requisitos e setup do projeto

Projeto com dois módulos em `oncar/`:

| Módulo | Stack | Porta padrão |
| --- | --- | --- |
| `oncar/backend` | Java 17 + Spring Boot 3.3 (Maven) | 8080 |
| `oncar/frontend` | Next.js 16 + React 19 (npm) | 3000 |

## Ferramentas necessárias

| Ferramenta | Versão mínima | Como obter |
| --- | --- | --- |
| JDK | 17+ (testado até o 26) | https://adoptium.net/ |
| Maven | não é preciso instalar — use o wrapper `./mvnw` (baixa o Maven 3.9 na 1ª execução) | — |
| Node.js | 20 LTS+ (testado até o 24) | https://nodejs.org/ |
| npm | 10+ (vem com o Node) | — |
| Docker + Docker Compose | opcional (produção/Postgres) | https://docs.docker.com/get-docker/ |
| Git | qualquer recente | https://git-scm.com/ |

Verifique tudo de uma vez:

```bash
java -version      # 17 ou mais recente
node -v            # v20+
npm -v
```

O Maven vem pelo wrapper do projeto (`oncar/backend/mvnw` no Linux/macOS,
`mvnw.cmd` no Windows); não precisa instalá-lo à parte.

## Instalação das dependências

### Backend

```bash
cd oncar/backend
cp .env.example .env         # ajuste as variáveis
./mvnw dependency:go-offline # baixa as dependências (mvnw.cmd no Windows)
```

### Frontend

```bash
cd oncar/frontend
cp .env.example .env.local # ajuste as variáveis
npm ci                     # instala a partir do package-lock.json
```

Use `npm ci` (não `npm install`) para reproduzir exatamente o `package-lock.json`.

### Setup automático

Na raiz do repositório:

- **Linux/macOS:** `./setup.sh`
- **Windows (PowerShell):** `./setup.ps1`

## Como executar

### Backend — desenvolvimento (H2 em memória)

```bash
cd oncar/backend
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
```

Sobe em <http://localhost:8080> (perfil `dev`, dados de demonstração, senha `oncar2026`).

### Backend — produção (PostgreSQL)

```bash
cd oncar/backend
docker compose up --build
```

ou com um Postgres próprio, definindo `ONCAR_DB_URL`, `ONCAR_DB_USER`, `ONCAR_DB_PASSWORD`.

### Frontend

```bash
cd oncar/frontend
npm run dev
```

Sobe em <http://localhost:3000>.

## Testes

```bash
cd oncar/backend && ./mvnw test
cd oncar/frontend && npm run lint
```
