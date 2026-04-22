# BYoVA Multi-RPC Sample (Spring Boot / Java)

This module is a reference implementation of the Webex Contact Center
**Bring-Your-Own-Virtual-Agent (BYoVA) multi-RPC** gRPC interface, built with
Spring Boot 3 and `grpc-java`. It exposes the `VoiceVirtualAgent`
bidirectional-streaming service and demonstrates how to handle the three input
types the WxCC platform sends:

- **Event input** (`SESSION_START`, `NO_INPUT`, `CUSTOM_EVENT`, …)
- **Audio input** (µ-law 8 kHz caller audio — silence-detected, buffered, and
  echoed back in either chunked or WAV mode)
- **DTMF input** (digits mapped to pre-recorded audio prompts, with `0`
  triggering an agent transfer and `*` triggering session end)

It also implements the unary `ListVirtualAgents` RPC.

The sample is deliberately self-contained and uses in-memory, file-backed
prompts so it can be run as a smoke test for a BYoVA integration without any
external services.

## Prerequisites

- Java 21+
- Maven 3.9+ (or use the included `./mvnw` wrapper)
- Access to the Cisco internal artifact repository to resolve
  `com.cisco.wccai:media-service` (defined in the `contact-center-release`
  repository in `pom.xml`)

## Project layout

```
src/main/java/com/cisco/wccai/byova
├── ByovaMultiRpcApplication.java      # Spring Boot entry point
├── audio/
│   ├── AudioConstants.java            # Classpath resource names
│   ├── AudioFileLoader.java           # Loads WAV bytes, writes capture files
│   └── MuLawCodec.java                # G.711 µ-law <-> linear PCM
├── config/
│   ├── GrpcServerProperties.java      # grpc.server.*
│   └── VoiceVaProperties.java         # voice.va.*  (audio / DTMF)
├── exception/
│   └── AudioProcessingException.java
├── grpc/
│   ├── GrpcContextHelper.java         # Correlation-id propagation
│   ├── GrpcServer.java                # Lifecycle bean (ApplicationReadyEvent)
│   ├── MetadataInterceptor.java       # Stamps an rpcId on each call
│   ├── VoiceVAGrpcService.java        # gRPC service stub
│   └── VoiceVARequestObserver.java    # Per-RPC state + dispatcher
└── service/
    ├── SilenceDetector.java           # Amplitude-based silence detection
    └── VoiceVAResponseBuilder.java    # Response protobuf construction
```

Pre-recorded prompts live under `src/main/resources/audio/`.

## Running locally

```bash
./mvnw spring-boot:run
```

The gRPC server listens on port **8086** (configurable via `grpc.server.port`).

## Configuration

All knobs are exposed through Spring Boot configuration. See
`application.yml` for the defaults; the most commonly tweaked values are:

| Property                              | Default | Description                                               |
|---------------------------------------|---------|-----------------------------------------------------------|
| `grpc.server.port`                    | `8086`  | gRPC listening port                                       |
| `grpc.server.shutdown-timeout-seconds`| `30`    | Graceful shutdown window                                  |
| `voice.va.input-timeout-millis`       | `10000` | Complete/incomplete speech timeout sent to WxCC           |
| `voice.va.audio.use-chunked-audio`    | `true`  | `true` → emit CHUNK responses; `false` → single WAV FINAL |
| `voice.va.audio.amplitude-threshold`  | `2000`  | PCM absolute amplitude above which a sample is "speech"   |
| `voice.va.audio.write-to-file`        | `false` | Persist captured caller audio to `~/recorded-audio/`      |
| `voice.va.dtmf.input-length`          | `9`     | Max digits reported to WxCC                               |
| `voice.va.dtmf.term-char`             | `16`    | Terminator key — `16` is `#` in the proto enum            |

Override any value at runtime, e.g.:

```bash
GRPC_SERVER_PORT=9090 \
VOICE_VA_AUDIO_WRITE_TO_FILE=true \
./mvnw spring-boot:run
```

## Building a JAR

```bash
./mvnw clean package
java -jar target/byova-multi-rpc-java-1.0.0.jar
```

## Docker

```bash
docker compose build
docker compose up
```

The image runs as a non-root user (`app`) and uses the Spring Boot layered-jar
launcher for fast rebuilds.

## Extending the sample

- **Connect a real speech service**: replace `VoiceVARequestObserver#emitWavAudioResponse`
  and `emitChunkedAudioResponses` with calls to your ASR/NLU engine and stream
  its responses back as WAV or CHUNK prompts.
- **Add authentication / mTLS**: `GrpcServer` builds a
  `NettyServerBuilder` — wire in `.sslContext(...)` and/or a custom
  `ServerInterceptor` to enforce credential checks.
- **Change virtual agent catalog**: override `VoiceVAResponseBuilder#sampleVirtualAgents()`
  to return your own list (e.g. fetched from a database).

## License

See the repository root `License` file.
