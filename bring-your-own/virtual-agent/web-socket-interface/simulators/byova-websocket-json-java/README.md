# BYoVA WebSocket Sample — JSON Schema (Spring Boot / Java)

A reference implementation of the Webex Contact Center **Bring-Your-Own-Virtual-Agent (BYoVA) WebSocket** interface using the **JSON wire format**, built with Spring Boot 4 and the standard `spring-boot-starter-websocket`. It exposes two endpoints:

- `/v1/va` — bidirectional channel that carries text JSON envelopes for everything: control messages, virtual-agent responses, and caller audio (base64-encoded inside `VoiceInput.caller_audio_b64`). No binary frames are used.
- `/v1/listVirtualAgents` — request/response endpoint used by the WxCC platform to discover the virtual agents this server can serve.

The sample handles the same three input types the WxCC platform sends:

- **Event input** (`SESSION_START`, `NO_INPUT`, `CUSTOM_EVENT`, …)
- **Audio input** — µ-law 8 kHz caller audio, silence-detected, buffered, and echoed back in either chunked or single-WAV mode.
- **DTMF input** — digits mapped to pre-recorded audio prompts; `0` triggers `TRANSFER_TO_AGENT`, `*` triggers `SESSION_END`.

The sample is deliberately self-contained — prompts are loaded from the classpath under `src/main/resources/audio/` — so it can be run as a smoke test for a BYoVA integration without any external services.

