# Requisitos e setup do projeto

Projeto com dois módulos em `oncar/`:

| Módulo | Stack | Porta padrão |
| --- | --- | --- |
| `oncar/backend` | Java 17 + Spring Boot 3.3 (Maven) | 8080 |
| `oncar/frontend` | Next.js 16 + React 19 (npm) | 3000 |

## Ferramentas necessárias

| Ferramenta | Versão mínima | Como obter |
| --- | --- | --- |
| JDK | 17 (LTS) | https://adoptium.net/ |
| Maven | 3.9+ | https://maven.apache.org/download.cgi |
| Node.js | 20 LTS+ | https://nodejs.org/ |
| npm | 10+ (vem com o Node) | — |
| Docker + Docker Compose | opcional (produção/Postgres) | https://docs.docker.com/get-docker/ |
| Git | qualquer recente | https://git-scm.com/ |

Verifique tudo de uma vez:

```bash
java -version      # deve indicar 17.x
mvn -version
node -v             # v20+ ou v22+
npm -v
```

## Instalação das dependências

### Backend

```bash
cd oncar/backend
cp .env.example .env      # ajuste as variáveis
mvn dependency:go-offline # baixa as dependências do Maven
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
mvn spring-boot:run
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
cd oncar/backend && mvn test
cd oncar/frontend && npm run lint
```
