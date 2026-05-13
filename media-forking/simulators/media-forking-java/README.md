# Media Forking Sample (Spring Boot / Java)

A reference implementation of the Webex Contact Center **Conversation Audio Forking** gRPC interface, built with Spring Boot 3 and `grpc-java`. It exposes the `ConversationAudio` bidirectional-streaming service so a partner-hosted server can receive the live audio of a Webex Contact Center call (caller and agent legs) for downstream processing — recording, real-time analytics, ASR, fraud detection, and so on.

Key characteristics of this sample:

- **Self-contained** — no external proto dependency. The proto schemas (`conversationaudioforking.proto`, `common/media_service_common.proto`) are checked into `src/main/proto/` and compiled locally by `protobuf-maven-plugin`.
- **Spring Boot service** — config externalised via `application.yml`, lifecycle managed via `ApplicationReadyEvent` / `@PreDestroy`, and dependencies wired via Spring rather than `new ...()`.
- **Authenticates every call** — a `ServerInterceptor` validates the inbound JWS/JWT against the Webex Identity Broker JWKS and the configured datasource binding before any audio frame is delivered to the service.

The sample does not transcode or re-emit the audio. The point of integration is the `ConversationAudioProcessor` service — replace its body with calls to your own ASR engine, recording sink, or analytics pipeline.

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
- **Network access to Maven Central** the first time you build, to download Spring Boot, gRPC, protobuf, Nimbus JOSE+JWT, and Lombok artifacts. The `protobuf-maven-plugin` will also pull the `protoc` binary for your OS.
- *(Optional)* **Docker / Docker Compose** if you want to run the containerised version.

## Quick Start

Run the server straight from source on port `8087`:

```bash
./mvnw spring-boot:run
```

For a smoke test without a real JWT, disable authorization (do **not** do this in any deployed environment):

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--auth.enabled=false"
```

You should see a startup line similar to:

```
gRPC server started on port 8087 with 1 service(s)
```

The server exposes a single RPC: `com.cisco.wcc.ccai.media.v1.ConversationAudio/StreamConversationAudio`. Point your forking client at `localhost:8087` and start streaming `ConversationAudioForkingRequest` messages. The server replies with one `ConversationAudioForkingResponse` carrying `status_message="SUCCESS"` when the client half-closes the stream.

## Project Layout

```
media-forking-java/
├── Dockerfile                   # Multi-stage build (JDK 21 → JRE 21 runtime)
├── docker-compose.yml           # One-command container run
├── mvnw / mvnw.cmd / .mvn/      # Maven wrapper
├── pom.xml                      # Build + protobuf-maven-plugin config
└── src/
    └── main/
        ├── java/com/cisco/wccai/forking/
        │   ├── MediaForkingApplication.java          # Spring Boot entry point
        │   ├── auth/
        │   │   ├── AccessTokenException.java         # Typed validation failure
        │   │   ├── AuthorizationHandler.java         # Strategy interface
        │   │   ├── AuthorizationHandlerFactory.java  # Picks a handler from token shape
        │   │   ├── JWTAuthorizationHandler.java      # Nimbus-based JWS verifier + JWKS cache
        │   │   └── PublicKeyResponse.java            # JWKS response POJO
        │   ├── config/
        │   │   ├── AuthProperties.java               # `auth.*` (JWT settings)
        │   │   ├── ForkingProperties.java            # `forking.*` (logging + capture)
        │   │   └── GrpcServerProperties.java         # `grpc.server.*`
        │   ├── grpc/
        │   │   ├── AuthorizationServerInterceptor.java     # JWT check on every gRPC call
        │   │   ├── ConversationAudioGrpcService.java       # gRPC service stub binding
        │   │   ├── ConversationAudioRequestObserver.java   # Per-RPC observer
        │   │   ├── GrpcContextHelper.java                  # Correlation-id propagation
        │   │   ├── GrpcServer.java                         # Lifecycle bean (ApplicationReadyEvent)
        │   │   └── MetadataInterceptor.java                # Stamps an rpcId on each call
        │   └── service/
        │       └── ConversationAudioProcessor.java         # Where you plug in your downstream consumer
        ├── proto/com/cisco/wcc/ccai/media/v1/
        │   ├── conversationaudioforking.proto
        │   └── common/media_service_common.proto
        └── resources/
            └── application.yml                       # Default config
