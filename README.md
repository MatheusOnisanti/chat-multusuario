# Chat Multiusuário com Histórico

Projeto acadêmico de Programação Orientada a Objetos para um chat multiusuário
em Java, com comunicação TCP, broadcast e histórico de mensagens persistido.

## Tecnologias

- Java 17 ou superior
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

## Autenticação V2 e TLS

O cliente atualizado utiliza os comandos `AUTENTICAR_V2` e `CADASTRAR_V2`. O
cadastro solicita login, nome e senha; depois, o login normal utiliza apenas
login e senha. Após a autenticação, o servidor retorna o nome canônico
cadastrado, usado pelo cliente para identificar as próprias mensagens. Os
comandos legados continuam disponíveis para compatibilidade.

O servidor e cada cliente precisam destas variáveis de ambiente na sessão atual
do PowerShell:

```powershell
$env:CHAT_TLS_KEYSTORE = "<caminho-local-do-keystore-PKCS12>"
$env:CHAT_TLS_KEYSTORE_PASSWORD = "<senha-local-do-keystore>"
$env:CHAT_TLS_TRUSTSTORE = "<caminho-local-do-truststore-PKCS12>"
$env:CHAT_TLS_TRUSTSTORE_PASSWORD = "<senha-local-do-truststore>"
```

Os valores acima são exemplos. Não coloque senhas reais neste arquivo, no
histórico de comandos ou no Git. Os certificados TLS e as senhas protegidas
devem ser preparados localmente em cada computador e não devem ser enviados ao
GitHub.

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

Em uma instalação nova, ao iniciar o servidor, o H2 cria um banco novo em
`./data/chat` e inicializa as tabelas necessárias. A pasta `data/` é local e
ignorada pelo Git; não é necessário copiar o banco de outro computador.

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
projeto, sem depender de uma instalação global do Maven. Na primeira execução,
o Wrapper precisa de acesso à internet para baixar a distribuição do Maven.
Execute os comandos no PowerShell, a partir da raiz do projeto:

```powershell
.\mvnw.cmd -version
.\mvnw.cmd compile
.\mvnw.cmd test
.\mvnw.cmd clean test
```

## Execução da aplicação

Para preparar certificados locais de demonstração, abra o PowerShell na raiz do
projeto e execute:

```powershell
. .\scripts\preparar-tls-demo.ps1
```
Execute
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

O script exige um JDK 17 ou superior, verifica `java.exe` e `keytool.exe`, cria
os arquivos PKCS12 em `tls-local\demo` e protege a senha local com DPAPI. Ele
não sobrescreve uma preparação existente. Execute a preparação sob o mesmo
usuário Windows que fará a apresentação.

Em um terminal PowerShell dedicado, inicie o servidor TCP na porta 5000:

```powershell
.\scripts\iniciar-demonstracao.ps1 -Role Server
```

O script compila com o Maven Wrapper e gera `target\classpath.txt` somente se
esses artefatos ainda não existirem. O Maven Wrapper pode precisar de internet
na primeira execução. O servidor usa o banco relativo `./data/chat`.

Com o servidor em execução, abra dois outros terminais PowerShell e execute o
mesmo comando em cada um para iniciar um cliente Swing:

```powershell
.\scripts\iniciar-demonstracao.ps1 -Role Client
```

Em cada janela, informe `localhost` como host e `5000` como porta. Para uma
conta nova, marque **Cadastrar** e informe login, nome e senha. Depois do
cadastro, ou para uma conta existente, deixe **Cadastrar** desmarcado e informe
login e senha. O nome exibido após o login é o nome canônico retornado pelo
servidor. Clique em **Conectar** para testar o chat entre as duas instâncias.

Para encerrar o servidor manualmente, volte ao terminal dedicado e pressione
`Ctrl+C` ao final da apresentação. Em um computador novo, deixe o H2 criar uma
base local vazia em vez de copiar o banco real.

## Execução rápida para apresentação — Windows PowerShell

Siga os passos abaixo em um computador novo. Os comandos não dependem do nome
do usuário nem do caminho onde o projeto foi extraído.

