# RTS Java Server

Authoritative game server for a 2D real-time strategy game, built with Java and Spring Boot.

The server is the single source of truth: clients only send commands, and the server validates them, updates the game state and broadcasts the result to all players.

**Game client:** [rts-game](https://github.com/eSquire-qq/rts-game)

## Features

- Authoritative game state owned and updated by the server
- Validation of every player command before it is applied
- [Game loop with fixed tick rate: X ticks/second]
- [Multiple players in one match / lobbies]
- [State synchronization to clients via WebSocket/TCP/UDP]
- [Persistence with PostgreSQL/MySQL/H2]

## Tech stack

- Java [17/21]
- Spring Boot [version]
- Maven (wrapper included, no local Maven needed)
- [WebSocket / TCP / REST]
- [Database]

## Architecture

```
 Unity client ──(player commands)──▶ Java server
      ▲                                  │
      │                         validate + apply to
      │                            game state
      └──────(state updates)─────────────┘
```

[2–4 sentences: how commands flow, what the game loop does, how conflicts and invalid commands are handled.]

## Getting started

**Requirements:** JDK [17/21]

```bash
git clone https://github.com/eSquire-qq/rts-java-server.git
cd rts-java-server

# Linux / macOS
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

The server starts on port `[8080]`. Configuration is in `src/main/resources/application.properties`.

To build a runnable jar:

```bash
./mvnw clean package
java -jar target/*.jar
```

## Project structure

```
src/main/java/...
  [controller/    – network entry points]
  [service/       – game logic and validation]
  [model/         – game state and entities]
```

## Author

Artem Teslia – [GitHub](https://github.com/eSquire-qq)
