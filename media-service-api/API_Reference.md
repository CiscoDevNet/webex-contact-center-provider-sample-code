# Table of Contents
1. [Media Service APIs](#media-service-api-section)
1.1. [Authentication and Validation of the Source](#authentication-section)
1.2. [Bring Your Own Virtual Agent](#byova-section)
1.2.1. [Services](#byova-services-section)
1.2.1.1. [List Virtual Agents](#list-va-section)
1.2.1.2. [Process Caller Input](#process-audio-section)
1.3. [Real Time Media Forking](#media-forking-section)
1.3.1. [Services](#forking-services-section)
1.3.1.1. [Media Forking](#forking-audio-section")
2. [References](#references-section)


# Media Service APIs <a name="media-service-api-section"></a>
Provides interface to handle media related use cases for webex contact center platform consumers.
There are different set of APIs for different use cases like Virtual agent, Real time transcripts(transcripts of the ongoing conversation between caller and human agent)
This document captures details of these APIs.
CCAI Platform offer below services as part of media service APIs-
1. BYoVA(Bring your own Virtual)- Where a provider can integrate their Virtual agents within webex contact center platform. Communication between the webex contact center client and Virtual agent happens over gRPC.
2. Real Time Media forking(Human agent - caller interaction)- Where a provider/customer can register a url with us where they want to receive the forked audio of the conversation between the agent(human agent) and caller.
More details about this , please refer [link](https://developer.webex-cx.com/documentation/guides/bring-your-own-virtual-agent)
This document describes the streaming APIs and relavant gRPC services provided by webex contact center platform for aobe mentioned use cases.

## Authentication and Validation of the source <a name="authentication-section"></a>
JWS(signed JWT) will be used as validation of the source from Virtual agent applications.
JWS will be created by Cisco/Webex while registering the [data source](https://developer.webex.com/create/docs/bring-your-own-datasource), for the respective org and will be signed using Cisco's private key.
This JWS will be returned as API response(data source registration) and should be stored/cached by the virtual agent application(at vendor/customer end).
When Contact center will try to establish a gRPC connection with Virtual agent application, it will send JWS along.
Virtual agent application(server), needs to validate this JWS as per process explained [here](https://developer.webex.com/create/docs/bring-your-own-datasource#verify-the-jws-token)
This step is necessary for Virtual agent application(server) to know that the gRPC connection is being established by Cisco.

## Bring your Own Virtual Agent <a name="byova-section"></a>

### Services <a name="byova-services-section"></a>

#### List Virtual Agents <a name="list-va-section"></a>
This Service is used to list virtual agents on the flow UI.
rpc ListVirtualAgents(ListVARequest) returns (ListVAResponse) {}

**Parameters**

[ListVARequest](https://github.com/webex/dataSourceSchemas/blob/098583adedf9a811c0170e45d986a57728773628/Services/VoiceVirtualAgent/5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L112) is the request param in the list virtual agent service.

[ListVAResponse](https://github.com/webex/dataSourceSchemas/blob/098583adedf9a811c0170e45d986a57728773628/Services/VoiceVirtualAgent/5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/byova_common.proto#L143) includes the list of virtual agents received as response.

Details of the service are mentioned [here](https://github.com/webex/dataSourceSchemas/tree/main/Services/VoiceVirtualAgent/5397013b-7920-4ffc-807c-e8a3e0a18f43).

#### Process caller input <a name="process-audio-section"></a>
Bidirectional streaming RPC to send and receive caller audio, DTMF or input events.
rpc ProcessCallerInput(stream VoiceVARequest) returns (stream VoiceVAResponse)

**Parameters**

[VoiceVARequest](https://github.com/webex/dataSourceSchemas/blob/098583adedf9a811c0170e45d986a57728773628/Services/VoiceVirtualAgent/5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L15) is the request paramter, which is used to send caller audio/DTMF data/Input event data to Virtual agent application.

[VoiceVAResponse](https://github.com/webex/dataSourceSchemas/blob/098583adedf9a811c0170e45d986a57728773628/Services/VoiceVirtualAgent/5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/voicevirtualagent.proto#L79) is the response received from Virtual agent application based on VoiceVARequest data.
Details of the service are mentioned [here](https://github.com/webex/dataSourceSchemas/tree/main/Services/VoiceVirtualAgent/5397013b-7920-4ffc-807c-e8a3e0a18f43).

## Real Time Media Forking <a name="media-forking-section"></a>

### Services <a name="forking-services-section"></a>

#### Media forking <a name="forking-audio-section"></a>
Bidirectional streaming RPC where the client streams the audio during the call and server sends acknowledgement when onComplete() is received.
rpc StreamConversationAudio(stream ConversationAudioForkingRequest) returns (stream ConversationAudioForkingResponse)

**Parameters**

[ConversationAudioForkingRequest](https://github.com/webex/dataSourceSchemas/blob/098583adedf9a811c0170e45d986a57728773628/Services/AudioForking/523e1b7f-4693-47bc-b84e-a7b7a505fb0b/Proto/conversationaudioforking.proto#L9) is the request parameters which includes the audio bytes along with other additional parameters.

[ConversationAudioForkingResponse](https://github.com/webex/dataSourceSchemas/blob/098583adedf9a811c0170e45d986a57728773628/Services/AudioForking/523e1b7f-4693-47bc-b84e-a7b7a505fb0b/Proto/conversationaudioforking.proto#L47) is the response sent by Server(listening to forked media).

# References <a name="references-section"></a>
**Schema and Proto defintions** : https://github.com/webex/dataSourceSchemas/tree/main/Services

**Sample simulator and code** : https://github.com/CiscoDevNet/webex-contact-center-ai-sample-code/tree/main/provider-api/dialog-connector-simulator

**Proto documentations** : 1. https://github.com/webex/dataSourceSchemas/tree/main/Services/VoiceVirtualAgent/5397013b-7920-4ffc-807c-e8a3e0a18f43/Proto/Docs
                           2. https://github.com/webex/dataSourceSchemas/tree/main/Services/AudioForking/523e1b7f-4693-47bc-b84e-a7b7a505fb0b/Proto/Docs

