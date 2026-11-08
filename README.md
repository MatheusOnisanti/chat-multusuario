# Chat Multiusuário com Histórico

Projeto acadêmico de Programação Orientada a Objetos para um chat multiusuário
em Java, com comunicação TCP, broadcast e histórico de mensagens persistido.

## Tecnologias

- Java 17
- Maven e Maven Wrapper
- Sockets TCP e threads
- JDBC com H2
- JUnit 5
- Arquitetura em camadas

O projeto não utiliza Spring Boot, Hibernate, JPA, microsserviços ou frameworks
desnecessários.

## Organização e dependências

O pacote raiz é `br.edu.chat`.

- `model`: `Usuario`, `Mensagem` e `MensagemInvalidaException`. O domínio não depende de sockets ou JDBC.
- `persistence`: contratos de persistência, como `MensagemRepository`.
- `persistence.jdbc`: `ConnectionFactory`, SQL e `JdbcMensagemRepository`.
- `service`: `ChatService`, `ConexaoCliente` e `ClientRegistry`. Não importa `java.net` nem `java.sql`.
- `server`: `Servidor`, `ClienteHandler` e `Protocolo`, responsáveis pela comunicação TCP.
- `client`: aplicação cliente TCP, apresentação das mensagens e interface gráfica Swing (`ClienteChatApp` e `ClienteChatFrame`).
- `config`: reservado para configurações centralizadas, se necessário.

O SQL permanece isolado na implementação JDBC. A camada de domínio permanece
independente de rede e persistência.

## Situação das etapas

### Etapa 1 — Preparação e arquitetura

Concluída. O projeto Maven foi configurado para Java 17, com H2, JUnit 5 e a
estrutura inicial de pacotes e documentação.

### Etapa 2 — Modelo de domínio e testes

Concluída. Foram implementados usuários, mensagens, validações de domínio,
exceção específica e testes unitários.

### Etapa 3 — Servidor TCP e broadcast

Concluída. O projeto possui servidor com `ServerSocket` e `accept()`, uma thread
por conexão, `ClienteHandler` implementando `Runnable`, registro thread-safe,
broadcast, protocolo textual e encerramento seguro.

### Etapa 4 — Persistência e histórico

Concluída. Mensagens são salvas com JDBC antes do broadcast, o histórico é
recuperado quando um cliente se conecta e falhas relevantes são registradas com
`java.util.logging`.

## Persistência

O servidor utiliza H2 em arquivo:

```text
jdbc:h2:file:./data/chat
```

Os testes utilizam bancos H2 em memória ou arquivos temporários isolados. Cada
operação JDBC abre sua própria conexão e utiliza try-with-resources para fechar
conexões, comandos e resultados.

O histórico é ordenado por `horario ASC, id ASC`. Um lock de coordenação organiza
a recuperação do histórico, o salvamento e o broadcast, evitando mensagens
duplicadas ou perdidas na entrada de novos clientes.

## Funcionalidades implementadas

- Validação de usuários e mensagens.
- Atendimento simultâneo de conexões TCP.
- Broadcast para os demais clientes conectados.
- Preservação do caractere `|` no conteúdo das mensagens.
- Persistência JDBC com H2.
- Recuperação de histórico ao conectar.
- Tratamento de falhas individuais de clientes.
- Encerramento seguro de sockets, handlers e servidor.
- Testes unitários, testes de concorrência, testes TCP e testes de persistência.

## Comandos de compilação e testes

O projeto exige Java 17 ou superior. No Windows, use o Maven Wrapper incluído no
projeto, sem depender de uma instalação global do Maven. Execute os comandos no
PowerShell, a partir da raiz do projeto:

```powershell
.\mvnw.cmd -version
.\mvnw.cmd compile
.\mvnw.cmd test
.\mvnw.cmd clean test
```

## Execução da aplicação

Compile o projeto antes de iniciar as aplicações:

```powershell
.\mvnw.cmd compile
.\mvnw.cmd dependency:build-classpath "-Dmdep.outputFile=target\classpath.txt"
$chatClassPath = "target\classes;" + (Get-Content target\classpath.txt -Raw).Trim()
```

Em um terminal PowerShell, inicie o servidor TCP na porta 5000:

```powershell
java -cp $chatClassPath br.edu.chat.server.ServidorApp
```

Com o servidor em execução, abra dois outros terminais PowerShell e inicie um
cliente Swing em cada terminal:

```powershell
$chatClassPath = "target\classes;" + (Get-Content target\classpath.txt -Raw).Trim()
java -cp $chatClassPath br.edu.chat.client.ClienteChatApp
```

Em cada janela, informe `localhost` como host, `5000` como porta e um nome
diferente, por exemplo `Ana` no primeiro cliente e `Bruno` no segundo. Depois,
clique em **Conectar** para testar o chat entre as duas instâncias.

## Requisitos funcionais previstos

O projeto completo prevê servidor com `ServerSocket` e laço `accept()`, uma
thread por conexão, `ClienteHandler` como `Runnable`, lista de clientes com
acesso seguro, broadcast, mensagens com remetente, conteúdo e horário,
persistência JDBC, repository isolando o SQL, recuperação de histórico,
desconexão segura e no mínimo cinco testes JUnit sem rede real.

## Limitações conhecidas e funcionalidades futuras

- O lock de coordenação é síncrono; um cliente lento pode atrasar temporariamente o histórico ou o broadcast.
- Não há filas assíncronas para clientes lentos.
- Não há autenticação de usuários.
- Não foram adicionados frameworks ou funcionalidades além do escopo aprovado.
