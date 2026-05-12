# Bring Your Own Virtual Agent — WebSocket

The Bring-Your-Own-Virtual-Agent (BYoVA) initiative empowers developers and AI vendors to seamlessly integrate external conversational interfaces with Webex Contact Center (WxCC). The **WebSocket** flavour of the integration carries the entire conversation over **one long-lived WebSocket session per call**. The two sides exchange discrete request/response messages on the same socket — no per-interaction RPC handshake, no protobuf code generation requirement (for the JSON variant), and a thinner runtime footprint than the gRPC variants.

This document describes:

- The reference implementations available under this directory — both **JSON** and **Protobuf** wire formats, in Java today (Python coming).
- The WebSocket framing rules every BYoVA server must follow (text vs. binary frames, ping/pong, close codes, envelope ordering) and the high-level event/streaming contract the VA Server must honour.
- Detailed call-flow walkthroughs (with sequence diagrams) for session start, DTMF, audio (WAV and CHUNK), barge-in, and call termination scenarios.

The unifying conceptual contract (events, prompts, input modes, audio modes) is identical to the gRPC variants — only the on-the-wire framing differs. If you already know the BYoVA contract from the [multi-RPC README](../grpc-interface/multi-rpc/README.md), you only need to learn the framing rules in this document; the events and rules are the same.


---

## Table of Contents

