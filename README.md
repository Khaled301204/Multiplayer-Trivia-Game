# Multiplayer Trivia Game (Java Sockets + Multithreading)

This project runs from the command line using two entry points:
- `server.TriviaServer` (server)
- `client.TriviaClient` (client)

The server loads required files from the `data/` folder on startup:
`config.txt`, `users.txt`, `questions.txt`, `scores.txt`.

## Build (Windows PowerShell)

From the project root (`C:\Users\user\Downloads\TriviaGame\TriviaGame`):

```powershell
# Compile all sources into ./out
mkdir out -ErrorAction SilentlyContinue | Out-Null

# PowerShell needs --% so @sources.txt is passed literally to javac
Get-ChildItem -Recurse -Filter *.java |
  ForEach-Object { $_.FullName } |
  Set-Content -Encoding ASCII .\sources.txt

javac --% -d out -sourcepath . @sources.txt
```

## Run server (PowerShell)

```powershell
# Default: port=5555, dataDir=data
java -cp out server.TriviaServer

# Or specify port and data directory
java -cp out server.TriviaServer 5555 data
```

## Run client (PowerShell)

Open a new terminal (you can run multiple clients for multiplayer), then:

```powershell
# Default: host=localhost, port=5555
java -cp out client.TriviaClient

# Or specify host and port
java -cp out client.TriviaClient localhost 5555
```

## Notes
- To quit at any prompt, type `-`
- For multiplayer/team play, start multiple clients and use the in-game menus



