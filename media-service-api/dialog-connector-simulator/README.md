# Dialog Connector Simulator

> **⚠️ For complete setup and usage instructions, see the [main README](../../README.md#quick-start-section)**

This directory contains the Dialog Connector Simulator - a sample Java application that demonstrates how to integrate external conversational interfaces with Webex Contact Center through gRPC services.

## Quick Links

- **[Complete Setup Guide](../../README.md#quick-start-section)** - Prerequisites, build, run, and configuration
- **[Local Development with ngrok](../../README.md#local-dev-section)** - Making your server publicly accessible
- **[Troubleshooting](../../README.md#troubleshooting-section)** - Common issues and solutions
- **[BYoVA Integration Guide](../../README.md#byova-getting-started-section)** - Detailed API usage and examples

## What's in This Directory

```
├── src/main/java/com/cisco/wccai/grpc/
│   ├── server/          # gRPC server implementation
│   └── client/          # Sample gRPC client for testing
├── src/main/proto/      # Protocol buffer definitions
├── src/main/resources/  # Configuration and audio files
└── target/             # Built JAR files (after mvn install)
```

## Key Files

- **`GrpcServer.java`** - Main server application entry point
- **`VoiceVAImpl.java`** - BYoVA (Virtual Agent) service implementation
- **`ConversationAudioForkImpl.java`** - Media Forking service implementation
- **`config.properties`** - Server configuration (update DATASOURCE_URL for your environment)
- **`voicevirtualagent.proto`** - gRPC service definitions for Virtual Agent
- **`conversationaudioforking.proto`** - gRPC service definitions for Media Forking

## Supported Features

- **Bring Your Own Virtual Agent (BYoVA)** - Integrate external conversational AI
- **Media Forking** - Real-time audio streaming of agent-caller interactions
- **Multiple Audio Formats** - WAV streaming and chunk streaming
- **DTMF Support** - Handle keypad input from callers
- **Barge-in Capabilities** - Interrupt prompts with caller input
- **JWT Authentication** - Secure communication with Webex Contact Center
- **mTLS Support** - Mutual TLS authentication

## Quick Start

```bash
# From the project root
cd media-service-api/dialog-connector-simulator

# Build
mvn clean install

# Run
java -jar target/dialog-connector-simulator-1.0.0-SNAPSHOT-allinone.jar
```

For detailed instructions, configuration, and troubleshooting, **[see the main README](../../README.md#quick-start-section)**.
