# BYOVA AI Simulator

A sample gRPC server that simulates a Bring Your Own Virtual Agent (BYOVA) for Webex Contact Center. This project demonstrates how to implement the Voice Virtual Agent gRPC interface, handling audio input, DTMF events, and session lifecycle.

## Project Structure

```
code/
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

This will automatically create a virtual environment, install dependencies, fetch proto schemas from GitHub, generate gRPC code, and start the server.

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

The gRPC server will start on port **8086**.

## How It Works

- **Session Start**: When a session begins, the server responds with a welcome audio prompt.
- **Audio Input**: Audio bytes are buffered. Once end-of-speech is detected, the server sends the audio response back in chunks.
- **DTMF Input**: Pressing `5` triggers a transfer to agent. Pressing `6` ends the session.
- **Authentication**: Incoming requests are validated via JWT token in the `authorization` metadata header.

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