-- =====================================================================
-- ProStock - Script DDL PostgreSQL
-- Sistema de gerenciamento de estoque (loja de autopeças)
-- =====================================================================

-- Execute como um usuário com permissão de criação de banco, ou aponte
-- para um banco "prostock" já existente antes de rodar este script.
-- CREATE DATABASE prostock;
-- \c prostock

CREATE SCHEMA IF NOT EXISTS prostock;
SET search_path TO prostock;

-- =====================================================================
-- Tabela: usuarios
-- =====================================================================
CREATE TABLE usuarios (
    id            BIGSERIAL PRIMARY KEY,
    nome          VARCHAR(120)  NOT NULL,
    email         VARCHAR(150)  NOT NULL,
    senha_hash    VARCHAR(255)  NOT NULL,
    cargo         VARCHAR(20)   NOT NULL,
    ativo         BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT uq_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_cargo CHECK (cargo IN ('ADMINISTRADOR', 'FUNCIONARIO'))
);

-- =====================================================================
-- Tabela: categorias
-- =====================================================================
CREATE TABLE categorias (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(100) NOT NULL,
    descricao   VARCHAR(255),

    CONSTRAINT uq_categorias_nome UNIQUE (nome)
);

-- =====================================================================
-- Tabela: fornecedores
-- =====================================================================
CREATE TABLE fornecedores (
    id            BIGSERIAL PRIMARY KEY,
    razao_social  VARCHAR(150) NOT NULL,
    cnpj          VARCHAR(14)  NOT NULL,
    contato       VARCHAR(120),
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_fornecedores_cnpj UNIQUE (cnpj),
    CONSTRAINT ck_fornecedores_cnpj_numerico CHECK (cnpj ~ '^[0-9]{14}$')
);