- [WebSocket Virtual Agent Simulators](#websocket-virtual-agent-simulators)
- [WebSocket Framing & Streaming Guidelines](#websocket-framing--streaming-guidelines)
- [Virtual Agent Streaming and Event Handling Guidelines](#virtual-agent-streaming-and-event-handling-guidelines)
- [Detailed Flow with Sequence Diagrams](#detailed-flow-with-sequence-diagrams)
    - [Step 1. Start of Conversation](#step-1-start-of-conversation)
    - [Step 2. DTMF Input Flow](#step-2-dtmf-input-flow)
    - [Step 3. Audio Input Flow](#step-3-audio-input-flow)
        - [Step 3.1. WAV Audio Streaming](#step-31-wav-audio-streaming)
        - [Step 3.2. CHUNK Audio Streaming](#step-32-chunk-audio-streaming)
    - [Step 4. Barge-In Prompts](#step-4-barge-in-prompts)
    - [Step 5. Call Termination, Transfer, and Custom Event](#step-5-call-termination-transfer-and-custom-event)

---

## WebSocket Virtual Agent Simulators

This directory ships one reference simulator per (schema, language) pair. All simulators expose the same two endpoints — `/v1/va` for the per-call channel and `/v1/listVirtualAgents` for VA discovery — and implement the same conceptual contract, so they are interchangeable from the WxCC client's point of view. Pick the cell that matches your stack.

| Schema | Java simulator | Python simulator |
|---|---|---|
| **JSON** (text frames; audio base64-embedded) | [`json-schema/simulators/byova-websocket-json-java/`](./json-schema/simulators/byova-websocket-json-java/) | `json-schema/simulators/byova-websocket-json-python/` *(scaffolded — implementation in progress)* |
| **Protobuf** (binary frames; audio raw in `bytes` fields) | [`proto-schema/simulators/byova-websocket-proto-java/`](./proto-schema/simulators/byova-websocket-proto-java/) | `proto-schema/simulators/byova-websocket-proto-python/` *(scaffolded — implementation in progress)* |

Folder layout at a glance:

```
web-socket-interface/
├── README.md                                ← this file (framing, flows, diagrams)
├── resources/
│   └── diagrams/                            ← WebSocket sequence diagrams (ws-*.jpeg)
├── json-schema/
│   └── simulators/
│       ├── byova-websocket-json-java/       ← Spring Boot 4 + JSON; shipped
│       └── byova-websocket-json-python/     ← scaffolded
└── proto-schema/
    └── simulators/
        ├── byova-websocket-proto-java/      ← Spring Boot 4 + Protobuf; shipped
        └── byova-websocket-proto-python/    ← scaffolded
```

For per-simulator prerequisites, run instructions, configuration, JWS validation, Docker, and extension points, head to the README inside the simulator you choose:

- [`json-schema/simulators/byova-websocket-json-java/README.md`](./json-schema/simulators/byova-websocket-json-java/README.md) — `mvn spring-boot:run` (port `8086`).
- [`proto-schema/simulators/byova-websocket-proto-java/README.md`](./proto-schema/simulators/byova-websocket-proto-java/README.md) — `mvn spring-boot:run` (port `8086`).

The cells marked *scaffolded* contain a placeholder directory but no runtime code yet.

> The flow walkthroughs below are written using the **JSON** simulator's message names (`VOICE_VA_REQUEST`, `VOICE_VA_RESPONSE`, etc.) for concreteness — see the [JSON envelope DTO](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/WsEnvelopeBase.java) and the [`MessageType`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/MessageType.java) enum. The Protobuf simulator uses identically-named message types serialized as protobuf binary frames; the per-step semantics described here apply to both.

## WebSocket Framing & Streaming Guidelines

These rules are framing-level — they apply to every WebSocket BYoVA server regardless of schema. The Java simulators implement them in [`VirtualAgentWebSocketHandler`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/handler/VirtualAgentWebSocketHandler.java).

1. **One WebSocket session per call.** WxCC opens a single WebSocket to `/v1/va` when the caller's call connects, and uses it for the entire conversation. There is **no** per-interaction handshake — every event, every prompt, and every audio packet flows over the same socket until it is closed.
2. **Frame type per schema.**
    - **JSON variant** — all envelopes (control + audio) ride on **text frames**. The caller's audio is base64-encoded inside the JSON envelope's [`caller_audio_b64`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/VoiceInput.java#L17) field. Binary frames are not used.
    - **Protobuf variant** — all envelopes ride on **binary frames** carrying the protobuf-serialized message; the audio is shipped raw inside the `bytes` field, no base64 overhead.
3. **Envelope ordering.** Every envelope carries a monotonically increasing `seq` and a `conversation_id` (see [`WsEnvelopeBase`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/WsEnvelopeBase.java)). The VA Server must process incoming envelopes in `seq` order and stamp its own `seq` on outgoing ones; out-of-order delivery should be treated as a transport bug.
4. **Message types.** The wire-level discriminator is the envelope's `type` field (see [`MessageType`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/MessageType.java)): `VOICE_VA_REQUEST`, `VOICE_VA_RESPONSE`, `ERROR`, `PING`, `PONG`. Anything else must be ignored with a logged warning.
5. **Ping / Pong.** WxCC sends `PING` envelopes (or WebSocket-level pings) periodically as a liveness probe. The VA Server must respond with `PONG` (or a WebSocket-level pong) — the simulator handles this in [`handlePongMessage`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/handler/VirtualAgentWebSocketHandler.java#L62). Failing to answer pings will trigger an idle close.
6. **Close codes.** Either side may initiate the close. The VA Server should close with `CloseStatus.NORMAL` after a `SESSION_END`; transport errors should close with `CloseStatus.SERVER_ERROR` (the simulator does this in [`handleTransportError`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/handler/VirtualAgentWebSocketHandler.java#L70)). After close, no further envelopes are accepted; a new call requires a new WebSocket.
7. **Discovery channel.** The separate `/v1/listVirtualAgents` endpoint follows a request/response pattern (one inbound `LIST_VIRTUAL_AGENTS` envelope, one outbound list, then close). It is unrelated to the per-call `/v1/va` lifecycle.

## Virtual Agent Streaming and Event Handling Guidelines

These rules describe the BYoVA event contract — they are framing-independent and apply identically to the gRPC and WebSocket variants.

1. The sequence of events must follow the same order as outlined in the sequence diagrams below.
2. The welcome prompt must be sent in response to the [`SESSION_START`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/EventInputType.java#L9) input event.
3. Sending [`END_OF_INPUT`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/OutputEventType.java#L21) immediately stops the caller's audio streaming, so it should be sent only on silence detection from the caller. (This is **not** true if [barge-in](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/Prompt.java) is enabled — see Step 4.)
4. If the caller does not provide any input within the configured timeout, the [`NO_INPUT`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/EventInputType.java#L15) event will be triggered.
5. Switching between Voice and DTMF is achieved by setting the desired [`input_mode`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/VoiceVAResponse.java#L27) on the [`VoiceVAResponse`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/VoiceVAResponse.java). There are three input modes (see [`VoiceVAInputMode`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/VoiceVAInputMode.java)):
    - `INPUT_VOICE` — only voice input is accepted.
    - `INPUT_EVENT_DTMF` — only DTMF input is accepted.
    - `INPUT_VOICE_DTMF` — both voice and DTMF inputs are accepted.

   If `input_mode` is not specified, `INPUT_VOICE_DTMF` is used as the default.



## Detailed Flow with Sequence Diagrams

> The diagrams below use a colour-coded swim-lane convention; see [`ws-color-code-names.jpeg`](./resources/diagrams/ws-color-code-names.jpeg) for the legend (caller, VA client / WxCC, VA server, AI service).

### Step 1. Start of Conversation

1. The reference simulator starts up as a WebSocket Virtual Agent Server (**VA Server**) and listens on `/v1/va`.
2. When the caller's call is connected, the VA Client opens a WebSocket to the VA Server. The handshake carries the JWS in the `Authorization` header — the simulator's [`AuthorizationHandshakeInterceptor`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/auth/AuthorizationHandshakeInterceptor.java) validates it before opening the session.
3. Immediately after the upgrade succeeds, the VA Client sends its first envelope — a `VOICE_VA_REQUEST` whose payload is a [`VoiceVARequest`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/VoiceVARequest.java) carrying the [`SESSION_START`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/EventInputType.java#L9) event. The [`conversation_id`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/WsEnvelopeBase.java#L27) is reused for the entire conversation. This first request is sent without any audio data.
4. `SESSION_START` can be used by the VA Server to start a session with its AI service and reply with a `VOICE_VA_RESPONSE` envelope wrapping a [`VoiceVAResponse`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/VoiceVAResponse.java). The response can include payloads, prompts, NLU data, and the [input mode](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/VoiceVAInputMode.java) for the next interaction. Prompts contain the audio that will be played to the caller; one or more prompts can be returned in a single response and are played at the client side in the order received.
5. The WebSocket session stays open. The VA Client will continue to drive the conversation by sending further `VOICE_VA_REQUEST` envelopes (audio, DTMF, events) on the **same** session.

<img src="./resources/diagrams/ws-session-start-flow.jpeg" alt="WebSocket session start sequence diagram showing the WS upgrade, SESSION_START envelope, welcome-prompt VOICE_VA_RESPONSE, and the session staying open" style="box-shadow: 5px 4px 8px rgba(4, 2, 2, 0.1); border: 1px solid #ccc; border-radius: 4px;">

### Step 2. DTMF Input Flow

1. When the caller enters DTMF input by pressing keys on the phone keypad, the VA Client sends a `VOICE_VA_REQUEST` envelope carrying the [`START_OF_DTMF`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/EventInputType.java#L18) event.
2. The `START_OF_DTMF` event signals that the caller has begun entering DTMF inputs. Based on this event, specific actions can be taken on the server side, such as preparing expected-value matching, updating UI flags, or pausing prompt generation.
3. No `VOICE_VA_RESPONSE` is required for `START_OF_DTMF` itself — the VA Server simply acknowledges by continuing to listen on the socket.
4. Once the caller has finished entering DTMF input, the VA Client sends a follow-up `VOICE_VA_REQUEST` carrying [`DTMFInputs`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/DTMFInputs.java). The terminating condition is one of:
    - The [`DTMF input length`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/DTMFInputConfig.java) requirement is satisfied (the expected number of digits has been entered).
    - The [`inter-digit timeout`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/DTMFInputConfig.java) elapses.
    - The [`termination character`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/DTMFInputConfig.java) is pressed (default `#`, see [`DTMFDigits`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/DTMFDigits.java)).
5. The received DTMF inputs can be processed according to the use case. For example, the VA Server can send back a `VOICE_VA_RESPONSE` containing an audio prompt asking the caller to confirm the entry by pressing `1`.
6. The caller confirms the previously entered DTMF input by pressing `1`, which arrives as another `VOICE_VA_REQUEST` carrying that single digit.
7. Another prompt (e.g. status or information based on the DTMF inputs) can be sent in response as another `VOICE_VA_RESPONSE`.

<img src="./resources/diagrams/ws-dtmf-flow.jpeg" alt="WebSocket DTMF flow sequence diagram showing START_OF_DTMF, DTMFInputs, confirmation prompt, and follow-up DTMF on the same session" style="box-shadow: 5px 4px 8px rgba(0, 0, 0, 0.1); border: 1px solid #ccc; border-radius: 4px;">

### Step 3. Audio Input Flow

At the start of the call, the VA Server must choose between **WAV Streaming** and **CHUNK Streaming**; this decision must not change during the call. For scripted virtual agents where prompts are pre-configured the VA Server should use WAV streaming; for longer prompts produced by LLMs it should use CHUNK streaming. The simulator's mode is controlled by `voice.va.audio.use-chunked-audio` in [`application.properties`](./json-schema/simulators/byova-websocket-json-java/src/main/resources/application.properties).

- **WAV Streaming** — always send the response as [`FINAL`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/ResponseType.java#L6) in a single `VOICE_VA_RESPONSE` envelope, with the WAV header included in the audio bytes.
- **CHUNK Streaming** — send the audio across multiple `VOICE_VA_RESPONSE` envelopes of type [`CHUNK`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/ResponseType.java#L12), and terminate the prompt with a final `VOICE_VA_RESPONSE` of type [`FINAL`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/ResponseType.java#L6) carrying **empty** audio bytes. The minimum `CHUNK` size is 100 bytes and the maximum is 64 KB (65,536 bytes); keep chunks as large as possible up to that limit.

#### Step 3.1. WAV Audio Streaming

1. When the caller starts speaking, the VA Client sends `VOICE_VA_REQUEST` envelopes carrying caller audio over the open WebSocket session.
2. The VA Server must be capable of detecting both speech and silence in the caller's audio (the simulator's [`SpeechDetectionService`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/service/SpeechDetectionService.java) shows one approach using an amplitude threshold).
3. Once speech is detected, send a `VOICE_VA_RESPONSE` containing the [`START_OF_INPUT`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/OutputEventType.java#L18) output event.
4. Continue consuming caller audio envelopes until silence is detected.
5. Once silence is detected, send a `VOICE_VA_RESPONSE` containing the [`END_OF_INPUT`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/OutputEventType.java#L21) output event. (Sending `END_OF_INPUT` immediately stops the caller's audio stream from the VA Client.)
6. The VA Server must wait for response generation to complete so that all responses can be sent in a single `VOICE_VA_RESPONSE` envelope. (The audio must include a WAV header for each `FINAL` envelope.)
7. Send all responses, either as one prompt or a list of prompts, with response type `FINAL` in a single envelope. (More than one envelope is not permitted for `FINAL` responses in WAV mode.)

<img src="./resources/diagrams/ws-wav-streaming-flow.jpeg" alt="WebSocket WAV streaming sequence diagram showing caller audio envelopes, START_OF_INPUT and END_OF_INPUT output events, and a single FINAL VOICE_VA_RESPONSE with the full WAV prompt" style="box-shadow: 5px 4px 8px rgba(0, 0, 0, 0.1); border: 1px solid #ccc; border-radius: 4px;">

#### Step 3.2. CHUNK Audio Streaming

1. When the caller starts speaking, the VA Client sends `VOICE_VA_REQUEST` envelopes carrying caller audio over the open WebSocket session.
2. The VA Server must be capable of detecting both speech and silence in the caller's audio.
3. Once speech is detected, send a `VOICE_VA_RESPONSE` containing the [`START_OF_INPUT`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/OutputEventType.java#L18) output event.
4. Continue consuming caller audio envelopes until silence is detected.
5. Once silence is detected, send a `VOICE_VA_RESPONSE` containing the [`END_OF_INPUT`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/OutputEventType.java#L21) output event. (Sending `END_OF_INPUT` immediately stops the caller's audio stream from the VA Client.)
6. The VA Server does not need to wait for response generation to finish. Audio responses can be sent in multiple `VOICE_VA_RESPONSE` envelopes with response type [`CHUNK`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/ResponseType.java#L12), without WAV headers, as soon as they are ready.
7. The last `VOICE_VA_RESPONSE` for this prompt must contain empty audio bytes and response type [`FINAL`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/ResponseType.java#L6) — this signals "end of prompt" to the VA Client.

<img src="./resources/diagrams/ws-chunk-streaming-flow.jpeg" alt="WebSocket CHUNK streaming sequence diagram showing multiple CHUNK VOICE_VA_RESPONSE envelopes followed by a terminating empty FINAL envelope" style="box-shadow: 5px 4px 8px rgba(0, 0, 0, 0.1); border: 1px solid #ccc; border-radius: 4px;">

### Step 4. Barge-In Prompts

Every prompt has a `barge_in` flag (`true`/`false`) on [`Prompt`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/Prompt.java). When barge-in is enabled, any prompt — regardless of its [`ResponseType`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/ResponseType.java) (`CHUNK`, `FINAL`, `PARTIAL`) or [`VoiceVAInputMode`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/VoiceVAInputMode.java) — can be barged in by caller input, except prompts associated with a termination or transfer event. The sequence diagram below uses the [`INPUT_VOICE_DTMF`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/VoiceVAInputMode.java#L15) input mode, meaning that the VA Client will continually send caller-audio envelopes (silent until the caller speaks); the same applies to [`INPUT_VOICE`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/VoiceVAInputMode.java#L9). For [`INPUT_EVENT_DTMF`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/VoiceVAInputMode.java#L12), envelopes are sent only when the caller submits DTMF input.

1. After [`SESSION_START`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/EventInputType.java#L9), the VA Server sends an 8-second Welcome-Prompt with `barge_in` enabled in a single `VOICE_VA_RESPONSE` envelope. The WebSocket session stays open.
2. The VA Client begins streaming silent caller-audio envelopes on the same session.
3. As soon as the caller enters DTMF input — say, after hearing 4 seconds of the prompt — it barges in on the Welcome-Prompt. The VA Client sends a `VOICE_VA_REQUEST` carrying the [`START_OF_DTMF`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/EventInputType.java#L18) event followed by a `VOICE_VA_REQUEST` carrying the collected DTMF digits.
4. The VA Server processes the DTMF inputs and sends Prompt-1 and Prompt-2 with `barge_in` disabled in `VOICE_VA_RESPONSE` envelopes.
5. Prompt-1 and Prompt-2 cannot be barged in and will be played in full even if the caller speaks or enters DTMF; any caller input at this point is dropped at the client side.
6. The VA Client resumes streaming caller audio. The VA Server detects speech and sends a `START_OF_INPUT` `VOICE_VA_RESPONSE`.
7. The VA Server collects caller audio until silence is detected, then sends `END_OF_INPUT`.
8. The VA Server sends 4 prompts with `barge_in` enabled.
9. Prompt-3 and Prompt-4 are played to the caller; the caller then barges in, causing Prompt-5 and Prompt-6 to be dropped at the client side.
10. The VA Server detects the caller's speech and sends `START_OF_INPUT` again, then collects audio until silence is detected.
11. On detecting silence, the VA Server sends `END_OF_INPUT` and the final Prompt-7 (with `barge_in` disabled) is played to the caller.

<img src="./resources/diagrams/ws-barge-in-flow.jpeg" alt="WebSocket barge-in flow sequence diagram showing barge-in-enabled prompts being interrupted by caller DTMF and speech, and barge-in-disabled prompts playing through to completion" style="box-shadow: 5px 4px 8px rgba(0, 0, 0, 0.1); border: 1px solid #ccc; border-radius: 4px;">

### Step 5. Call Termination, Transfer, and Custom Event

A call can be terminated, transferred to a live agent, or used to drive a custom action (e.g. moving the caller to another queue). All three are signalled via the [`OutputEventType`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/OutputEventType.java) / [`EventInputType`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/EventInputType.java) enums on the existing WebSocket session — there is no separate teardown channel.

1. **Transfer to agent** — an ongoing call with a virtual agent can be transferred to a live agent by sending the [`TRANSFER_TO_AGENT`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/OutputEventType.java#L12) output event in a `VOICE_VA_RESPONSE` envelope, optionally accompanied by an audio prompt. The VA Client will play the prompt, hand the call to a live agent, and close the WebSocket.
2. **Session end from server** — the VA Server can disconnect the call by sending the [`SESSION_END`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/OutputEventType.java#L9) output event in a `VOICE_VA_RESPONSE` envelope, optionally accompanied by an audio prompt. After the prompt is played, the VA Client closes the WebSocket; the VA Server should reciprocate with `CloseStatus.NORMAL`.
3. **Session end from client** — when the caller hangs up, the VA Client sends a `VOICE_VA_REQUEST` carrying the [`SESSION_END`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/EventInputType.java#L12) input event and then closes the WebSocket. No prompt can be sent in response — the socket is gone by the time the VA Server reads the event.
4. **Custom event** — [`CUSTOM_EVENT`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/OutputEventType.java#L15) (output) and [`CUSTOM_EVENT`](./json-schema/simulators/byova-websocket-json-java/src/main/java/com/cisco/wccai/ws/voice/constant/EventInputType.java#L21) (input) can be used to trigger pre-configured custom actions (queue moves, CRM screen-pops, analytics tagging, etc.) without ending the session.

> The websocket-interface diagram set does not yet ship a dedicated call-end sequence diagram. The flow is identical to the gRPC variant on the conceptual level — see the [multi-RPC call-end diagram](../grpc-interface/multi-rpc/resources/diagrams/voice-va-call-end-flow.jpg) for a visual reference, substituting "RPC" with "VOICE_VA_RESPONSE envelope on the WebSocket".

---

> **Note:** These flow diagrams are illustrative and may not cover every possible scenario or edge case. The actual implementation may vary based on specific requirements, configurations, and the language / framework you choose. For any questions or clarifications regarding the call flow, please refer to the simulator README ([`json-schema/simulators/byova-websocket-json-java/README.md`](./json-schema/simulators/byova-websocket-json-java/README.md)) or contact the support team.
