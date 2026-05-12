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
- [Authentication (JWS / JWT validation)](#authentication-jws--jwt-validation)
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
    │   │   ├── auth/
    │   │   │   ├── AccessTokenException.java        # Typed validation failure
    │   │   │   ├── AuthorizationHandler.java        # Strategy interface
    │   │   │   ├── AuthorizationHandlerFactory.java # Picks a handler from token shape
    │   │   │   ├── JWTAuthorizationHandler.java     # Nimbus-based JWS verifier + JWKS cache
    │   │   │   └── PublicKeyResponse.java           # JWKS response POJO
    │   │   ├── config/
    │   │   │   ├── AuthProperties.java          # `auth.*` (JWT settings)
    │   │   │   ├── GrpcServerProperties.java   # `grpc.server.*`
    │   │   │   └── VoiceVaProperties.java      # `voice.va.*` (audio / DTMF)
    │   │   ├── exception/
    │   │   │   └── AudioProcessingException.java
    │   │   ├── grpc/
    │   │   │   ├── AuthorizationServerInterceptor.java # JWT check on every gRPC call
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
| `auth.enabled`                        | `true`  | Master switch for JWT validation; see [Authentication](#authentication-jws--jwt-validation) |
| `auth.datasource-url`                 | _placeholder_ | Public URL of this BYoVA service registered in Webex CC (must match `com.cisco.datasource.url` claim) |
| `auth.datasource-schema-uuid`         | _placeholder_ | BYoVA schema UUID provisioned for your tenant                |
| `auth.public-key-cache-minutes`       | `60`    | TTL of the cached Identity Broker JWKS                       |

Override at runtime via Spring's standard config sources (env vars, `--prop=value` CLI args, external `application.yml`, …):

```bash
GRPC_SERVER_PORT=9090 \
VOICE_VA_AUDIO_WRITE_TO_FILE=true \
./mvnw spring-boot:run
```

## Authentication (JWS / JWT validation)

Every inbound gRPC call is authenticated by [`AuthorizationServerInterceptor`](./src/main/java/com/cisco/wccai/byova/grpc/AuthorizationServerInterceptor.java) before the request reaches business code. The interceptor reads the `authorization` metadata header, parses it as a Cisco JWS/JWT, and runs four checks:

1. **Signature verification** against the issuer's public JWKS, fetched from `<issuer>/oauth2/v2/keys/verificationjwk` and cached in-memory (default TTL 60 min, with stale-cache fallback on HTTP 429).
2. **Expiration** — the `exp` claim must be in the future.
3. **Required claims + issuer allow-list** — `iss` must be one of `auth.valid-issuers`, and `aud`, `sub`, and `jti` must all be present.
4. **Datasource binding** — the `com.cisco.datasource.url` and `com.cisco.datasource.schema.uuid` claims must equal the `auth.datasource-url` and `auth.datasource-schema-uuid` values configured for this server. This is what guarantees the token was minted **for this BYoVA service and this schema** and not for some other Webex tenant or service.

Any failure terminates the call with `Status.UNAUTHENTICATED`.

### Required configuration

In any deployed environment you must set:

```yaml
auth:
  enabled: true
  datasource-url: https://<your-public-byova-host>:443
  datasource-schema-uuid: <your-byova-schema-uuid>
```

`datasource-url` and `datasource-schema-uuid` must match the values produced when you register the data source in Control Hub (see [`bring-your-own/virtual-agent/README.md`](../../../README.md) for the onboarding flow). The shipped `application.yml` contains obvious placeholder values that **must** be replaced — leaving them in place will reject every legitimate token.

### Disabling for local development

Setting `auth.enabled=false` skips validation entirely. Use this **only** for local smoke tests where you are sending requests yourself with a tool like `grpcurl`; never disable it in any environment that reaches the public Internet or the Webex CC platform.

```bash
AUTH_ENABLED=false ./mvnw spring-boot:run
```

### Where to extend it

- To support OAuth2 opaque tokens or a custom scheme, add a new `AuthorizationHandler` implementation and wire it into [`AuthorizationHandlerFactory`](./src/main/java/com/cisco/wccai/byova/auth/AuthorizationHandlerFactory.java).
- To add MDC propagation (org id, tracking id, request id), update `AuthorizationServerInterceptor#interceptCall` to push the desired claims onto SLF4J MDC; the `trackingId` header is already extracted via `TRACKING_ID_KEY`.

## Extending the Sample

- **Connect a real speech service** — replace `VoiceVARequestObserver#emitWavAudioResponse` and `emitChunkedAudioResponses` with calls to your ASR/NLU engine, and stream its responses back as WAV or `CHUNK` prompts.
- **Add mTLS** — `GrpcServer` builds a `NettyServerBuilder`. Wire in `.sslContext(...)` to enforce TLS in addition to the JWT check.
- **Change the virtual-agent catalog** — override `VoiceVAResponseBuilder#sampleVirtualAgents()` to return your own list (e.g. fetched from a database).

