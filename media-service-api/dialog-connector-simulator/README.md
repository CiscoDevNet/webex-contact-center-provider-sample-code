# Table of Contents

1. [Bring your own virtual agent](#byova-section)
2. [Media Forking](#media-forking-section)


# Bring your own Virtual Agent <a name="byova-section"></a>

The Bring Your Own Virtual Agent Initiative empowers Developers and AI vendors to seamlessly integrate external conversational interface(s) with Webex Contact Center IVR.
More details of this explained in [file](../../README.md)

## Using Dialog Connector Simulator for BYoVA
The Dialog Connector Simulator is a sample code that demonstrates how to integrate an external conversational interface with Webex Contact Center IVR.

Refer to the [Dialog Connector Simulator Sample Code](https://github.com/CiscoDevNet/webex-contact-center-ai-sample-code/tree/main/provider-api/dialog-connector-simulator).
For the interface definition see `src/main/proto/com/cisco/wcc/ccai/media/v1/VoiceVirtualAgent.proto`.


### Onboarding Steps for Using the Dialog Connector Simulator
![VA-flow](./src/main/resources/images/VAProvisioning.jpg)

*Fig 2: Architectural Diagram for provisioning a Virtual Agent*

General guidelines of customer/partner onboarding is mentioned [here](./README.md)

## Dialog Connector Application Development
### Code Overview
This sample code offers an overview of the various methods and messages used when the Dialog Connector interacts with
the Webex CC VA Client Application.

Here,the Dialog Connector represents a **gRPC Server Application**(see `src/main/java/com/cisco/wccai/grpc/server/GrpcServer.java`) that listens for incoming requests from the
Webex CC VA Client Application which is a **gRPC Client Application**(see `src/main/java/com/cisco/wccai/grpc/client/VoiceVAClient.java`).



### Development Environment Commands
1. Install Java 17.
Verify the Installation by opening a new terminal and run:

    `java -version`
    
2. Configure the application:
   Edit `src/main/resources/config.properties` and update the configuration values for your environment:
   ```properties
   # For local development with ngrok, update this to your ngrok URL
   DATASOURCE_URL = https://your-ngrok-id.ngrok-free.app
   
   # Other important settings
   API_URL = localhost
   PORT = 8086
   USE_TLS = false
   ```

3. Compile Protobuf Definitions: This will generate java classes under target/generated-sources/protobuf/grpc-java and target/generated-sources/protobuf/java.
    
    `cd webex-contact-center-byova-sample-code/media-service-api/dialog-connector-simulator`

    `mvn clean compile`
    
4. Build the Main Application:

   `mvn clean install`

5. Run the gRPC Server:

   **Option 1 - Using Maven exec plugin:**
   ```bash
   mvn exec:java -Dexec.mainClass="com.cisco.wccai.grpc.server.GrpcServer"
   ```

   **Option 2 - Using the generated JAR:**
   ```bash
   java -jar target/dialog-connector-simulator-1.0.0-SNAPSHOT-allinone.jar
   ```

   **Option 3 - Using Java directly:**
   ```bash
   java -cp target/classes:target/dependency/* com.cisco.wccai.grpc.server.GrpcServer
   ```

   The server will start and listen for incoming gRPC connections. You should see log output indicating the server has started successfully.

### Configuration

The Dialog Connector Simulator uses the `src/main/resources/config.properties` file for configuration. Key properties include:

#### JWT Validation Configuration
- **DATASOURCE_URL**: The datasource URL used for JWT token validation. This must match the `com.cisco.datasource.url` claim in the JWT token received from Webex Contact Center.
  - For local development with ngrok: `https://your-ngrok-id.ngrok-free.app`
  - For production: Your actual service URL (e.g., `https://your-domain.com:443`)
  - Default: `https://dialog-connector-simulator.intgus1.ciscoccservice.com:443`

#### Audio Configuration
- **AUDIO_ENCODING_TYPE**: Supported types - `LINEAR16`, `MULAW` (default: `MULAW`)
- **SAMPLE_RATE_HERTZ**: Audio sample rate (default: `8000`)
- **BUFFER_SIZE**: Audio buffer size (default: `8192`)

#### Server Configuration
- **API_URL**: Endpoint to connect (default: `localhost`)
- **PORT**: Server port - TLS: `443`, NonTLS: `31400` (default: `8086`)
- **USE_TLS**: Enable/disable TLS (default: `false`)

#### Other Configuration
- **LANGUAGE_CODE**: Language code for processing (default: `en-US`)
- **ORG_ID**: Organization identifier (default: `org_01`)
- **PROMPT_DURATION_MS**: Prompt duration in milliseconds (default: `10000`)
- **AUDIO_DURATION_MS**: Audio duration in milliseconds (default: `60000`)
- **SAVE_INPUT_AUDIO**: Whether to save input audio (default: `true`)

**Important**: When using ngrok for local development, make sure to update the `DATASOURCE_URL` property with your current ngrok URL to avoid JWT validation failures.

### Troubleshooting

#### JWT Claims Validation Failed Error
If you encounter the error `Claims validation failed` in your logs, this is typically caused by one of the following issues:

1. **Datasource URL Mismatch**: The most common cause is when the `DATASOURCE_URL` in your `config.properties` doesn't match the `com.cisco.datasource.url` claim in your JWT token.
   - **Solution**: Update the `DATASOURCE_URL` property to match your current service URL
   - **For ngrok users**: Update the URL whenever you restart ngrok and get a new URL

2. **Missing Required JWT Claims**: Ensure your JWT token contains all required claims:
   - `iss` (issuer) - must be one of the valid Webex issuers
   - `aud` (audience) - must be present
   - `sub` (subject) - must be present  
   - `jti` (JWT ID) - must be present
   - `com.cisco.datasource.url` - must match your `DATASOURCE_URL`
   - `com.cisco.datasource.schema.uuid` - must be `5397013b-7920-4ffc-807c-e8a3e0a18f43`

3. **Invalid Issuer**: The JWT issuer must be one of the supported Webex identity brokers:
   - `https://idbrokerbts.webex.com/idb`
   - `https://idbrokerbts-eu.webex.com/idb`
   - `https://idbroker.webex.com/idb`
   - `https://idbroker-eu.webex.com/idb`
   - `https://idbroker-b-us.webex.com/idb`
   - `https://idbroker-ca.webex.com/idb`

To debug JWT issues, you can decode your JWT token using tools like [jwt.io](https://jwt.io) to verify the claims.


### gRPC Bi-directional Streaming Guidelines
1. _onNext_, _onError_, and _onCompleted_ are gRPC methods defined in the [StreamObserver<T>](https://grpc.github.io/grpc-java/javadoc/io/grpc/stub/StreamObserver.html) interface for the Java language. The names of these methods and their API signatures vary due to language-specific idioms and implementations of the gRPC library. For more details, please refer to the [gRPC documentation](https://grpc.io/docs/languages/). 
2. For each RPC, _onCompleted_ will be called from the VA Client side after all the data has been sent, and the RPC will be deemed half-closed. Once the VA Server has finished sending all the responses for the same RPC, _onCompleted_ must be called to fully close the RPC. 
3. Each RPC must be closed by calling _onCompleted_ in the end except in cases of unexpected call termination scenarios.



### Virtual Agent Streaming and Event Handling Guidelines
1. The sequence of events must follow the same order as outlined in the sequence diagrams. 
2. Welcome prompt should be sent in response to the [SESSION_START](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L182) event.
3. Sending [END_OF_INPUT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L162) will immediately stop the caller's audio streaming. So it should be sent upon silence detection from the caller's end. [This is NOT true if [barge-in](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L123) is enabled.]
4. If the caller does not provide any input within the configured timeout duration, the [NO_INPUT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L185) event will be triggered. 
5. Switching between Voice and DTMF can be achieved by setting the required [INPUT_MODE](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L91) in the [VoiceVAResponse](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L77). There are three input modes:
   - [INPUT_VOICE](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L129) - Only Voice input is accepted
   - [INPUT_EVENT_DTMF](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L130) - Only DTMF input is accepted
   - [INPUT_VOICE_DTMF](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L131) - Both Voice and DTMF inputs are accepted
   
   If INPUT_MODE is not specified then [INPUT_VOICE_DTMF](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L131) will be considered as default.



### Detailed Flow with Sequence Diagram
### Step 1. Start of Conversation
1. The Dialog Connector will start up as a gRPC Virtual Agent Server Application (**VA Server**).
2. When the caller's call is connected, the VA Client establishes a gRPC connection with the VA Server by creating a new conversation (`conversation_id`) and sending a [VoiceVARequest](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L16) to the VA Server with SESSION_START event. The `conversation_id` is used for the entire conversation between the VA Client and VA Server. The request is sent without any audio data.
3. [SESSION_START](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L182) can be used by the connector to start the session with its AI Service and return a response back to the Client using [ViceVAResponse](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L77). It could contain response payloads, prompts, NLU data, and input mode for handling the next interactions from the Caller. Prompts contain the audio which needs to be played to the Caller. It can return one or multiple prompts in a response. Prompts are played one after another at the client side in the sequence of receiving.
4. New RPC is initiated with [SESSION_START](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L182) event of type [EVENT_INPUT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L41) from VA Client to VA Server.
5. Once prompt is sent, VA Server should call onCompleted. RPC is completed. A new RPC will be initiated to handle further events.

<img src="./src/main/resources/images/voice-va-session-start-flow.jpg" alt="Description" style="box-shadow: 5px 4px 8px rgba(0, 0, 0, 0.1); border: 1px solid #ccc; border-radius: 4px;">


### Step 2. DTMF Input Flow
1. If caller enters DTMF input by pressing numbers on the phone's keypad then the new RPC-1 gets initiated with [START_OF_DTMF](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L186) event of type [EVENT_INPUT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L41) from VA Client to VA Server. 
2. [START_OF_DTMF](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L186) event indicates that the caller is entering DTMF inputs. Based on this event, specific actions can be taken, such as populating required values or updating flags. 
3. In response to [START_OF_DTMF](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L186) event, onCompleted should be called to complete the RPC-1. 
4. Once the caller has finished entering the DTMF input, it will be sent to VA Server when one of the following conditions is met:
   - [DTMF input length](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L100) requirement is satisfied, meaning the expected number of digits has been entered.
   - An [inter-digit timeout](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L98) occurs.
   - [Termination character](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L99) is pressed.

5. RPC-2 is initiated with the DTMF inputs of type [DTMF_INPUT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L38). 
6. Received DTMF inputs or digits can be process according to the use case. For example, an audio prompt can be sent to confirm the DTMF entries by pressing '1' in onNext followed by onCompleted. RPC-2 is completed. 
7. Caller confirms the previously entered DTMF inputs by pressing '1', RPC-3 is initiated with caller's confirmation DTMF input. 
8. Another prompt can be sent in response to caller's confirmation. For example, an audio prompt with status or information based on the DTMF inputs in onNext followed by onCompleted. RPC-3 is completed.

<img src="./src/main/resources/images/voice-va-dtmf-flow.jpg" alt="Description" style="box-shadow: 5px 4px 8px rgba(0, 0, 0, 0.1); border: 1px solid #ccc; border-radius: 4px;">

### Step 3. Audio Input Flow
At the start of the call, the VA Server must choose between WAV Streaming and CHUNK Streaming, this decision should not be altered during the call. For scripted virtual agents where prompts are pre configured VA Server should use WAV streaming and for longer prompts with LLM models, VA Server should use CHUNK streaming.
   - In the case of WAV Streaming, always send the response as [FINAL](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L110) in single onNext with the WAV header in the audio, followed by onCompleted.
   - In the case of CHUNK Streaming, always send a [FINAL](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L110) response with EMPTY audio after all the [CHUNK](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L112) responses, followed by onCompleted. Minimum [CHUNK](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L112) size is 100 bytes, and there is no maximum limit. It is recommended to keep the [CHUNK](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L112) size as large as possible.
   
### Step 3.1. WAV Audio Streaming
1. If caller starts speaking, a new RPC is initiated with caller's audio from VA Client to VA Server. 
2. VA Server should be capable of detecting both the caller's speech and silence. 
3. Once the caller's speech is detected, send the [START_OF_INPUT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L160) event in the onNext. 
4. Continue to consume the audio from the caller until silence is detected. 
5. Once silence is detected, send the [END_OF_INPUT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L162) event. [Please note that sending the [END_OF_INPUT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L162) will immediately stop streaming the caller's audio to the VA Server.]
6. VA Server must wait for the response generation to complete so that all responses can be sent in onNext. [Audio must have a WAV header for each onNext.]
7. Send all responses in one or as a list of prompts with the response type [FINAL](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L110) in a single onNext, followed by onCompleted. RPC is completed. [More than one onNext is not permitted to send the responses with the response type [FINAL](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L110).]

<img src="./src/main/resources/images/voice-va-wav-streaming.jpg" alt="Description" style="box-shadow: 5px 4px 8px rgba(0, 0, 0, 0.1); border: 1px solid #ccc; border-radius: 4px;">

### Step 3.2. CHUNK Audio Streaming
1. If caller starts speaking, a new RPC is initiated with caller's audio from VA Client to VA Server. 
2. VA Server should be capable of detecting both the caller's speech and silence. 
3. Once the caller's speech is detected, send the [START_OF_INPUT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L160) event in the onNext. 
4. Continue to consume the audio from the caller until silence is detected. 
5. Once silence is detected, send the [END_OF_INPUT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L162) event. [Please note that sending the [END_OF_INPUT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L162) will immediately stop streaming the caller's audio to the VA Server.]
6. VA Server does not need to wait for the response generation to finish. Audio responses can be sent in multiple onNext calls with the response type [CHUNK](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L112), without WAV headers, as soon as they are ready. 
7. Last onNext must have EMPTY audio/bytes and response type must be [FINAL](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L110). 
8. Send the onCompleted after [FINAL](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L110) onNext, RPC is completed.

<img src="./src/main/resources/images/voice-va-chunk-streaming-flow.jpg" alt="Description" style="box-shadow: 5px 4px 8px rgba(0, 0, 0, 0.1); border: 1px solid #ccc; border-radius: 4px;">

### Step 4. Barge-In Prompts
Every prompt will have an config option to set [barge-in](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L123) to TRUE or FALSE. If barge-in is enabled, any prompt with any [ResponseType (CHUNK, FINAL, PARTIAL)](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L109) or [VoiceVAInputMode](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L127) will be barged-in on caller's input except termination or transfer event prompts. The following sequence diagram uses the [INPUT_VOICE_DTMF](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L131) input mode that means every new RPC will contain silent audio packets until caller speaks and the same applies to [INPUT_VOICE](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L129) as well. If the input mode is [INPUT_EVENT_DTMF](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L130), a new RPC will be triggered upon DTMF input submission from the caller's end.

1. After [SESSION_START](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L182), RPC-1 gets half-closed, which means the VA Client cannot send anything in RPC-1. Since [barge-in](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L123) is enabled, RPC-2 is initiated. 
2. VA Server sends a Welcome-Prompt of duration 8 seconds with [barge-in](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L123) enabled, followed by onCompleted. RPC-1 is completed. 
3. RPC-2 initially has silence audio, but as soon as the caller enters DTMF input after hearing 4 seconds of the prompt, it barges in on the Welcome-Prompt playback in the middle and sends the [START_OF_DTMF](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L186) event to the VA Server. 
4. VA Server responds to [START_OF_DTMF](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L186) event with onCompleted. RPC-2 is completed. 
5. RPC-3 is initiated with DTMF inputs, VA Server receives the DTMF inputs and sends Prompt-1 and Prompt-2 with [barge-in](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L123) disabled. 
6. Prompt-1 and Prompt-2 cannot be barged in and will be played completely even if the caller tries to speak or provide DTMF input. The caller's input at this point will be dropped. 
7. RPC-3 is completed and RPC-4 is initiated with caller's audio and VA Server detects caller's speech and sends [START_OF_INPUT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L160) event. 
8. VA Server collects caller's audio until silence is detected and then sends [END_OF_INPUT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L162) event. 
9. VA Server sends 4 prompts with [barge-in](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L123) enabled and RPC-5 is initiated. 
10. Prompt-3 and Prompt-4 are played back to the caller, and then the caller barges in, causing Prompt-5 and Prompt-6 to be dropped. 
11. VA Server detects caller's speech and sends [START_OF_INPUT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L160) event again and collects audio until the silence is detected. 
12. Upon detecting silence, the VA Server sends an [END_OF_INPUT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L162) event, and the last Prompt-7, with [barge-in](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L123) disabled, will be played back to the caller.


<img src="./src/main/resources/images/voice-va-barge-in-flow.jpg" alt="Description" style="box-shadow: 5px 4px 8px rgba(0, 0, 0, 0.1); border: 1px solid #ccc; border-radius: 4px;">


### Step 5. Call Termination, Transfer, and Custom Event
Call can be terminated, transferred to an agent, or a custom action can be performed, such as sending the caller to another queue.

1. Transfer to agent: An ongoing call with a virtual agent can be transferred to a live agent by sending the [TRANSFER_TO_AGENT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L153) output event, along with an optional audio prompt. 
2. Session end from server application: Call can be disconnected from the VA Server side by sending the [SESSION_END](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L152) output event, along with an optional audio prompt. 
3. Session end from client application: When the caller disconnects the call, a [SESSION_END](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L183) input event will be sent to the VA Server, and no prompt can be sent. 
4. Custom event: [CUSTOM_EVENT](https://github.com/webex/dataSourceSchemas/blob/f625b9f80dd0673bc0da01f443e31104a1a66dbd/Services/VoiceVirtualAgent_5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L154) can be sent to perform preconfigured custom actions.

<img src="./src/main/resources/images/voice-va-call-end-flow.jpg" alt="Description" style="box-shadow: 5px 4px 8px rgba(0, 0, 0, 0.1); border: 1px solid #ccc; border-radius: 4px;">



# Media Forking <a name="media-forking-section"></a>

This feature allows customers to access the media which is the real time interaction between the human agent and the caller in the Webex Contact Center.

## Using Dialog Connector Simulator for Media Forking

The Dialog Connector Simulator is a sample code that demonstrates how to receive the media from the Webex Contact Center and do the further processing.

Refer to the [Dialog Connector Simulator Sample Code](https://github.com/CiscoDevNet/webex-contact-center-provider-sample-code/tree/main/media-service-api/dialog-connector-simulator).
For the interface definition see `src/main/proto/com/cisco/wcc/ccai/media/v1/conversationaudioforking.proto`.

The proto has a bidirectional streaming RPC where the client(Webex contact center) streams the audio during the call and server(customer/partner) sends acknowledgement once when onComplete() is received.

### Code Overview

This sample code offers an overview of the various methods and messages used when the when Webex contact center interacts with the dialog connector simulator server.

Here,the dialog connector simulator server represents a **gRPC Server Application**(see `src/main/java/com/cisco/wccai/grpc/server/GrpcServer.java`) that listens for incoming requests from the Webex Contact Center.

### Development Environment Commands
1. Install Java 17.
   Verify the Installation by opening a new terminal and run:

   `java -version`
2. Compile Protobuf Definitions: This will generate java classes under target/generated-sources/protobuf/grpc-java and target/generated-sources/protobuf/java.

   `cd webex-contact-center-provider-sample-code/media-service-api/dialog-connector-simulator`

   `mvn clean compile`
3. Build the Main Application:

   `mvn clean install`
4. Run the gRPC Server:

   **Option 1 - Using Maven exec plugin:**
   ```bash
   mvn exec:java -Dexec.mainClass="com.cisco.wccai.grpc.server.GrpcServer"
   ```

   **Option 2 - Using the generated JAR:**
   ```bash
   java -jar target/dialog-connector-simulator-1.0.0-SNAPSHOT-allinone.jar
   ```

   **Option 3 - Using Java directly:**
   ```bash
   java -cp target/classes:target/dependency/* com.cisco.wccai.grpc.server.GrpcServer
   ```

5. The Dialog Connector will start up as a **gRPC Server Application** (`run GrpcServer.java`).

### Detailed Flow with Sequence Diagram

<img src="./src/main/resources/images/media-forking-sequence.jpg" alt="Description" style="box-shadow: 5px 4px 8px rgba(0, 0, 0, 0.1); border: 1px solid #ccc; border-radius: 4px;">

> **_NOTE:_** The customer using this feature would write the logic to do further processing of the media received based on their requirements. The simulator code above does not do any further processing with the audio received. 


