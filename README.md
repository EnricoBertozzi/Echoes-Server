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

2. Copie o arquivo `.env.example` e preencha as variáveis de ambiente.

```bash
cp ./.env.example ./.env
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

Depois de gerar o keystore, salve-o em `./certs/keystore.p12` e, no `.env`, defina:

```bash
USE_SSL=true
SSL_KEYSTORE_PATH=/app/certs/keystore.p12   # Docker; com Maven use ./certs/keystore.p12
SSL_KEYSTORE_PASSWORD=<senha_de_armazenamento>
SSL_ALIAS=<cert>
# Atrás de proxy TLS, opcional:
FORWARD_HEADERS_STRATEGY=framework
```

> **Atenção:** o ESP32 valida o certificado do servidor contra as CAs públicas
> (bundle do ESP-IDF). Um certificado autoassinado gerado pelo `keytool` **não**
> será aceito pelo firmware. Para o dispositivo, exponha a API por um proxy TLS
> com certificado de CA pública (ex.: Let's Encrypt) e use `https://` em
> `API_BASE_URL`. `API_ALLOW_PLAIN_HTTP 1` no `Config.h` só deve ser usado em
> laboratório isolado.

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

5. Em desenvolvimento, defina `SWAGGER_ENABLED=true` no `.env` e acesse a documentação em [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html). Fora de desenvolvimento a documentação fica desligada.

# Banco de dados e schema

O schema do banco de dados é gerenciado pelo **Hibernate/JPA** (`spring.jpa.hibernate.ddl-auto=update`), sem uso de Flyway/migrations versionadas no startup.

- Em desenvolvimento, o Hibernate aplica automaticamente ajustes de schema compatíveis com as entidades mapeadas.
- Os arquivos Markdown em `src/main/resources/legal/` permanecem como fonte de conteúdo dos documentos legais, mas a publicação automática via migrations foi removida junto com o Flyway. Caso necessário, a carga/publicação desses documentos deve ser tratada por outro mecanismo (seed/script/manual).

