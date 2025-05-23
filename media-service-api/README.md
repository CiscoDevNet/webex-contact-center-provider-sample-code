**Note:** *These APIs are not open for public consumption, offer has to be procurred for your org in order to use these services. For any new request to use these APIs please reach out to Cisco on [ccai-connectors@cisco.com](mailto:ccai-connectors@cisco.com) with business use case.*

# Architecture

![Architecture](https://user-images.githubusercontent.com/5176062/115956139-c2be9480-a518-11eb-9aa6-6906b070b00b.png)

1.	A Contact Center Client will use AI for below persona.
    Virtual Agent (VA): This is the persona in which Caller will deal with IVR / Virtual Assistant, which will be capable of Voice / DTMF inputs which will be processed by an NLP engine and provides the response in the form of synthesized audio.
2.	The VA request goes through API Gateway for billing / throttling.
3.	CCAI Orchestrator Platform will based on the request fetch the Config which will have the service app details.(service app includes jws and url of the external connector)
4.	Orchestrator to establish the connection with the jws provided by the service app data source registration.
5.	Orchestrator sends the request to External AI Connector over standard gRPC protobuf spec defined by Orchestrator.
6.	External AI Connector receives the media and translate it into the desired External AI Service format and route it to the request.
7.	The responses received from External AI Services are returned back to Orchestrator by the External AI Connector.

## Virtual Agent Callflow
![VA](https://user-images.githubusercontent.com/5176062/116988874-a37cef80-acee-11eb-9123-bd7fa37373f0.jpg)

# Onboarding
Webex Contact Center Control Hub to be used by the customer to create a configuration.

For providers-
1.	A service app needs to be created(for Voice /Digital virtual agent schema) and should be authorized by customer admin for the usage.
2.	A configuration will need to be created which is represented by a configId(which will be associated with service app) and will define the Services / Features to be used. 
3.	Orchestrator will orchestrate the call to a specific provider and add the necessary feature flags based on the config details.

Note: Detailed documentation for on-boarding can be found on webex developer portal
https://developer.webex-cx.com/documentation/guides/bring-your-own-virtual-agent

API
For detailed description of the API, please refer the protobuf files.
•	VoiceVirtualAgent: API to be called for Virtual Agent request/Response.
Request: The requests will be passed with the generic params defined in the protobuf and Provider specific params will be set in the Map contained in the request in the form of Key / Value.
Response: The response will need to populate the generic params defined in the protobuf in the specific params and Provider specific objects will be passed back to client in the form of object json.
Features: The request will also carry the desired features needed on the incoming request. The response object should return the responses accordingly.
•	Real Time audio forking: API to be called for Real time audio forking use case.
Request: The requests will be passed with the generic params defined in the protobuf.
Response: The response includes status of the forked audio and error messages(if applicable). Right now status field is not used by the client and is only logged.
Features: The request will also carry the desired features needed on the incoming request. The response object should return the responses accordingly.
# Authentication
Orchestrator will use a jws while establishing gRPC connection. This jws is a kwt generated by Cisco's developer platform for the org and signed by cisco's private key.
This is generated at the data source registration phase. This jws is valid upto 24 hours and is configurable.
Providers need to fetch cisco's public key and match it with the key id present in the header of jws. if its matches then the request is from Cisco.
# Serviceability
Each Provider endpoint to expose certain APIs to monitor the health of endpoint.
https://<Service endpoint>/<service Name>/v1/ping
Response:
```sh
{
"serviceName": "<Service Name>",
"serviceType": "REQUIRED",
"serviceState": "online",
"message": "<Service Name> is ONLINE",
"lastUpdated": "2021-01-22T12:24:37.382Z",
}
```
# Glossary
* Service app: Service app created by selecting respective schema on dev portal.
* Data source: Using authorized service app's token data source is registered with Cisco which includes the url at which communication should happen.At the same step jws is alos generated.
* Provider: AI service provider.
* AI Service: Service / Feature offered by a Provider.
