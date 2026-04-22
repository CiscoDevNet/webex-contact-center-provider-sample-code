# BYoVA Multi-RPC Sample (Python)

This module is a reference implementation of the Webex Contact Center
**Bring-Your-Own-Virtual-Agent (BYoVA) multi-RPC** gRPC interface, written in
Python using `grpcio`. It exposes the `VoiceVirtualAgent` service and
demonstrates how to handle the three input types the WxCC platform sends:

- **Event input** (`SESSION_START`, `SESSION_END`, …)
- **Audio input** (µ-law 8 kHz caller audio — buffered, and echoed back in
  chunked mode)
- **DTMF input** (`5` triggers an agent transfer, `6` ends the session)

It also implements the unary `ListVirtualAgents` RPC.

The sample is deliberately self-contained and uses in-memory, file-backed
prompts so it can be run as a smoke test for a BYoVA integration without any
external services.

## Project Structure

```
code/
├── fetch_proto_schema.py             # Fetches .proto schemas from GitHub
├── requirements.txt                  # Python dependencies
└── src/
    ├── server/
    │   └── AIAgentServer.py          # gRPC server entry point (port 8086)
    ├── service/
    │   ├── RequestProcessor.py       # Routes incoming requests (audio, DTMF, events)
    │   ├── AudioProcessor.py         # Processes audio input and detects end-of-speech
    │   └── VirtualAgents.py          # Loads virtual agent config from JSON
    ├── interceptor/
    │   └── AuthInterceptor.py        # gRPC interceptor for token validation
    ├── utils/
    │   ├── EventUtils.py             # Helper methods for building gRPC responses
    │   └── AudioUtils.py             # Reads default audio files
    ├── model/
    │   └── VirtualAgentInfo.py       # Virtual agent data model
    ├── config/
    │   ├── virtual_agents.json       # Virtual agent definitions
    │   └── audio/                    # Audio files (wav)
    └── proto/                        # Protobuf definitions and generated code
```

## Prerequisites

- Python 3.10+

## Quick Start

The easiest way to run the server is using the provided script:

```bash
chmod +x run.sh
./run.sh
```

This will automatically create a virtual environment, install dependencies,
fetch proto schemas from GitHub, generate gRPC code, and start the server.

## Manual Setup

1. Create and activate a virtual environment:

```bash
python3 -m venv .venv
source .venv/bin/activate
```

2. Install dependencies:

```bash
pip install -r code/requirements.txt
```

3. Fetch proto schemas and generate gRPC code:

```bash
cd code
python fetch_proto_schema.py
cd src
python -m grpc_tools.protoc -I./proto --python_out=./proto --grpc_python_out=./proto ./proto/*.proto
cd ../..
```

4. Start the server:

```bash
python code/src/server/AIAgentServer.py
```

The gRPC server will start on port **8086** (configurable via the
`GRPC_SERVER_PORT` environment variable).

## How It Works

- **Session Start**: When a session begins, the server responds with a welcome
  audio prompt.
- **Audio Input**: Audio bytes are buffered. Once end-of-speech is detected
  (by buffer size in this sample), the server sends the audio response back
  in chunks.
- **DTMF Input**: Pressing `5` triggers a transfer to agent. Pressing `6`
  ends the session.
- **Authentication**: Incoming requests are validated via JWT token in the
  `authorization` metadata header. The sample `AuthInterceptor` demonstrates
  the shape of a validation flow — replace it with full signature verification
  against the issuer's JWKS before production use.

## Configuration

Runtime configuration is exposed through environment variables.

| Variable           | Default | Description                                            |
|--------------------|---------|--------------------------------------------------------|
| `GRPC_SERVER_PORT` | `8086`  | gRPC listening port                                    |
| `worker_thread`    | `10`    | Size of the `ThreadPoolExecutor` that handles RPCs     |

Override any value at runtime, e.g.:

```bash
GRPC_SERVER_PORT=9090 python code/src/server/AIAgentServer.py
```

## Configuring Virtual Agents

Edit `code/src/config/virtual_agents.json` to add or modify virtual agents:

```json
[
  {
    "virtual_agent_id": 1,
    "virtual_agent_name": "My Agent",
    "is_default": false
  }
]
```

## Regenerating Proto Files

If you modify the `.proto` files, regenerate the Python code:

```bash
python3 -m grpc_tools.protoc \
  -I./code/src/proto \
  --python_out=./code/src/proto \
  --grpc_python_out=./code/src/proto \
  ./code/src/proto/*.proto
```

## Docker

```bash
docker compose build
docker compose up
```

The image runs as a non-root user (`app`) for least-privilege execution.

## Extending the Sample

- **Connect a real speech service**: replace the audio handling in
  `AudioProcessor._response_to_user_as_chunk` with calls to your ASR/NLU
  engine and stream responses back as `CHUNK` prompts.
- **Harden authentication / mTLS**: extend `AuthInterceptor.validate_token`
  to fully verify the JWT signature against the fetched JWKS, and wire in
  TLS credentials on the gRPC server via `grpc.ssl_server_credentials(...)`.
- **Change the virtual agent catalog**: override
  `VirtualAgents._load_virtual_agents` to fetch agent metadata from an
  external source (database, REST API, etc.) instead of the JSON file.

## License

See the repository root `License` file.
