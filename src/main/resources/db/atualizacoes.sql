-- =====================================================================
-- ProStock - atualizações de schema para bancos criados com uma versão
-- anterior do database/schema.sql.
--
-- Executado automaticamente a cada inicialização, ANTES do Hibernate validar
-- as entidades (ver config/AtualizacaoDeSchema.java). Todo comando aqui deve
-- ser idempotente: rodar de novo num banco já atualizado não pode mudar nada.
-- =====================================================================

-- Controle de concorrência otimista em produtos (Produto.versao / @Version).
ALTER TABLE produtos ADD COLUMN IF NOT EXISTS versao BIGINT NOT NULL DEFAULT 0;

-- Alerta de excesso de estoque (TipoAlerta.ESTOQUE_EXCESSO).
ALTER TABLE alertas DROP CONSTRAINT IF EXISTS ck_alertas_tipo;
ALTER TABLE alertas ADD CONSTRAINT ck_alertas_tipo
    CHECK (tipo IN ('ESTOQUE_BAIXO', 'ESTOQUE_EXCESSO', 'ESTOQUE_OBSOLETO', 'RUPTURA'));
