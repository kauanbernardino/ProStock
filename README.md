# ProStock

Sistema de gerenciamento de estoque para lojas de autopeças — controle de produtos, categorias, fornecedores, movimentações de estoque, alertas automáticos, vendas de balcão (com Pix) e um painel com sugestão de compra ponderada pela demanda.

Projeto de TCC, construído para uso real no dia a dia de uma loja.

## Stack

- Java 17 + Spring Boot 3.3.2 (Web, Data JPA, Security, Validation)
- PostgreSQL 17
- Spring Session JDBC (sessão de login persistida no banco)
- Frontend em HTML/CSS/JavaScript puro (sem framework), servido como estático pelo próprio Spring
- JUnit 5 + Mockito

## Pré-requisitos

- JDK 17
- Maven 3.9+
- PostgreSQL 17 (local ou em outra máquina acessível)

## Como rodar localmente

**1. Criar o banco e aplicar o schema**

```bash
createdb prostock
psql -d prostock -f database/schema.sql
```

O script cria o schema `prostock` dentro do banco e todas as tabelas — não precisa rodar mais nada além dele.

**2. Configurar a conexão (se for diferente do padrão)**

Por padrão a aplicação espera Postgres em `localhost:5432`, usuário/senha `postgres`/`postgres` (veja [`application.yml`](src/main/resources/application.yml)). Para usar outra configuração, sobrescreva por variável de ambiente antes de subir a aplicação:

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/prostock?currentSchema=prostock"
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
```

**3. Rodar a aplicação**

```bash
mvn spring-boot:run
```

Acesse **http://localhost:8080**.

**Login padrão** (criado automaticamente na primeira execução, se o banco de usuários estiver vazio):

```
admin@prostock.com / admin123
```

> Troque essa senha pela tela de usuários assim que possível — não é seguro deixar a credencial padrão ativa fora de ambiente de teste.

## Variáveis de ambiente

| Variável | Padrão | Para quê |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/prostock?currentSchema=prostock` | Conexão com o banco |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | Credenciais do banco |
| `UPLOAD_DIR` | `./uploads` | Onde ficam salvas as fotos de produto |
| `PIX_CHAVE` | `00000000000` | Chave Pix real da loja |
| `PIX_NOME_RECEBEDOR` | `ProStock Autopecas` | Nome exibido no código Pix gerado |
| `PIX_CIDADE` | `SAO PAULO` | Cidade exibida no código Pix gerado |
| `CRON_OBSOLESCENCIA` | `0 0 7 * * *` | Horário da verificação automática de estoque obsoleto |

Os dados reais do Pix **não** ficam no `application.yml` (que vai para o Git). Defina-os como variáveis de ambiente ou crie o arquivo `config/application.properties` na raiz do projeto (ignorado pelo Git), que o Spring Boot carrega automaticamente:

```properties
app.pix.chave=+5500000000000
app.pix.nome-recebedor=Nome do Recebedor
app.pix.cidade=CIDADE
```

## Atualização de bancos existentes

Alterações de schema feitas depois da primeira versão ficam em `src/main/resources/db/atualizacoes.sql` e são aplicadas automaticamente a cada inicialização (os comandos são idempotentes). Um banco criado com um `schema.sql` antigo não precisa de nenhum passo manual.

## Banco de demonstração

Para apresentar ou testar o sistema com volume real de dados, existe um banco separado (`prostock_demo`) com cerca de 7 meses de operação simulada de uma loja de autopeças: 47 peças com fotos ilustrativas, 8 categorias, 6 fornecedores, 4 usuários, mais de mil vendas (Pix, cartão e dinheiro, incluindo cancelamentos), compras, devoluções, avarias, requisições internas, inventários mensais e alertas. O banco de uso real não é tocado.

No IntelliJ, use a configuração de execução **ProStock DEMO** (ou rode a classe `com.prostock.demo.ProStockDemoApplication`). Na primeira vez o banco é criado e populado sozinho.

**ProStock DEMO (recriar dados)** apaga e gera tudo de novo, com as datas deslocadas para que o último dia simulado seja ontem. Use na véspera de uma apresentação, para o gráfico da semana e a previsão de demanda ficarem atuais.

Usuários da demonstração:

| E-mail | Senha | Cargo |
|---|---|---|
| admin@prostock.com | admin123 | Administrador |
| ana.souza@prostock.com | func123 | Funcionário |
| joao.pereira@prostock.com | func123 | Funcionário |
| marcos.lima@prostock.com | func123 | Funcionário (inativo) |

Os dados (um arquivo por dia da semana, em `src/main/resources/demo`) e as fotos são gerados por `ferramentas/demo/gerar_dados_demo.py` e `ferramentas/demo/gerar_fotos_demo.py` (Python 3 com `bcrypt` e `Pillow`).

### Rodando a demonstração em outro computador

Os dados e as fotos da demonstração já fazem parte do projeto — não é preciso copiar banco nenhum. O sistema também não depende de internet para rodar.

1. Instale o **JDK 17**, o **PostgreSQL 17** (na instalação, defina a senha do usuário `postgres` como `postgres`) e o **IntelliJ IDEA Community**.
2. Baixe o projeto (`git clone` ou o ZIP do GitHub) e abra a pasta no IntelliJ.
3. Rode a configuração **ProStock DEMO (recriar dados)**. Ela cria o banco `prostock_demo`, carrega os dados e copia as fotos.
4. Acesse **http://localhost:8080** e entre com um dos usuários acima.

Se a senha do `postgres` for outra, informe-a na variável de ambiente `DB_PASSWORD` da configuração de execução (*Edit Configurations… → Environment variables*).

Sem o arquivo `config/application.properties`, o Pix usa a chave de exemplo `00000000000`.

## Testes

```bash
mvn test
```

## Estrutura do projeto

```
src/main/java/com/prostock/
├── model/        entidades JPA (tabelas do banco)
├── repository/   consultas Spring Data JPA
├── dto/          objetos de request/response da API
├── service/      regras de negócio
├── controller/   endpoints REST
├── security/     autenticação e autorização
└── config/       configuração do Spring (segurança, CSRF, upload)

src/main/resources/
├── application.yml
└── static/       frontend (HTML/CSS/JS)

database/schema.sql   schema completo do PostgreSQL
```

## Segurança

- Autenticação por sessão (cookie), senha com hash BCrypt.
- Autorização por cargo: `ADMINISTRADOR` (acesso total) e `FUNCIONARIO` (operação do dia a dia, sem gestão de cadastros nem usuários).
- Proteção CSRF (cookie-based) e escape de HTML em todo dado dinâmico renderizado no frontend.

Este é um sistema de uso interno — não há cadastro público de usuários; contas são criadas por um administrador.