For the underlying call-flow contract, see the [parent BYoVA README](../../../README.md).

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Project Layout](#project-layout)
- [Building a JAR](#building-a-jar)
- [Configuration](#configuration)
- [Authentication (JWS / JWT validation)](#authentication-jws--jwt-validation)
- [Extending the Sample](#extending-the-sample)

## Prerequisites

- **JDK 21 or later** (the project pins `java.version=21`).
- **Maven 3.9+** (required by Spring Boot 4).
- **Network access to Maven Central** the first time you build, to download Spring Boot, Jackson, Nimbus JOSE+JWT, and Lombok.
- *(Optional)* **Docker / Docker Compose** if you want to run the containerised version.

## Quick Start

Run the server straight from source on port `8086`:

```bash
mvn spring-boot:run
```

Once you see `Tomcat started on port 8086`, open a WebSocket connection (e.g. with `wscat`):

```bash
# Voice/event channel
wscat -c ws://localhost:8086/v1/va -H "Authorization: Bearer <jwt>"

# Virtual-agent catalog
wscat -c ws://localhost:8086/v1/listVirtualAgents -H "Authorization: Bearer <jwt>"
```

> The `Authorization` header is required unless you set `auth.enabled=false` for local development — see [Authentication](#authentication-jws--jwt-validation).

## Project Layout

```
byova-websocket-json-java/
├── pom.xml
└── src/main/
    ├── java/com/cisco/wccai/
    │   ├── ByovaWebsocketJsonJavaApplication.java # Spring Boot entry point
    │   ├── auth/
    │   │   ├── AccessTokenException.java                # Typed validation failure
    │   │   ├── AuthProperties.java                      # `auth.*` (JWT settings)
    │   │   ├── AuthorizationHandler.java                # Strategy interface
    │   │   ├── AuthorizationHandlerFactory.java         # Picks a handler from token shape
    │   │   ├── AuthorizationHandshakeInterceptor.java   # JWT check on WS upgrade
    │   │   ├── JWTAuthorizationHandler.java             # Nimbus-based JWS verifier + JWKS cache
    │   │   └── PublicKeyResponse.java                   # JWKS response POJO
    │   ├── common/
    │   │   ├── AudioConstant.java                       # Classpath resource names
    │   │   └── AudioProcessingException.java
    │   ├── config/
    │   │   ├── JacksonConfig.java                       # ObjectMapper configuration
    │   │   └── WebSocketConfig.java                     # Endpoint + handshake registration
    │   ├── handler/
    │   │   ├── ListVirtualAgentWebSocketHandler.java    # /v1/listVirtualAgents
    │   │   └── VirtualAgentWebSocketHandler.java        # /v1/va
    │   ├── service/                                     # Audio, DTMF, processor, adaptor
    │   ├── util/                                        # Audio file/format utilities
    │   └── ws/                                          # JSON DTOs (envelopes, voice, list)
    └── resources/
        ├── application.properties                       # Default config
        └── audio/                                       # Pre-recorded prompt WAVs
```

## Building a JAR

```bash
mvn clean package -DskipTests
java -jar target/byova-websocket-json-java-1.0.0.jar
```

The packaged jar is a Spring Boot fat jar with `ByovaWebsocketJsonJavaApplication` as the entry point.

## Configuration

All knobs are exposed through Spring Boot configuration; see [`src/main/resources/application.properties`](./src/main/resources/application.properties) for the defaults. The most commonly tweaked values:

| Property                                      | Default | Description                                                  |
|-----------------------------------------------|---------|--------------------------------------------------------------|
| `server.port`                                 | `8086`  | HTTP/WebSocket listening port                                |
| `spring.websocket.max-binary-message-buffer-size` | `10485760` | Max binary frame size (10 MB)                            |
| `spring.websocket.max-session-idle-timeout`   | `900000` | Session idle timeout (15 min)                                |
| `voice.va.input.timeout-millis`               | `10000` | Complete/incomplete speech timeout reported to WxCC          |
| `voice.va.audio.use-chunked-audio`            | `true`  | `true` → emit `CHUNK` responses; `false` → single WAV `FINAL`|
| `voice.va.audio.amplitude-threshold`          | `2000`  | PCM absolute amplitude above which a sample is "speech"      |
| `voice.va.audio.write-to-file`                | `false` | Persist captured caller audio to `~/recorded-audio/`         |
| `voice.va.dtmf.input-length`                  | `9`     | Max digits reported to WxCC                                  |
| `voice.va.dtmf.term-char`                     | `DTMF_DIGIT_POUND` | Terminator key                                    |
| `auth.enabled`                                | `true`  | Master switch for JWT validation; see [Authentication](#authentication-jws--jwt-validation) |
| `auth.datasource-url`                         | _placeholder_ | Public URL of this BYoVA service registered in Webex CC (must match `com.cisco.datasource.url` claim) |
| `auth.datasource-schema-uuid`                 | _placeholder_ | BYoVA schema UUID provisioned for your tenant        |
| `auth.public-key-cache-minutes`               | `60`    | TTL of the cached Identity Broker JWKS                       |

Override at runtime via Spring's standard config sources (env vars, `--prop=value` CLI args, external `application.properties`, …):

```bash
SERVER_PORT=9090 \
VOICE_VA_AUDIO_WRITE_TO_FILE=true \
mvn spring-boot:run
```

## Authentication (JWS / JWT validation)

Every WebSocket upgrade request is authenticated by [`AuthorizationHandshakeInterceptor`](./src/main/java/com/cisco/wccai/auth/AuthorizationHandshakeInterceptor.java) **before** a session is opened. The interceptor reads the `Authorization` HTTP header presented during the handshake, parses it as a Cisco JWS/JWT, and runs four checks:

1. **Signature verification** against the issuer's public JWKS, fetched from `<issuer>/oauth2/v2/keys/verificationjwk` and cached in-memory (default TTL 60 min, with stale-cache fallback on HTTP 429).
2. **Expiration** — the `exp` claim must be in the future.
3. **Required claims + issuer allow-list** — `iss` must be one of `auth.valid-issuers`, and `aud`, `sub`, and `jti` must all be present.
4. **Datasource binding** — the `com.cisco.datasource.url` and `com.cisco.datasource.schema.uuid` claims must equal the `auth.datasource-url` and `auth.datasource-schema-uuid` values configured for this server. This is what guarantees the token was minted **for this BYoVA service and this schema** and not for some other Webex tenant or service.

A failed check aborts the handshake with HTTP `401 Unauthorized` — no WebSocket session is opened and no further bytes are processed.

### Required configuration

In any deployed environment you must set:

```properties
auth.enabled=true
auth.datasource-url=https://<your-public-byova-host>:443
auth.datasource-schema-uuid=<your-byova-schema-uuid>
```

`datasource-url` and `datasource-schema-uuid` must match the values produced when you register the data source in Control Hub (see [`bring-your-own/virtual-agent/README.md`](../../../README.md) for the onboarding flow). The shipped `application.properties` contains obvious placeholder values that **must** be replaced — leaving them in place will reject every legitimate token.

### Disabling for local development

Setting `auth.enabled=false` skips validation entirely. Use this **only** for local smoke tests with `wscat` or a test client; never disable it in any environment that reaches the public Internet or the Webex CC platform.

```bash
AUTH_ENABLED=false mvn spring-boot:run
```

### Where to extend it

- To support OAuth2 opaque tokens or a custom scheme, add a new `AuthorizationHandler` implementation and wire it into [`AuthorizationHandlerFactory`](./src/main/java/com/cisco/wccai/auth/AuthorizationHandlerFactory.java).
- To attach the validated subject/org id to the session for downstream use, store it under `attributes` in `AuthorizationHandshakeInterceptor#beforeHandshake` (already done for `trackingId`); the WebSocket handlers can then read it from `WebSocketSession#getAttributes()`.

## Extending the Sample

- **Connect a real speech service** — replace the audio echo logic in `service/AudioStreamingService` with calls to your ASR/NLU engine, and stream its responses back as WAV or `CHUNK` envelopes.
- **Add TLS termination** — Spring Boot exposes the standard `server.ssl.*` properties; configure a keystore to terminate TLS in-process or, more commonly, terminate at your ingress and forward over plain HTTP on a private network.
- **Change the virtual-agent catalog** — override `service/VirtualAgentProcessor#sendVirtualAgentsList` to return your own list (e.g. fetched from a database).