```

## Proto Code Generation

`org.xolstice.maven.plugins:protobuf-maven-plugin` is wired into the `compile` and `compile-custom` goals. Every Maven build re-runs `protoc` and `protoc-gen-grpc-java` and emits Java + gRPC stubs under `target/generated-sources/protobuf/`:

- `target/generated-sources/protobuf/java/com/cisco/wcc/ccai/media/v1/Conversationaudioforking.java`
- `target/generated-sources/protobuf/java/com/cisco/wcc/ccai/media/v1/MediaServiceCommon.java`
- `target/generated-sources/protobuf/grpc-java/com/cisco/wcc/ccai/media/v1/ConversationAudioGrpc.java`

If you change anything under `src/main/proto/`, just rebuild — `mvn compile` is enough.

## Building a JAR

```bash
./mvnw clean package -DskipTests
java -jar target/media-forking-java-1.0.0.jar
```

The packaged jar is a Spring Boot fat jar (layered) with `MediaForkingApplication` as the entry point.

## Docker

```bash
docker compose build
docker compose up
```

The image is a multi-stage build (`eclipse-temurin:21-jdk` → `eclipse-temurin:21-jre`), runs as a non-root user (`app`, UID `10001`), and uses the Spring Boot layered-jar launcher for fast rebuilds. Port `8087` is exposed by default.

## Configuration

All knobs are exposed through Spring Boot configuration; see [`src/main/resources/application.yml`](./src/main/resources/application.yml) for the defaults. The most commonly tweaked values:

| Property                                | Default | Description                                                                 |
|-----------------------------------------|---------|-----------------------------------------------------------------------------|
| `grpc.server.port`                      | `8087`  | gRPC listening port                                                         |
| `grpc.server.shutdown-timeout-seconds`  | `30`    | Graceful shutdown window                                                    |
| `forking.log-every-n-frames`            | `50`    | Log a progress line every Nth frame per (conversation, role) pair           |
| `forking.write-to-file`                 | `false` | When `true`, append raw forked audio to per-conversation/role files         |
| `forking.capture-dir`                   | `~/forked-audio` | Directory where capture files are written                          |
| `auth.enabled`                          | `true`  | Master switch for JWT validation; see [Authentication](#authentication-jws--jwt-validation) |
| `auth.datasource-url`                   | _placeholder_ | Public URL of this service registered in Webex CC (must match `com.cisco.datasource.url` claim) |
| `auth.datasource-schema-uuid`           | _placeholder_ | Conversation Audio Forking schema UUID provisioned for your tenant |
| `auth.public-key-cache-minutes`         | `60`    | TTL of the cached Identity Broker JWKS                                      |

Override at runtime via Spring's standard config sources (env vars, `--prop=value` CLI args, external `application.yml`, …):

```bash
GRPC_SERVER_PORT=9090 \
FORKING_WRITE_TO_FILE=true \
./mvnw spring-boot:run
```

## Authentication (JWS / JWT validation)

Every inbound gRPC call is authenticated by [`AuthorizationServerInterceptor`](./src/main/java/com/cisco/wccai/forking/grpc/AuthorizationServerInterceptor.java) before the request reaches business code. The interceptor reads the `authorization` metadata header, parses it as a Cisco JWS/JWT, and runs four checks:

1. **Signature verification** against the issuer's public JWKS, fetched from `<issuer>/oauth2/v2/keys/verificationjwk` and cached in-memory (default TTL 60 min, with stale-cache fallback on HTTP 429).
2. **Expiration** — the `exp` claim must be in the future.
3. **Required claims + issuer allow-list** — `iss` must be one of `auth.valid-issuers`, and `aud`, `sub`, and `jti` must all be present.
4. **Datasource binding** — the `com.cisco.datasource.url` and `com.cisco.datasource.schema.uuid` claims must equal the `auth.datasource-url` and `auth.datasource-schema-uuid` values configured for this server. This is what guarantees the token was minted **for this Conversation Audio Forking service and this schema** and not for some other Webex tenant or service.

Any failure terminates the call with `Status.UNAUTHENTICATED`.

### Required configuration

In any deployed environment you must set:

```yaml
auth:
  enabled: true
  datasource-url: https://<your-public-forking-host>:443
  datasource-schema-uuid: <your-forking-schema-uuid>
```

`datasource-url` and `datasource-schema-uuid` must match the values produced when you register the data source in Control Hub. The shipped `application.yml` contains obvious placeholder values that **must** be replaced — leaving them in place will reject every legitimate token.

### Disabling for local development

Setting `auth.enabled=false` skips validation entirely. Use this **only** for local smoke tests where you are sending requests yourself with a tool like `grpcurl`; never disable it in any environment that reaches the public Internet or the Webex CC platform.

```bash
AUTH_ENABLED=false ./mvnw spring-boot:run
```

### Where to extend it

- To support OAuth2 opaque tokens or a custom scheme, add a new `AuthorizationHandler` implementation and wire it into [`AuthorizationHandlerFactory`](./src/main/java/com/cisco/wccai/forking/auth/AuthorizationHandlerFactory.java).
- To add MDC propagation (org id, tracking id, request id), update `AuthorizationServerInterceptor#interceptCall` to push the desired claims onto SLF4J MDC; the `trackingId` header is already extracted via `TRACKING_ID_KEY`.

## Extending the Sample

The whole point of integrating Conversation Audio Forking is to do something useful with the audio. Replace [`ConversationAudioProcessor`](./src/main/java/com/cisco/wccai/forking/service/ConversationAudioProcessor.java) with your own logic — common patterns include:

- **Stream to an ASR engine** — push `audio.audio_data` into a streaming recognition client per `(conversation_id, role)` pair and emit the transcripts to your downstream system.
- **Record and analyze** — write the raw bytes (after decoding/transcoding from G.711 µ-law / A-law / LINEAR16) to durable storage (S3, GCS, NFS) for compliance and post-call analytics.
- **Real-time fraud / sentiment** — feed the audio into your real-time analytics platform and act on detection events (alerts, supervisor whispers, agent assist).
- **Add mTLS** — `GrpcServer` builds a `NettyServerBuilder`. Wire in `.sslContext(...)` to enforce TLS in addition to the JWT check.
- **Persist counters / metrics** — replace the `ConcurrentHashMap` counters in `ConversationAudioProcessor` with your metrics library (Micrometer, Prometheus) for visibility into volume per conversation.

## Notes on the proto

The `ConversationAudioForkingRequest` carries a single `AudioStream` per message:

- `audio.role` distinguishes the **caller** (`CALLER`) from the **agent** (`AGENT`); both legs are forked over the same RPC.
- `audio.role_id` is a per-leg GUID so you can distinguish multiple legs per role (e.g. multi-party conferencing).
- `audio.audio_timestamp` is the capture wall-clock time at the source. The sample uses it to compute and log per-frame end-to-end latency.
- `audio.encoding` is one of `LINEAR16`, `MULAW`, `ALAW`. The sample does not decode the audio; your downstream consumer must.
- `additional_info` is reserved for future use; ignore it for now.
