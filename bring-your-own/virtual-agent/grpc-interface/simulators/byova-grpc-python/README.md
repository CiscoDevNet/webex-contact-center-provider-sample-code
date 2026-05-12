# BYoVA gRPC Sample (Python)

A reference implementation of the Webex Contact Center **Bring-Your-Own-Virtual-Agent (BYoVA)** gRPC interface, written in Python using `grpcio`. It exposes the `VoiceVirtualAgent` bidirectional-streaming service plus the unary `ListVirtualAgents` RPC, and demonstrates how to handle the three input types the WxCC platform sends:

- **Event input** (`SESSION_START`, `SESSION_END`, `NO_INPUT`, …)
- **Audio input** — µ-law 8 kHz caller audio, buffered, and echoed back in chunked mode.
- **DTMF input** — `5` triggers an agent transfer, `6` ends the session.

The sample is deliberately self-contained — prompts are loaded from `code/src/config/audio/` — so it can be run as a smoke test for a BYoVA integration without any external services.

For the underlying call-flow contract and sequence diagrams, see the [parent README](../README.md).

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Manual Setup](#manual-setup)
- [Project Layout](#project-layout)
- [Proto Code Generation](#proto-code-generation)
- [How It Works](#how-it-works)
- [Configuration](#configuration)
- [Configuring Virtual Agents](#configuring-virtual-agents)
- [Docker](#docker)
- [Extending the Sample](#extending-the-sample)

## Prerequisites

- **Python 3.10 or later**.
- **Network access** the first time you run the server, to install dependencies from PyPI and to fetch the latest `.proto` schemas via `code/fetch_proto_schema.py`.
- *(Optional)* **Docker / Docker Compose** if you want to run the containerised version.

The proto schemas are not committed to the module — `run.sh` fetches them from the upstream `dataSourceSchemas` repo and runs `grpc_tools.protoc` to generate the Python stubs. See [Proto Code Generation](#proto-code-generation) below.

## Quick Start

The fastest path to a running server uses the bundled script, which handles venv creation, dependency install, proto fetch, codegen, and start-up in one command:

```bash
chmod +x run.sh
./run.sh
```

The gRPC server starts on port **8086** (override via the `GRPC_SERVER_PORT` env var).

## Manual Setup

If you'd rather control each step yourself (for debugging, IDE integration, or CI):

```bash
# 1. Create and activate a venv
python3 -m venv .venv
source .venv/bin/activate

# 2. Install dependencies
pip install -r code/requirements.txt

# 3. Fetch proto schemas + generate gRPC stubs
cd code
python fetch_proto_schema.py
cd src
python -m grpc_tools.protoc \
  -I./proto \
  --python_out=./proto \
  --grpc_python_out=./proto \
  ./proto/*.proto
cd ../..

# 4. Start the server
python code/src/server/AIAgentServer.py
```

## Project Layout

```
byova-grpc-python/
├── Dockerfile                # Container image (Python 3.10-slim, non-root)
├── docker-compose.yml        # One-command container run
├── README.md
├── run.sh                    # Venv + deps + proto + start (recommended)
└── code/
    ├── fetch_proto_schema.py # Downloads .proto files from GitHub
    ├── requirements.txt      # Python dependencies (grpcio, grpcio-tools, ...)
    └── src/
        ├── server/
        │   └── AIAgentServer.py   # gRPC server entry point (port 8086)
        ├── service/
        │   ├── RequestProcessor.py  # Routes incoming requests (audio, DTMF, events)
        │   ├── AudioProcessor.py    # Buffers audio + emits chunked responses
        │   └── VirtualAgents.py     # Loads VA catalog from JSON
        ├── interceptor/
        │   └── AuthInterceptor.py   # JWT validation scaffold (replace before prod)
        ├── utils/
        │   ├── EventUtils.py        # Helpers for building gRPC responses
        │   └── AudioUtils.py        # Reads default audio files
        ├── model/
        │   └── VirtualAgentInfo.py  # Virtual-agent data class
        ├── config/
        │   ├── virtual_agents.json  # Virtual-agent definitions
        │   └── audio/               # Pre-recorded prompt WAVs
        └── proto/                   # .proto + generated *_pb2.py / *_pb2_grpc.py
```

## Proto Code Generation

Two paths produce the `*_pb2.py` / `*_pb2_grpc.py` modules consumed by the server:

- **Automatic (`run.sh`)** — fetches the latest schemas from `webex/dataSourceSchemas` and regenerates stubs on every run.
- **Manual** — re-run only the codegen step after editing any `.proto`:

  ```bash
  python3 -m grpc_tools.protoc \
    -I./code/src/proto \
    --python_out=./code/src/proto \
    --grpc_python_out=./code/src/proto \
    ./code/src/proto/*.proto
  ```

Generated `*_pb2*.py` files are intentionally git-ignored so the sample always runs against the current upstream contract.

## How It Works

- **Session Start** — the server replies to `SESSION_START` with a welcome audio prompt.
- **Audio Input** — caller audio bytes are buffered. Once end-of-speech is detected (by buffer size in this sample), the response audio is streamed back in chunks.
- **DTMF Input** — pressing `5` triggers `TRANSFER_TO_AGENT`; pressing `6` triggers `SESSION_END`.
- **Authentication** — `AuthInterceptor` shows the shape of JWT validation against the `authorization` metadata header. Replace it with full signature verification against the issuer's JWKS before production use.

## Configuration

Runtime configuration is exposed through environment variables.

| Variable           | Default | Description                                            |
|--------------------|---------|--------------------------------------------------------|
| `GRPC_SERVER_PORT` | `8086`  | gRPC listening port                                    |
| `worker_thread`    | `10`    | Size of the `ThreadPoolExecutor` that handles RPCs     |

Override at runtime, e.g.:

```bash
GRPC_SERVER_PORT=9090 python code/src/server/AIAgentServer.py
```

## Configuring Virtual Agents

Edit `code/src/config/virtual_agents.json` to add or modify agents returned by `ListVirtualAgents`:

```json
[
  {
    "virtual_agent_id": 1,
    "virtual_agent_name": "My Agent",
    "is_default": false
  }
]
```

## Docker

```bash
docker compose build
docker compose up
```

The image runs as a non-root user (`app`) for least-privilege execution and exposes port `8086`.

## Extending the Sample

- **Connect a real speech service** — replace the audio handling in `AudioProcessor._response_to_user_as_chunk` with calls to your ASR/NLU engine and stream the responses back as `CHUNK` prompts.
- **Harden authentication / mTLS** — extend `AuthInterceptor.validate_token` to fully verify the JWT signature against the fetched JWKS, and wire in TLS credentials on the gRPC server via `grpc.ssl_server_credentials(...)`.
- **Change the virtual-agent catalog** — override `VirtualAgents._load_virtual_agents` to fetch agent metadata from an external source (database, REST API, …) instead of the JSON file.