-- =====================================================================
-- Tabela: produtos
-- =====================================================================
CREATE TABLE produtos (
    id                  BIGSERIAL PRIMARY KEY,
    nome                VARCHAR(150)    NOT NULL,
    sku                 VARCHAR(50)     NOT NULL,
    descricao           VARCHAR(500),
    preco               NUMERIC(12,2)   NOT NULL,
    quantidade_atual    INTEGER         NOT NULL DEFAULT 0,
    estoque_minimo      INTEGER         NOT NULL DEFAULT 0,
    estoque_maximo      INTEGER         NOT NULL,
    localizacao_fisica  VARCHAR(100),
    categoria_id        BIGINT          NOT NULL,
    fornecedor_id       BIGINT          NOT NULL,
    ativo               BOOLEAN         NOT NULL DEFAULT TRUE,
    criado_em           TIMESTAMP       NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMP       NOT NULL DEFAULT now(),
    versao              BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT uq_produtos_sku UNIQUE (sku),
    CONSTRAINT fk_produtos_categoria
        FOREIGN KEY (categoria_id) REFERENCES categorias (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_produtos_fornecedor
        FOREIGN KEY (fornecedor_id) REFERENCES fornecedores (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_produtos_preco_positivo CHECK (preco >= 0),
    CONSTRAINT ck_produtos_quantidade_nao_negativa CHECK (quantidade_atual >= 0),
    CONSTRAINT ck_produtos_estoque_minimo_nao_negativo CHECK (estoque_minimo >= 0),
    CONSTRAINT ck_produtos_estoque_max_maior_min CHECK (estoque_maximo >= estoque_minimo)
);

-- Necessário para o índice gin_trgm_ops de busca textual por nome (deve
-- existir antes do índice que a utiliza).
-- Requer permissão de superusuário/extensão habilitada no banco.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_produtos_nome ON produtos USING gin (nome gin_trgm_ops);
CREATE INDEX idx_produtos_sku ON produtos (sku);
CREATE INDEX idx_produtos_categoria ON produtos (categoria_id);
CREATE INDEX idx_produtos_fornecedor ON produtos (fornecedor_id);

-- =====================================================================
-- Tabela: produto_imagens
-- Fotos do produto, usadas pelo vendedor para conferência visual quando
-- o cliente traz a peça física em mãos mas não sabe o veículo/ano exato.
-- =====================================================================
CREATE TABLE produto_imagens (
    id          BIGSERIAL PRIMARY KEY,
    produto_id  BIGINT       NOT NULL,
    caminho     VARCHAR(255) NOT NULL,
    ordem       INTEGER      NOT NULL DEFAULT 0,
    criado_em   TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_produto_imagens_produto
        FOREIGN KEY (produto_id) REFERENCES produtos (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE INDEX idx_produto_imagens_produto ON produto_imagens (produto_id);

-- =====================================================================
-- Tabela: motivos_movimentacao
-- =====================================================================
CREATE TABLE motivos_movimentacao (
    id          BIGSERIAL PRIMARY KEY,
    descricao   VARCHAR(100) NOT NULL,

    CONSTRAINT uq_motivos_descricao UNIQUE (descricao)
);

-- =====================================================================
-- Tabela: movimentacoes
-- =====================================================================
CREATE TABLE movimentacoes (
    id            BIGSERIAL PRIMARY KEY,
    produto_id    BIGINT      NOT NULL,
    usuario_id    BIGINT      NOT NULL,
    motivo_id     BIGINT      NOT NULL,
    tipo          VARCHAR(10) NOT NULL,
    quantidade    INTEGER     NOT NULL,
    data_hora     TIMESTAMP   NOT NULL DEFAULT now(),
    observacao    VARCHAR(255),

    CONSTRAINT fk_movimentacoes_produto
        FOREIGN KEY (produto_id) REFERENCES produtos (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_movimentacoes_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_movimentacoes_motivo
        FOREIGN KEY (motivo_id) REFERENCES motivos_movimentacao (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_movimentacoes_tipo CHECK (tipo IN ('ENTRADA', 'SAIDA')),
    CONSTRAINT ck_movimentacoes_quantidade_positiva CHECK (quantidade > 0)
);

CREATE INDEX idx_movimentacoes_produto ON movimentacoes (produto_id);
CREATE INDEX idx_movimentacoes_usuario ON movimentacoes (usuario_id);
CREATE INDEX idx_movimentacoes_data_hora ON movimentacoes (data_hora);

-- =====================================================================
-- Tabela: vendas
-- Uma venda no balcão: ao ser finalizada, gera automaticamente uma
-- movimentação de SAÍDA (motivo VENDA) para cada item.
-- =====================================================================
CREATE TABLE vendas (
    id                        BIGSERIAL PRIMARY KEY,
    usuario_id                BIGINT        NOT NULL,
    valor_total               NUMERIC(12,2) NOT NULL,
    forma_pagamento           VARCHAR(10)   NOT NULL,
    status                    VARCHAR(20)   NOT NULL DEFAULT 'CONCLUIDA',
    data_hora                 TIMESTAMP     NOT NULL DEFAULT now(),
    cancelada_em              TIMESTAMP,
    cancelado_por_usuario_id  BIGINT,

    CONSTRAINT fk_vendas_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_vendas_cancelado_por
        FOREIGN KEY (cancelado_por_usuario_id) REFERENCES usuarios (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_vendas_valor_total_nao_negativo CHECK (valor_total >= 0),
    CONSTRAINT ck_vendas_forma_pagamento CHECK (forma_pagamento IN ('DINHEIRO', 'PIX', 'CARTAO')),
    CONSTRAINT ck_vendas_status CHECK (status IN ('CONCLUIDA', 'CANCELADA'))
);

CREATE INDEX idx_vendas_usuario ON vendas (usuario_id);
CREATE INDEX idx_vendas_data_hora ON vendas (data_hora);

-- =====================================================================
-- Tabela: itens_venda
-- Preço unitário é gravado no momento da venda (não referencia o preço
-- atual do produto, que pode mudar depois).
-- =====================================================================
CREATE TABLE itens_venda (
    id              BIGSERIAL PRIMARY KEY,
    venda_id        BIGINT        NOT NULL,
    produto_id      BIGINT        NOT NULL,
    quantidade      INTEGER       NOT NULL,
    preco_unitario  NUMERIC(12,2) NOT NULL,

    CONSTRAINT fk_itens_venda_venda
        FOREIGN KEY (venda_id) REFERENCES vendas (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_itens_venda_produto
        FOREIGN KEY (produto_id) REFERENCES produtos (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_itens_venda_quantidade_positiva CHECK (quantidade > 0),
    CONSTRAINT ck_itens_venda_preco_nao_negativo CHECK (preco_unitario >= 0)
);

CREATE INDEX idx_itens_venda_venda ON itens_venda (venda_id);
CREATE INDEX idx_itens_venda_produto ON itens_venda (produto_id);

-- =====================================================================
-- Tabela: alertas
-- =====================================================================
CREATE TABLE alertas (
    id            BIGSERIAL PRIMARY KEY,
    produto_id    BIGINT      NOT NULL,
    tipo          VARCHAR(20) NOT NULL,
    mensagem      VARCHAR(255) NOT NULL,
    data_criacao  TIMESTAMP   NOT NULL DEFAULT now(),
    lido          BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_alertas_produto
        FOREIGN KEY (produto_id) REFERENCES produtos (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT ck_alertas_tipo CHECK (tipo IN ('ESTOQUE_BAIXO', 'ESTOQUE_EXCESSO', 'ESTOQUE_OBSOLETO', 'RUPTURA'))
);

CREATE INDEX idx_alertas_produto ON alertas (produto_id);
CREATE INDEX idx_alertas_lido ON alertas (lido);

-- =====================================================================
-- Tabelas: SPRING_SESSION / SPRING_SESSION_ATTRIBUTES
-- Sessão HTTP persistida no banco (Spring Session JDBC), para que o login
-- sobreviva a um restart da aplicação. Schema oficial do spring-session-jdbc.
-- =====================================================================
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID              CHAR(36)     NOT NULL,
    SESSION_ID              CHAR(36)     NOT NULL,
    CREATION_TIME           BIGINT       NOT NULL,
    LAST_ACCESS_TIME        BIGINT       NOT NULL,
    MAX_INACTIVE_INTERVAL   INT          NOT NULL,
    EXPIRY_TIME             BIGINT       NOT NULL,
    PRINCIPAL_NAME          VARCHAR(100),

    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID  CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME       VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES      BYTEA        NOT NULL,

    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);

-- =====================================================================
-- Dados iniciais (seed) - motivos de movimentação padrão
-- =====================================================================
INSERT INTO motivos_movimentacao (descricao) VALUES
    ('COMPRA'),
    ('VENDA'),
    ('DEVOLUCAO'),
    ('AVARIA'),
    ('AJUSTE_INVENTARIO'),
    ('ESTORNO');

-- Usuário administrador inicial (senha deve ser definida via aplicação,
-- este hash é apenas um placeholder e deve ser trocado antes de uso real).
-- INSERT INTO usuarios (nome, email, senha_hash, cargo) VALUES
--     ('Administrador', 'admin@prostock.com', '<hash_bcrypt>', 'ADMINISTRADOR');
