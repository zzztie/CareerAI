# U53 | CareerHub + CareerAI local Docker Compose

This Compose stack starts CareerHub, CareerAI, MySQL, Redis, pgvector, RabbitMQ and Ollama on one private network. It is a **fresh local environment**. It does not import the data already stored by the IDEA/Windows setup; old resume IDs and jobs will not appear automatically.

## Layout and prerequisites

Keep the projects as sibling folders:

```text
projects/
├── CareerHub/
└── CareerAI/
    ├── compose.yaml
    └── careerai-server/
```

Use Docker Engine with the Compose plugin from WSL. From Ubuntu, this project is normally at `/mnt/d/projects/CareerAI`. The two Java images are built with Maven and Java 17. The first Ollama start downloads `bge-m3` (about 1.1 GB), so it can take time.

## First start

1. Copy `.env.example` to `.env` in this directory and fill every value locally. `JWT_SECRET` must be **standard Base64 encoding of at least 32 random bytes** (for example, 32 random bytes become a 44-character Base64 string). CareerHub calls `Decoders.BASE64.decode(secret)` and then `Keys.hmacShaKeyFor`, so an ordinary 32-character password or Base64URL value containing `_` is invalid. Use distinct passwords for the databases and RabbitMQ. Do not send `.env` to chat or commit it. `DEEPSEEK_API_KEY` is the same kind of key used by the IDEA run configuration; it is not stored in the Compose file.
2. Stop the IDEA runs of CareerHub and CareerAI so ports 8080 and 8081 are free. Existing standalone pgvector, Ollama or RabbitMQ containers may keep running because this Compose file does not publish their ports; the new stack uses separate data volumes and will use extra resources.
3. In **Ubuntu/WSL**, run:

```bash
cd /mnt/d/projects/CareerAI
sudo docker compose config --quiet
sudo docker compose up --build -d
sudo docker compose ps
```

### Reuse an existing local Ollama model volume

If this machine already has `bge-m3` in the Docker volume named
`careerai-ollama`, and model downloads fail because a VPN or DNS proxy
returns a fake `198.18.x.x` address, use the local override instead of
re-downloading. Stop the old standalone Ollama container first so two
Ollama servers do not write to the same volume at the same time:

```bash
cd /mnt/d/projects/CareerAI
sudo docker stop careerai-ollama
sudo docker compose -f compose.yaml -f compose.reuse-ollama.yaml config --quiet
sudo docker compose -f compose.yaml -f compose.reuse-ollama.yaml up --build -d
sudo docker compose -f compose.yaml -f compose.reuse-ollama.yaml ps
```

The override maps `ollama_data` to the existing `careerai-ollama` volume.
Its one-shot `ollama-pull` service runs `ollama show bge-m3` to verify
the model is available locally; it does not contact the model registry.
Use both `-f` options for later `logs`, `ps`, and `down` commands for
this local stack. The base `compose.yaml` remains the path for a new
machine with normal model-registry access.

Compose waits for MySQL, Redis, pgvector, RabbitMQ and Ollama health checks. `ollama-pull` must complete successfully before CareerAI starts. The two application ports are available only on the local host at `127.0.0.1:8080` and `127.0.0.1:8081`.

## Check and troubleshoot

```bash
sudo docker compose ps
sudo docker compose logs --tail=100 careerhub careerai
sudo docker compose logs --tail=100 ollama-pull mysql pgvector rabbitmq
curl -i http://127.0.0.1:8080/jobs/count
```

`/jobs/count` should return a valid response from CareerHub and its new MySQL database. The fresh database starts with no user, resume or job records; register a user and add demo jobs through the application before running a skill-gap analysis. A CareerAI request that requires DeepSeek also needs a working external API key and network connection.

Inside the network, CareerAI reaches CareerHub at `http://careerhub:8080`, not `localhost:8080`. It reaches MySQL at `mysql:3306`, pgvector at `pgvector:5432`, RabbitMQ at `rabbitmq:5672` and Ollama at `ollama:11434`.

MySQL init scripts create both `careerhub` and `careerai`; PostgreSQL init scripts create the vector extension, `rag_chunk` and `rag_import_task`. These scripts run only when their database volumes are empty. The named volumes also keep uploaded resume PDFs, imported knowledge files, the Ollama model and RabbitMQ state across ordinary container restarts.

`sudo docker compose down` stops this stack and retains its volumes. Do not add `-v` unless you deliberately intend to erase the new stack's database and uploaded files.

After changing an application value in `.env`, recreate that application container with the same Compose file selection. A running container keeps the environment it received when it was created; simply making another API request or restarting that same container does not import the new `.env` value.

## Current limits

- The Compose stack is for local learning and demonstrations. It does not migrate Windows/IDEA data or keys.
- `ollama/ollama:latest` can change between installations; the model name is fixed to `bge-m3`.
- Both Java applications passed local Maven packaging and image builds. On this machine, the reuse override completed its local `bge-m3` check; CareerHub returned `GET /jobs/count` = `0` (HTTP 200), and CareerAI created a test conversation in its MySQL database. Those checks cover startup and basic database access, not a full DeepSeek/RAG workflow.
