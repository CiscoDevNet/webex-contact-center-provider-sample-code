# BYoVA Multi-RPC Sample (Spring Boot / Java)

A reference implementation of the Webex Contact Center **Bring-Your-Own-Virtual-Agent (BYoVA) multi-RPC** gRPC interface, built with Spring Boot 3 and `grpc-java`. It exposes the `VoiceVirtualAgent` bidirectional-streaming service plus the unary `ListVirtualAgents` RPC, and demonstrates how to handle the three input types the WxCC platform sends:

- **Event input** (`SESSION_START`, `NO_INPUT`, `CUSTOM_EVENT`, …)
- **Audio input** — µ-law 8 kHz caller audio, silence-detected, buffered, and echoed back in either chunked or single-WAV mode.
- **DTMF input** — digits mapped to pre-recorded audio prompts; `0` triggers `TRANSFER_TO_AGENT`, `*` triggers `SESSION_END`.

The sample is deliberately self-contained — prompts are loaded from the classpath under `src/main/resources/audio/` — so it can be run as a smoke test for a BYoVA integration without any external services.

For the underlying call-flow contract and sequence diagrams, see the [parent README](../README.md).

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Project Layout](#project-layout)
- [Proto Code Generation](#proto-code-generation)
- [Building a JAR](#building-a-jar)
- [Docker](#docker)
- [Configuration](#configuration)
- [Extending the Sample](#extending-the-sample)
- [License](#license)

## Prerequisites

- **JDK 21 or later** (the project pins `java.version=21`).
- **Maven 3.9+** — or just use the bundled `./mvnw` wrapper, which downloads the right Maven version on first run.
- **Network access to Maven Central** the first time you build, to download Spring Boot, gRPC, and protobuf artifacts. The `protobuf-maven-plugin` will also pull the `protoc` binary for your OS.
- *(Optional)* **Docker / Docker Compose** if you want to run the containerised version.

The proto schemas (`voicevirtualagent.proto`, `common/byova_common.proto`) are checked into `src/main/proto/` — there is **no** dependency on the internal Cisco `media-service` jar.

## Quick Start

Run the server straight from source on port `8086`:

```bash
./mvnw spring-boot:run
```

You should see a startup line similar to:

```
gRPC server started on port 8086
```

Point your BYoVA client (or grpcurl) at `localhost:8086` and start sending `VoiceVARequest` messages.

## Project Layout

```
byova-multi-rpc-java/
├── Dockerfile                  # Multi-stage build (JDK 21 → JRE 21 runtime)
├── docker-compose.yml          # One-command container run
├── mvnw / mvnw.cmd / .mvn/     # Maven wrapper
├── pom.xml                     # Build + protobuf-maven-plugin config
└── src/
    ├── main/
    │   ├── java/com/cisco/wccai/byova/
    │   │   ├── ByovaMultiRpcApplication.java   # Spring Boot entry point
    │   │   ├── audio/
    │   │   │   ├── AudioConstants.java         # Classpath resource names
    │   │   │   ├── AudioFileLoader.java        # Loads WAV bytes, writes capture files
    │   │   │   └── MuLawCodec.java             # G.711 µ-law <-> linear PCM
    │   │   ├── config/
    │   │   │   ├── GrpcServerProperties.java   # `grpc.server.*`
    │   │   │   └── VoiceVaProperties.java      # `voice.va.*` (audio / DTMF)
    │   │   ├── exception/
    │   │   │   └── AudioProcessingException.java
    │   │   ├── grpc/
    │   │   │   ├── GrpcContextHelper.java      # Correlation-id propagation
    │   │   │   ├── GrpcServer.java             # Lifecycle bean (ApplicationReadyEvent)
    │   │   │   ├── MetadataInterceptor.java    # Stamps an rpcId on each call
    │   │   │   ├── VoiceVAGrpcService.java     # gRPC service stub
    │   │   │   └── VoiceVARequestObserver.java # Per-RPC state + dispatcher
    │   │   └── service/
    │   │       ├── SilenceDetector.java        # Amplitude-based silence detection
    │   │       └── VoiceVAResponseBuilder.java # Response protobuf construction
    │   ├── proto/com/cisco/wcc/ccai/media/v1/  # voicevirtualagent.proto + common/
    │   └── resources/
    │       ├── application.yml                 # Default config
    │       └── audio/                          # Pre-recorded prompt WAVs
    └── test/                                   # JUnit + Mockito tests
```

## Proto Code Generation

`org.xolstice.maven.plugins:protobuf-maven-plugin` is wired into the `compile` and `compile-custom` goals. Every Maven build re-runs `protoc` and `protoc-gen-grpc-java` and emits Java + gRPC stubs under `target/generated-sources/protobuf/`:

- `target/generated-sources/protobuf/java/com/cisco/wcc/ccai/media/v1/Voicevirtualagent.java`
- `target/generated-sources/protobuf/java/com/cisco/wcc/ccai/media/v1/ByovaCommon.java`
- `target/generated-sources/protobuf/grpc-java/com/cisco/wcc/ccai/media/v1/VoiceVirtualAgentGrpc.java`

If you change anything under `src/main/proto/`, just rebuild — `mvn compile` is enough.

## Building a JAR

```bash
./mvnw clean package -DskipTests
java -jar target/byova-multi-rpc-java-1.0.0.jar
```

The packaged jar is a Spring Boot fat jar (layered) with `ByovaMultiRpcApplication` as the entry point.

## Docker

```bash
docker compose build
docker compose up
```

The image is a multi-stage build (`eclipse-temurin:21-jdk` → `eclipse-temurin:21-jre`), runs as a non-root user (`app`, UID `10001`), and uses the Spring Boot layered-jar launcher for fast rebuilds. Port `8086` is exposed by default.

## Configuration

All knobs are exposed through Spring Boot configuration; see [`src/main/resources/application.yml`](./src/main/resources/application.yml) for the defaults. The most commonly tweaked values:

| Property                              | Default | Description                                                  |
|---------------------------------------|---------|--------------------------------------------------------------|
| `grpc.server.port`                    | `8086`  | gRPC listening port                                          |
| `grpc.server.shutdown-timeout-seconds`| `30`    | Graceful shutdown window                                     |
| `voice.va.input-timeout-millis`       | `10000` | Complete/incomplete speech timeout reported to WxCC          |
| `voice.va.audio.use-chunked-audio`    | `true`  | `true` → emit `CHUNK` responses; `false` → single WAV `FINAL`|
| `voice.va.audio.amplitude-threshold`  | `2000`  | PCM absolute amplitude above which a sample is "speech"      |
| `voice.va.audio.write-to-file`        | `false` | Persist captured caller audio to `~/recorded-audio/`         |
| `voice.va.dtmf.input-length`          | `9`     | Max digits reported to WxCC                                  |
| `voice.va.dtmf.term-char`             | `16`    | Terminator key — `16` is `#` in the proto enum               |

Override at runtime via Spring's standard config sources (env vars, `--prop=value` CLI args, external `application.yml`, …):

```bash
GRPC_SERVER_PORT=9090 \
VOICE_VA_AUDIO_WRITE_TO_FILE=true \
./mvnw spring-boot:run
```

## Extending the Sample

- **Connect a real speech service** — replace `VoiceVARequestObserver#emitWavAudioResponse` and `emitChunkedAudioResponses` with calls to your ASR/NLU engine, and stream its responses back as WAV or `CHUNK` prompts.
- **Add authentication / mTLS** — `GrpcServer` builds a `NettyServerBuilder`. Wire in `.sslContext(...)` and/or a custom `ServerInterceptor` to enforce credential checks.
- **Change the virtual-agent catalog** — override `VoiceVAResponseBuilder#sampleVirtualAgents()` to return your own list (e.g. fetched from a database).

