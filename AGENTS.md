# Orientações do projeto

## Objetivo

Desenvolver, por etapas, um Chat Multiusuário com Histórico para o trabalho de
Programação Orientada a Objetos do 2º bimestre de 2026.

## Tecnologias obrigatórias

- Java 17 ou superior.
- Maven.
- Programação Orientada a Objetos.
- Sockets TCP e threads, quando a etapa autorizar.
- JDBC com H2.
- JUnit 5.
- Git.
- Arquitetura em camadas.

Não utilizar Spring Boot, Hibernate, JPA, microserviços ou frameworks
desnecessários.

## Requisitos do projeto completo

1. Servidor com `ServerSocket` e laço `accept()`.
2. Uma thread por conexão.
3. `ClienteHandler` implementando `Runnable`.
4. Lista de clientes com acesso sincronizado.
5. Broadcast para clientes conectados.
6. Mensagem com remetente, conteúdo e horário.
7. Persistência JDBC.
8. Repository isolando o SQL.
9. Recuperação do histórico ao conectar.
10. Desconexão segura.
11. No mínimo cinco testes JUnit sem rede real.
12. Organização em pacotes e responsabilidades bem definidas.

## Direção das dependências

- `model` não depende de sockets nem JDBC.
- `service` não importa `java.net` nem `java.sql`.
- O SQL fica somente na implementação em `persistence.jdbc`.
- A comunicação TCP fica na camada `server` e na aplicação `client`.
- Interfaces e contratos devem ser preferidos quando trouxerem benefício claro,
  sem criar abstrações desnecessárias.

## Pacotes e responsabilidades

- `br.edu.chat.model`: domínio (`Usuario`, `Mensagem` e
  `MensagemInvalidaException`).
- `br.edu.chat.persistence`: contratos de persistência (`MensagemRepository`).
- `br.edu.chat.persistence.jdbc`: conexão e implementação JDBC.
- `br.edu.chat.service`: casos de uso e registro de clientes.
- `br.edu.chat.server`: servidor TCP, protocolo e handler.
- `br.edu.chat.client`: cliente TCP.
- `br.edu.chat.config`: configurações, somente se forem necessárias.

## Processo de desenvolvimento

- Trabalhar uma etapa por vez e aguardar autorização para iniciar a próxima.
- Analisar arquivos existentes antes de alterar ou remover qualquer conteúdo.
- Explicar a responsabilidade de cada classe criada.
- Manter o código simples e compreensível para um estudante.
- Criar testes antes ou junto das regras de negócio.
- Não implementar funcionalidades extras sem autorização.
- Executar `mvn compile` e `mvn test` após mudanças relevantes.
- Não afirmar que algo funciona sem executar a verificação correspondente.
