# Webex Contact Center — Provider Sample Code

Reference implementations and integration guides for the **Media Service APIs** that Webex Contact Center (WxCC) exposes to providers, partners, and customers. Use these samples as the starting point for any external service that needs to plug audio, virtual agents, or real-time transcripts into a WxCC call flow.

This repository is intentionally split by **feature** and by **transport**. Each top-level feature directory ships its own deep-dive README, runnable simulators (Java and/or Python), Docker assets, and configuration so you can clone exactly the sample that matches your stack.

> **Note:** All code in this repository is **sample code** intended for reference and learning. It is not intended for production use as-is — review, harden, and adapt it for your own environment.

---

## Table of Contents

- [Features at a Glance](#features-at-a-glance)
- [Picking a Sample](#picking-a-sample)
- [Common Prerequisites](#common-prerequisites)
- [Cross-cutting Topics](#cross-cutting-topics)
- [Support, Contributing, and License](#support-contributing-and-license)

---

## Features at a Glance

<table>
  <thead>
    <tr>
      <th>Feature</th>
      <th>Transport</th>
      <th>Schema</th>
      <th>Onboarding walkthrough</th>
      <th>Reference docs</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td rowspan="2"><strong>Bring-Your-Own-Virtual-Agent (BYoVA)</strong></td>
      <td>gRPC — bidirectional streaming, one short-lived RPC per turn</td>
      <td>Protobuf</td>
      <td rowspan="2"><a href="./bring-your-own/virtual-agent/README.md#onboarding-a-new-customer--partner"><code>bring-your-own/virtual-agent/README.md</code> § Onboarding</a></td>
      <td><a href="./bring-your-own/virtual-agent/grpc-interface/README.md"><code>bring-your-own/virtual-agent/grpc-interface/README.md</code></a></td>
    </tr>
    <tr>
      <td>WebSocket — one long-lived WebSocket per call (text frames)</td>
      <td>JSON</td>
      <td><a href="./bring-your-own/virtual-agent/web-socket-interface/README.md"><code>bring-your-own/virtual-agent/web-socket-interface/README.md</code></a></td>
    </tr>
    <tr>
      <td><strong>Real-Time Media Forking</strong></td>
      <td>gRPC — bidirectional streaming, one RPC per call</td>
      <td>Protobuf</td>
      <td><a href="./media-forking/README.md#onboarding-a-new-customer--partner"><code>media-forking/README.md</code> § Onboarding</a></td>
      <td><a href="./media-forking/README.md"><code>media-forking/README.md</code></a></td>
    </tr>
  </tbody>
</table>

Every feature README covers:

- Onboarding into Webex (Service App → tokens → data source → flow).
- Audio / runtime constraints.
- Runtime authentication (JWS / JWT validation).
- The full event grammar with per-step sequence diagrams.
- Where to plug your downstream service into the simulator code.

---

## Picking a Sample

Pick the simulator whose feature, schema, language, and transport match your target stack:

| Feature | Transport | Schema | Language | Code Sample |
|---|---|---|---|---|
| BYoVA (Virtual Agent) | gRPC | Protobuf | Java | [`bring-your-own/virtual-agent/grpc-interface/simulators/byova-grpc-java/`](./bring-your-own/virtual-agent/grpc-interface/simulators/byova-grpc-java/) |
| BYoVA (Virtual Agent) | gRPC | Protobuf | Python | [`bring-your-own/virtual-agent/grpc-interface/simulators/byova-grpc-python/`](./bring-your-own/virtual-agent/grpc-interface/simulators/byova-grpc-python/) |
| BYoVA (Virtual Agent) | WebSocket | JSON | Java | [`bring-your-own/virtual-agent/web-socket-interface/simulators/byova-websocket-json-java/`](./bring-your-own/virtual-agent/web-socket-interface/simulators/byova-websocket-json-java/) |
| Real-Time Media Forking | gRPC | Protobuf | Java | [`media-forking/simulators/media-forking-java/`](./media-forking/simulators/media-forking-java/) |

Every simulator is self-contained — clone the repo, follow the **Quick Start** in that simulator's README, and you'll have a server listening locally in one command (`./mvnw spring-boot:run` for Java, `./run.sh` for Python).

---

## Common Prerequisites

You only need the Webex side of the integration once per tenant. The detailed step-by-step (with Control Hub screenshots and `curl` examples) lives in the feature READMEs — at the high level:

1. A **Webex Contact Center tenant** with an admin who can approve Service Apps and create flows.
2. An authorized **Webex Service App** scoped to the [Bring Your Own Data Source (BYoDS)](https://developer.webex.com/webex-contact-center/docs/api/v1/data-sources) APIs, with the **valid domains** your service will be reachable on.
3. A **registered data source** of the correct schema for the feature you're integrating (one for BYoVA, one for Media Forking — schema UUIDs are documented in the [WxCC schema catalog](https://github.com/webex/dataSourceSchemas)).
4. A **Config / Flow** in Control Hub that selects the authorized Service App and routes the call to your feature (Virtual Agent V2 activity for BYoVA, Media Forking activity for forking).

---

## Cross-cutting Topics

### Runtime Authentication (JWS / JWT)

Every WxCC connection — gRPC or WebSocket — carries a **JWS** issued at data-source registration time. Your server must validate it (signature against the Webex Identity Broker JWKS, expiration, required claims, datasource binding) before accepting any payload. Each Java simulator includes sample interceptor code you can use as a starting point. See the JWS section in any feature README for the full contract.

### mTLS Support

WxCC supports mutual TLS as an extra transport-layer auth check on top of (not in place of) JWS. See [`mtls-authentication.md`](./mtls-authentication.md) for the certificate exchange, supported features, and configuration steps.

### Audio / runtime constraints (apply to BYoVA + Media Forking)

- **Sample rate:** 8 kHz or 16 kHz, mono.
- **Encoding:** Linear16 or G.711 µ-law (Media Forking also supports A-law).
- **Language code:** `en-US` for BYoVA (additional locales are added per release — confirm in your tenant's docs).

Per-feature audio-format details (WAV vs raw PCM, framing, headers) live in the feature READMEs.

---

## Support, Contributing, and License

- **License.** This code is published under the [Cisco Sample Code License v1.1](./License). It is provided as sample/reference code only and is not covered by any Cisco support contract.
- **Issues / questions.** File a GitHub issue against this repository — include the feature, language, and a minimal reproducer.
- **Contributing.** PRs are welcome. Please keep changes scoped to a single feature/simulator, run that simulator's tests, and update the relevant README sections.