1. Instale um JDK 17 ou superior. Os comandos `java` e `keytool` precisam estar
   disponíveis no PowerShell, e é necessária uma conexão com a internet na
   primeira compilação, para o Maven Wrapper baixar o Maven.
2. Abra o PowerShell na raiz do projeto, isto é, na pasta que contém o arquivo
   `mvnw.cmd`.
3. Verifique o ambiente Java:

   ```powershell
   java -version
   keytool -help
   ```

   Se algum comando não for encontrado, localize o JDK instalado pelo Windows
   ou com `Get-Command java.exe`. Configure `JAVA_HOME` apontando para a pasta
   raiz do JDK, sem incluir `\bin`, e adicione sua pasta `bin` ao `PATH`:

   ```powershell
   $env:JAVA_HOME = "C:\caminho\para\o\jdk"
   $env:Path = "$env:JAVA_HOME\bin;$env:Path"
   ```

   Substitua apenas o caminho de exemplo pelo caminho encontrado no computador
   da apresentação. Cada terminal novo pode precisar repetir essa configuração.
4. Libere a execução dos scripts somente na sessão atual:

   ```powershell
   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
   ```

5. Na primeira execução, prepare o TLS:

   ```powershell
   .\scripts\preparar-tls-demo.ps1
   ```

6. Em um terminal PowerShell, ainda na raiz do projeto, inicie o servidor:

   ```powershell
   .\scripts\iniciar-demonstracao.ps1 -Role Server
   ```

7. Em um segundo terminal PowerShell, também na raiz do projeto, inicie o
   primeiro cliente:

   ```powershell
   .\scripts\iniciar-demonstracao.ps1 -Role Client
   ```

8. Em um terceiro terminal PowerShell, também na raiz do projeto, inicie o
   segundo cliente:

   ```powershell
   .\scripts\iniciar-demonstracao.ps1 -Role Client
   ```

   Cada terminal precisa estar na raiz do projeto e pode precisar configurar
   sua própria política de execução e seu próprio ambiente Java.
9. Nos dois clientes, use **Cadastrar** para criar usuários diferentes, com
   logins, nomes e senhas distintos. Depois, conecte cada cliente com seu
   respectivo usuário, usando `localhost` e a porta `5000`. Envie mensagens de
   um cliente e confirme que elas aparecem no outro.

### Solução de problemas na apresentação

- **JDK ou `keytool` não encontrado:** confirme `java -version` e
  `keytool -help`, configure `JAVA_HOME` para a raiz do JDK e inclua
  `JAVA_HOME\bin` no `PATH` da sessão.
- **Scripts bloqueados:** execute novamente
  `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` em cada terminal
  PowerShell usado na apresentação.
- **Porta 5000 ocupada:** encerre o programa que já está usando a porta ou
  finalize a apresentação anterior. Para identificar o processo, use
  `Get-NetTCPConnection -LocalPort 5000 -State Listen`.
- **Primeira compilação Maven:** mantenha a internet disponível; o Maven
  Wrapper baixa sua distribuição e dependências na primeira execução. Aguarde
  a conclusão e tente novamente se o download for interrompido.

## Requisitos funcionais previstos

O projeto completo prevê servidor com `ServerSocket` e laço `accept()`, uma
thread por conexão, `ClienteHandler` como `Runnable`, lista de clientes com
acesso seguro, broadcast, mensagens com remetente, conteúdo e horário,
persistência JDBC, repository isolando o SQL, recuperação de histórico,
desconexão segura e no mínimo cinco testes JUnit sem rede real.

## Limitações conhecidas e funcionalidades futuras

- O lock de coordenação é síncrono; um cliente lento pode atrasar temporariamente o histórico ou o broadcast.
- Não há filas assíncronas para clientes lentos.
- A autenticação V2 e o cadastro persistem as credenciais no banco H2 local; os
  dados de uma máquina não são compartilhados automaticamente com outra.
- Não foram adicionados frameworks ou funcionalidades além do escopo aprovado.
