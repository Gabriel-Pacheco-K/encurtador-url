# 🔗 Encurtador de URL com estatísticas

![CI](https://github.com/Gabriel-Pacheco-K/encurtador-url/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)
![Redis](https://img.shields.io/badge/Redis-7-DC382D)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED)

Encurtador de links no estilo do bit.ly, com um **painel de estatísticas**: cliques por dia, de onde vêm os acessos, navegadores e dispositivos. O back-end é em Java e Spring Boot, com **cache em Redis** no redirecionamento e **registro assíncrono** de cliques.

![Painel de estatísticas](docs/screenshot.png)

> O print usa dados de exemplo.

---

## ✨ Funcionalidades

- **Encurtar links** com código aleatório em Base62 (7 caracteres, cerca de 3,5 trilhões de combinações)
- **Apelido personalizado**, como `/meu-portfolio`, e **data de expiração** opcional
- **Redirecionamento rápido**: o destino fica no **cache do Redis**, então acessos repetidos não consultam o banco
- **Estatísticas**: total de cliques, série dos últimos 30 dias, principais origens, navegadores e dispositivos
- **Painel web** responsivo com gráficos (Chart.js), servido pela própria aplicação
- **Token de gerenciamento**: só quem criou o link consegue apagá-lo, sem precisar de cadastro
- **Rate limiting** por IP na criação de links, com os cabeçalhos `X-RateLimit-*` e `Retry-After`
- **Segurança**: aceita apenas `http` e `https` (bloqueia `javascript:` e outros esquemas) e recusa links para o próprio encurtador
- **Erros no padrão RFC 7807** (Problem Details) e documentação interativa no Swagger

## 🛠️ Tecnologias

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 (records, text blocks, `List.getLast`) |
| Framework | Spring Boot 3.3 (Web, Data JPA, Cache, Validation, Actuator) |
| Banco de dados | PostgreSQL 16 + Flyway |
| Cache | Redis 7 (Spring Cache) |
| Front-end | HTML, CSS, JavaScript puro e Chart.js |
| Documentação | springdoc-openapi (Swagger UI) |
| Testes | JUnit 5 (testes parametrizados), Mockito, AssertJ, MockMvc, Awaitility, H2 |
| Infra | Docker (multi-stage), Docker Compose, GitHub Actions |

## 🏗️ Arquitetura

```
                   ┌──────────── cache hit ────────────┐
GET /{codigo} ──▶ RedirectController ──▶ ShortUrlService.resolve() ──▶ Redis
                        │                       │ cache miss
                        │                       └──▶ PostgreSQL
                        │
                        ├──▶ 302 Location: URL original   (resposta imediata)
                        │
                        └──▶ ClickRecorder (@Async) ──▶ PostgreSQL   (em paralelo)
```

**Decisões técnicas:**

- **302 em vez de 301**: com 301, o navegador guarda o redirecionamento e os próximos cliques nem chegam ao servidor, o que deixaria as estatísticas incompletas. A resposta também leva `Cache-Control: no-store`.
- **Cache só do necessário**: o Redis guarda um `record` com id, URL e expiração, e não a entidade JPA inteira. As entradas expiram sozinhas (TTL de 1 hora) e saem do cache quando o link é apagado (`@CacheEvict`).
- **Clique assíncrono**: o usuário é redirecionado na hora, e a gravação da estatística acontece em outra thread. Uma falha nessa gravação nunca quebra o redirecionamento.
- **Contador atômico**: `UPDATE ... SET click_count = click_count + 1` direto no banco. Ler, somar e salvar perderia cliques com acessos simultâneos.
- **Agregação no banco**: as estatísticas usam `GROUP BY` em JPQL. Os dias sem clique são preenchidos com zero no Java, para o gráfico ficar contínuo.
- **Privacidade**: do referer, guarda só o domínio (`linkedin.com`), nunca o caminho completo, que pode conter dados pessoais.
- **Códigos aleatórios** (`SecureRandom`) em vez de sequenciais: impedem adivinhar os links de outras pessoas.
- **Token comparado em tempo constante** (`MessageDigest.isEqual`), para não vazar informação pelo tempo de resposta.
- **`Clock` injetável**: a expiração e as janelas do rate limit podem ser testadas sem depender da hora real.

## 🚀 Como executar

### Com Docker (recomendado)

```bash
git clone https://github.com/Gabriel-Pacheco-K/encurtador-url.git
cd encurtador-url
docker compose up --build
```

Depois, acesse:
- **Painel:** http://localhost:8080
- **Swagger:** http://localhost:8080/swagger-ui.html

### Localmente

Requisitos: Java 21, Maven, PostgreSQL e Redis.

```bash
docker compose up -d db redis
mvn spring-boot:run
```

| Variável | Padrão | Descrição |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | Endereço público usado nos links curtos |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | `localhost:5432/encurtador` | Banco de dados |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis |
| `RATE_LIMIT_MAX` / `RATE_LIMIT_WINDOW` | `20` / `1m` | Links por IP em cada janela de tempo |

## 📖 API

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/urls` | Encurta uma URL (`url`, `alias` e `expiresAt` opcionais) |
| `GET` | `/{codigo}` | Redireciona (302) e registra o clique |
| `GET` | `/api/urls/{codigo}/stats` | Estatísticas do link |
| `DELETE` | `/api/urls/{codigo}` | Apaga o link (cabeçalho `X-Manage-Token`) |

### Exemplo de uso

```bash
curl -X POST localhost:8080/api/urls -H "Content-Type: application/json" \
  -d '{"url":"https://github.com/Gabriel-Pacheco-K","alias":"github-gabriel"}'
```

```json
{
  "code": "github-gabriel",
  "shortUrl": "http://localhost:8080/github-gabriel",
  "originalUrl": "https://github.com/Gabriel-Pacheco-K",
  "createdAt": "2026-10-11T14:11:38.759Z",
  "expiresAt": null,
  "manageToken": "xh1pcZSDuDWnzsMFHrMamm6b5XSHN1Sy"
}
```

| Situação | Status |
|---|---|
| Link criado | `201 Created` |
| Redirecionamento | `302 Found` |
| URL ou campos inválidos | `400 Bad Request` |
| Token de gerenciamento errado | `403 Forbidden` |
| Código inexistente | `404 Not Found` |
| Apelido já usado | `409 Conflict` |
| Link expirado | `410 Gone` |
| Limite de criação atingido | `429 Too Many Requests` |

## 🧪 Testes

```bash
mvn verify
```

São **39 testes**:

- **Unitários**: geração de código com nova tentativa em caso de colisão, validação de URL (incluindo `javascript:` e links para o próprio encurtador), classificação de User-Agent (testes parametrizados), janela do rate limit com relógio controlado, série de 30 dias preenchida com zeros, e verificação do token
- **Integração** (MockMvc + H2): o fluxo completo de criar, redirecionar e consultar estatísticas; prova de que **5 redirecionamentos fazem só 1 consulta ao banco** (cache); apelido duplicado; expiração (410); exclusão com token e remoção do cache

O GitHub Actions executa os testes e o build da imagem Docker a cada push.

## 🗺️ Próximos passos

- [ ] QR Code para cada link
- [ ] Rate limit distribuído no Redis, para rodar com várias instâncias
- [ ] Geolocalização aproximada por país
- [ ] Testcontainers para testar com PostgreSQL e Redis reais

---

Desenvolvido por **Gabriel Pacheco Kiesel**: [Portfólio](https://gabriel-pacheco-k.github.io) · [LinkedIn](https://www.linkedin.com/in/gabriel-pacheco-3b637a362/) · [GitHub](https://github.com/Gabriel-Pacheco-K)
