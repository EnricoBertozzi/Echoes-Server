# Introdução

O uso de animais reais na medicina veterinária é um tema debatido entre os profissionais da área. Como forma preservar o bem-estar animal, normas e alternativas foram desenvolvidas com o objetivo de reduzir e substituir o uso de animais durante atividades pedagógicas e científicas, além de aprimorar os métodos já utilizados.

Nesse contexto, o uso de simuladores didáticos são eficazes no treinamento de habilidades clínicas, contribuindo para a redução da ansiedade e permitindo a repetição ilimitada das técnicas.

O projeto de um conjunto consiste no desenvolvimento de um simulador canino de baixo custo para o estudo de ausculta pulmonar e cardíaca, utilizando de componentes relacionados a Desenvolvimento Mobile, Web e Sistemas Embarcados com Internet das Coisas (IoT).

# Tecnologias

- Java 21.
- Spring Boot.
- Spring JPA.
- Spring Security
- JWT.
- MySQL.
- Redis.
- Docker.

# Como executar

1. Clone o repositório online do github em sua máquina.

```bash
git clone https://github.com/EnricoBertozzi/Echoes-Server.git
cd Echoes-Server
```

2. Copie o arquivo `env.example` e preencha as variáveis de ambiente.

```bash
cp ./env.example ./.env
```

3. Gere o conjunto de chaves e o certificado para comunicação HTTPS/TLS.

```bash
# Gerando chaves.
keytool -genkeypair -alias [cert] -keyalg [algoritmo] -storetype [type] -keystore [nome_do_repositorio] -storepass [senha_de_armazenamento] -keypass [senha_da_chave] -dname "CN=[host], OU=[unidade], O=[organização], L=[região], ST=[uf], C=[pais]"
```

```bash
# Exportando certificado.
keytool -exportcert -keystore [nome_do_repositorio] -alias [nome] -file [nome_do_arquivo] -storepass [senha_do_repositorio]
```

4. Execute o projeto com Maven, Docker ou Docker Compose.

**MAVEN**
4.1 Inicie os servidores dos bancos de dados MySQL e Redis. Após sua inicialização, execute:

```bash
# Execute o plugin de inicialização do spring boot pelo Maven.
./mvnw spring-boot:run
```

**DOCKER**
4.2 Realize o build da image docker, crie e execute seu container.

```bash
# Build a imagem
docker build -t echoes-image .

# Crie e execute o container
docker run -di --name echoes-container -p 8080:8080 echoes-image
```

**DOCKER COMPOSE**
4.3 Suba todos os conteineres com `docker-compose`.

```bash
# Execute o docker compose
docker-compose up -d
```

5. Acesse a documentação dos endpoints do projeto pelo endereço [https://localhost/swagger-ui/index.html](https://localhost/swagger-ui/index.html)

# Versionamento de documentos legais (Flyway)

Os documentos legais (termos de uso, política de privacidade, política de cookies e registro das operações de tratamento) são versionados e publicados via **Flyway**, executado automaticamente no startup da aplicação.

A relação é explícita:

```text
arquivo Markdown
       ↑
       │ referência
       │
Java Migration
       │
       ↓
Banco
```

Estrutura:

```text
src/main/resources/legal/
├── cookies-policy/1.0.0.md     (fonte de conteúdo)
├── privacy-policy/1.0.0.md
├── terms/1.0.0.md
└── operations/1.0.0.md

src/main/java/com/n0hana/echoes_server/db/migration/
├── LegalDocumentMigration.java          (base reutilizável)
├── V2__publish_cookies_policy_1_0_0.java
├── V3__publish_privacy_policy_1_0_0.java
├── V4__publish_terms_1_0_0.java
└── V6__publish_operations_1_0_0.java

src/main/resources/db/migration/
├── V1__create_legal_documents_table.sql               (estrutura)
└── V5__add_processing_operations_to_terms_type.sql    (estrutura)
```

## Migrations são imutáveis

Uma migration **já aplicada nunca deve ser alterada**. Para corrigir ou evoluir o conteúdo de um documento:

1. Crie o novo Markdown em `src/main/resources/legal/<tipo-do-documento>/<versao>.md`, ex.:

   ```text
   legal/terms/1.1.0.md
   ```

2. Crie a Java Migration correspondente, ex.:

   ```text
   V7__publish_terms_1_1_0.java
   ```

3. A migration deve referenciar o Markdown pelo classpath (`legal/terms/1.1.0.md`) e usar `LegalDocumentMigration.publishDocument(...)` com o `DocumentType` existente e o novo `version`.

4. Os dois arquivos devem fazer parte do **mesmo commit/PR**.

As migrações Java herdam de `LegalDocumentMigration`, que:
- lê o Markdown do classpath em UTF-8 (falha se o arquivo não existir) e não altera o conteúdo;
- insere o documento na tabela `terms` via `PreparedStatement`, com o status `PUBLISHED` e o `DocumentType` do domínio existente;
- aloca os ids pela mesma sequência do Hibernate (`terms_seq`), evitando colisão com ids futuros.

## Novo `DocumentType`: a migration de estrutura vem ANTES da publicação

A coluna `terms.type` é um `ENUM` do MySQL, e o Hibernate (`ddl-auto=update`) só a altera **depois** do Flyway. Como a publicação roda **durante** o Flyway, um `DocumentType` novo cujo `ENUM` ainda não aceite o valor faz o `INSERT` ser rejeitado e a migration é marcada como `failed` — o que impede **todas** as startups seguintes com
`Detected failed migration to version N`.

Ao adicionar uma constante em `DocumentType`, crie as duas migrations, nesta ordem:

1. A migration SQL que **amplia o `ENUM`** (aditiva, preservando os valores existentes e os documentos já publicados), com versão **menor** que a da publicação:

   ```sql
   ALTER TABLE terms
       MODIFY COLUMN type enum('COOKIES_POLICY', /* ... */, 'NOVO_TIPO') DEFAULT NULL;
   ```

2. A Java Migration que publica o documento, com a versão seguinte.

O teste `FlywayMigrationIntegrationTest.todoDocumentTypeDoDominioEhAceitoPeloEnumDaColunaType` garante que todo `DocumentType` do domínio é aceito pelo `ENUM` após as migrations.

## Comportamento

- Banco vazio: o Flyway cria a estrutura (`V1`) e publica os documentos 1.0.0 (`V2`–`V6`); só migrations pendentes são executadas.
- Reinicialização: o Flyway reconhece `flyway_schema_history` e não duplica documentos.
- Novos documentos: apenas a nova migration é aplicada; as anteriores permanecem intactas.
- Bancos existentes sem histórico Flyway são baselineados na versão 0 (`spring.flyway.baseline-on-migrate=true`), preservando o schema já criado pelo Hibernate.

## Recuperando de uma migration marcada como `failed`

O `validate` roda **antes** de qualquer migration, então uma migration `failed` trava o startup e o próprio `migrate` não consegue se recuperar. Remova a linha com falha de `flyway_schema_history` (ou rode `flyway repair`):

```sql
-- com falha: o 'success = 0' é o que trava a validação
SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;

DELETE FROM flyway_schema_history WHERE success = 0;
```

Migrations Java só gravam DML, então a transação é revertida e não sobra documento pela metade — a migration pode ser reexecutada com segurança. Para migrations SQL com DDL (que o MySQL não reverte), limpe também as mudanças parciais antes de rodar `repair`.

