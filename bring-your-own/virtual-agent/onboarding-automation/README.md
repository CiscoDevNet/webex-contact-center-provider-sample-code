# Automate BYOVA provider onboarding

Use this Python sample to explore the provider-side automation that follows customer authorization of a Bring Your Own Virtual Agent (BYOVA) Contact Center Service App. The sample creates and refreshes a provider OAuth session and maintains Service App lifecycle webhooks. It maps an authorization to the correct customer organization, retrieves customer-specific Service App credentials, and creates or updates that customer's Data Source.

**Important:** This repository is a runnable reference, not a production service. It deliberately keeps state and secrets in process memory, processes webhook work synchronously, and exposes no renewal scheduler. Review [Production hardening required](#production-hardening-required) before adapting it for a customer environment. For BYOVA concepts, runtime transports, and the customer-owned Contact Center configuration, see the [BYOVA overview](../README.md).

## What the sample demonstrates

The sample shows how two separate Webex security principals work together:

1. The provider creates and authorizes one OAuth Integration with `spark:applications_token`, `application:webhooks_write`, and `application:webhooks_read`.
1. Each customer authorizes the provider's Contact Center Service App in Control Hub.
1. Webex sends an `authorized` event to the provider's signed webhook endpoint.
1. The sample reads the customer key from `data.authorizerOrgId` and retrieves a Service App token pair for only that organization.
1. The sample uses that customer's Service App access token to create or update a BYOVA Data Source.
1. The Data Source `id` becomes the **Resource Identifier** that the customer enters in the Virtual Agent feature.

The sample does not authorize the Service App for the customer, create a Virtual Agent feature, modify a Flow Designer flow, or route calls. Those remain actions for customer administrators.

| Credential | Owner | Used by the sample |
| --- | --- | --- |
| Integration client ID and secret | Provider | OAuth code exchange and provider token refresh |
| Integration access token | Provider | List and create lifecycle webhooks and retrieve customer-specific Service App tokens |
| Service App client ID and secret | Provider application | Request and refresh a token pair for one customer organization |
| Customer Service App access token | One authorizing customer organization | List, create, and update that customer's Data Source |
| Webhook secret | Provider | Verify the exact raw webhook request bytes |
| Data Source JSON Web Signature (JWS) | One customer Data Source | Runtime credential returned by Webex; the sample stores it only in process memory |

Inspect the orchestration in [`service.py`](python/src/byova_onboarding/service.py), the Webex API calls in [`webex.py`](python/src/byova_onboarding/webex.py), and the sample state transitions in [`store.py`](python/src/byova_onboarding/store.py).

## Before you run the sample

Prepare the following prerequisites:

- Python 3.10 or later.
- A provider-owned Webex organization and access to create an Integration and a Contact Center Service App.
- A provider OAuth Integration registered under the same Developer Portal developer that created the Contact Center Service App. In **My Webex Apps**, select **Create a New App** > **Create an Integration**, register the exact `INTEGRATION_REDIRECT_URI`, select the three required Integration scopes, and copy the one-time-displayed client secret directly into protected server-side storage.
- A Contact Center sandbox or test organization in which the Service App can be authorized.
- A Service App configured for the BYOVA schema and the gateway domain that you will register in the Data Source URL.
- A public HTTPS URL that forwards to the sample's `/webhooks/service-app` route for live webhook delivery.
- A public HTTPS callback URL that forwards to `/oauth/callback` and exactly matches the redirect URI registered on the Integration.

Configure the provider OAuth Integration with exactly these scopes for this workflow:

```text
spark:applications_token
application:webhooks_write
application:webhooks_read
```

Configure the Contact Center Service App separately with:

```text
spark-admin:datasource_read
spark-admin:datasource_write
```

Do not put the Data Source scopes on the provider Integration, and do not use the Integration access token for `/datasources` calls. The Integration token retrieves customer-specific Service App tokens and manages the webhooks. The customer-specific Service App token manages that customer's Data Source.

The sample validates that the OAuth redirect URI, webhook target, Data Source URL, and Webex API base URL are absolute HTTPS URLs. It also restricts `BYOVA_TOKEN_LIFE_MINUTES` to the supported range of 1 through 1440. Review these checks in [`config.py`](python/src/byova_onboarding/config.py).

## Install and configure

From the repository root, install the Python package and its development dependencies:

```bash
cd bring-your-own/virtual-agent/onboarding-automation/python
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -e '.[dev]'
cp .env.example .env
```

Open `.env` and replace every placeholder. The supplied [`.env.example`](python/.env.example) defines these settings:

| Variable | Purpose |
| --- | --- |
| `WEBEX_API_BASE_URL` | Webex REST base URL; keep the production default unless you are running an approved test endpoint |
| `INTEGRATION_CLIENT_ID` | Provider OAuth Integration client ID |
| `INTEGRATION_CLIENT_SECRET` | Provider OAuth Integration secret |
| `INTEGRATION_REDIRECT_URI` | Exact HTTPS callback URI registered on the Integration |
| `SERVICE_APP_ID` | Service App `id` used in webhook filters and the application-token path |
| `SERVICE_APP_CLIENT_ID` | Service App OAuth `clientId`, also returned as Data Source `applicationId` |
| `SERVICE_APP_CLIENT_SECRET` | Service App secret used for customer token retrieval and refresh |
| `WEBHOOK_TARGET_URL` | Public HTTPS URL for `/webhooks/service-app` |
| `WEBHOOK_SECRET` | Long, random shared secret used to verify `X-Spark-Signature` |
| `BYOVA_SCHEMA_ID` | Service App-approved BYOVA Data Source schema |
| `BYOVA_DATA_SOURCE_URL` | Provider gateway URL under a Service App-approved domain |
| `BYOVA_AUDIENCE` and `BYOVA_SUBJECT` | Expected audience and subject for the Data Source credential |
| `BYOVA_TOKEN_LIFE_MINUTES` | Requested Data Source credential lifetime, from 1 through 1440 minutes |

Optionally, set `OAUTH_STATE_TTL_SECONDS`; the default is 600 seconds. **Warning:** Never commit `.env`. Use a secret manager rather than an environment file outside local development.

Load the values and start the FastAPI application factory:

```bash
set -a
source .env
set +a
uvicorn byova_onboarding.app:create_app \
  --factory \
  --host 127.0.0.1 \
  --port 8000
```

In another terminal, verify the local liveness route:

```bash
curl --fail http://127.0.0.1:8000/healthz
```

**Expected result:** The response is `{"status":"ok"}`. This route proves that the process is running; it does not test Webex authentication, storage, webhook reachability, or Data Source readiness.

| Route | Purpose | Important sample behavior |
| --- | --- | --- |
| `GET /healthz` | Local liveness check | Returns `status: ok`; it is not a dependency-readiness check |
| `GET /oauth/start` | Begin provider OAuth | Issues expiring, single-use state and redirects the provider user to Webex |
| `GET /oauth/callback` | Finish provider OAuth | Exchanges the code, stores Integration tokens, and creates missing lifecycle webhooks |
| `POST /webhooks/service-app` | Process customer lifecycle | Verifies the raw-body HMAC, maps `data.authorizerOrgId`, and currently performs downstream Webex calls synchronously |

## Authorize the provider Integration and create webhooks

Open `http://127.0.0.1:8000/oauth/start` in a browser after the callback URL is reachable through the registered HTTPS address. Authorize the Integration with the provider account that owns and operates the applications. Customers do not authorize this Integration.

The implementation in [`app.py`](python/src/byova_onboarding/app.py), [`service.py`](python/src/byova_onboarding/service.py), and [`webex.py`](python/src/byova_onboarding/webex.py) performs the following sequence:

1. `GET /oauth/start` asks `InMemoryStore` for a cryptographically unpredictable state value.
1. The store keeps the state for ten minutes by default.
1. The route redirects the browser to `/v1/authorize` with the exact three Integration scopes.
1. Webex redirects to the exact registered `INTEGRATION_REDIRECT_URI` with `code` and `state`.
1. `GET /oauth/callback` consumes the state before exchanging the one-time code. A consumed, missing, or expired state cannot be reused in that process.
1. The sample stores the returned Integration access token, refresh token, and calculated expiries in process memory.
1. The sample lists current webhooks, then creates any missing exact active match for `serviceApp` `authorized` and `serviceApp` `deauthorized` with `filter: id=SERVICE_APP_ID`.

**Expected result:** A successful callback returns a response like:

```json
{
  "status": "ready",
  "webhooksCreated": 2
}
```

`webhooksCreated` can be 0, 1, or 2 because the sample does not duplicate an active exact match. This behavior is intentionally narrow: the current list request has no pagination, and the sample does not update or repair an existing disabled or drifted subscription. It treats the required exact active record as missing and creates a new one.

Because Integration tokens and OAuth state live in [`InMemoryStore`](python/src/byova_onboarding/store.py), restarting the process loses them. Repeat the provider authorization after a local restart.

## Receive and verify lifecycle webhooks

Webex sends customer authorization and deauthorization events to `WEBHOOK_TARGET_URL`. The route reads `await request.body()` into bytes and verifies the `X-Spark-Signature` compatibility header before calling `json.loads`.

The exact implementation lives in [`signature.py`](python/src/byova_onboarding/signature.py):

```python
expected = hmac.new(secret.encode("utf-8"), raw_body, hashlib.sha1).hexdigest()
return hmac.compare_digest(expected, signature.lower())
```

The helper also requires a nonempty body and secret and exactly 40 hexadecimal signature characters. The digest covers the unmodified raw request bytes. Parsing and reserializing logically equivalent JSON changes those bytes and must fail verification. This sample implements only the documented `X-Spark-Signature` HMAC-SHA1 compatibility path; it does not implement or infer an `X-Webex-Signature` parser.

After signature verification, `_parse_lifecycle_event` in [`app.py`](python/src/byova_onboarding/app.py) requires:

- `resource` to equal `serviceApp`.
- `event` to equal `authorized` or `deauthorized`.
- `data.id` to equal `SERVICE_APP_ID`.
- `data.authorizerOrgId` to contain the customer organization ID.
- `data.authorizationDate` to contain a timezone-aware timestamp.

Ignore the top-level `orgId` when mapping the customer. That envelope field identifies the organization that owns the webhook. Use `data.authorizerOrgId` as both the sample's customer key and the `targetOrgId` in the application-token request.

The parser constructs a logical event key by hashing the Service App ID, customer organization ID, event name, and authorization date with SHA-256. Within one running process, the store uses that key to recognize a repeated completed event. The route logs only the non-secret event type, provider-generated onboarding record ID, and outcome. It does not log the customer organization ID, raw body, tokens, JWS, or authorization headers.

The route returns `401` for an invalid signature, `400` for an invalid event contract, and `502` when the demonstrated downstream Webex call raises `WebexAPIError`. On success it returns `202` with `active`, `unchanged`, `fenced`, or `inactive` status, depending on the lifecycle result.

**Important:** Sample boundary: The route does not acknowledge immediately after durable acceptance. It waits for token retrieval and Data Source reconciliation and only then returns `202`. Move this work to an atomic durable queue before production.

## Provision one customer

For a valid `authorized` event, `OnboardingService.handle_lifecycle_event` serializes work by `(SERVICE_APP_ID, customer organization ID)` with an in-process `asyncio.Lock`. The store creates or advances a generation unless the logical event is a duplicate, is older than current state, or would replay an authorization generation that was already revoked.

The Webex API sequence is:

1. `WebexClient.fetch_customer_service_app_tokens` gets a current provider Integration bearer. It refreshes that Integration token lazily if it is near expiry.
1. It sends `POST /applications/{SERVICE_APP_ID}/token` with the Integration bearer and the Service App client ID, Service App client secret, and `targetOrgId` from `data.authorizerOrgId`.
1. It converts the response into a customer-specific `OAuthTokens` record.
1. It calls `GET /datasources` with the customer-specific Service App access token.
1. It compares returned records with the configured `BYOVA_DATA_SOURCE_URL`.
1. It rejects the route if any URL match has an `applicationId` different from the configured Service App client ID, and rejects an ambiguous result when several records use the route.
1. If no record matches, it sends `POST /datasources` with the approved schema, configured URL, audience, subject, a fresh nonce, and token lifetime.
1. If exactly one acceptable route matches, it sends `PUT /datasources/{DATA_SOURCE_ID}` with the complete configured payload and `status: active`.
1. It requires a response ID. When the response supplies status, URL, application ID, or schema, the sample requires active status and the expected route, Service App, and schema.

The customer-specific Service App token, not the Integration token, authenticates all Data Source requests. Inspect the request construction and validation in [`webex.py`](python/src/byova_onboarding/webex.py).

The response checks are useful defenses, but they are not a full production identity proof. The sample tolerates omitted optional response fields, does not validate a returned `orgId`, and initially selects candidates by exact URL. Before production, reconcile the complete intended identity across customer organization, Service App, schema, route, and status.

After a successful Webex response, `complete_authorization` commits only if the tenant remains in the same provisioning generation. The process-local record contains the Data Source ID, status, JWS, expiry, nonce, and customer token pair. A repeated successfully processed event returns `unchanged` without repeating the token and Data Source API sequence.

Treat the returned Data Source ID as the customer's **Resource Identifier**. Do not treat successful creation as call-routing readiness. The customer must still bind the identifier to a Virtual Agent feature, publish the Flow Designer flow, and test the call path.

**Important:** Sample boundary: `InMemoryStore` retains customer OAuth tokens and the Data Source JWS as Python objects. A production store must encrypt these secrets and keep secret-manager references in ordinary tenant state.

## Renew OAuth and Data Source credentials

OAuth credentials and the Data Source JWS expire independently. The sample implements both renewal code paths but does not schedule them.

`WebexClient.integration_access_token` checks the stored Integration access-token expiry whenever another operation needs a provider bearer. Within a 60-second default margin, it uses the Integration refresh token, stores the replacement token pair, and retains the previous refresh token if Webex omits a replacement.

For a customer record, `OnboardingService.renew_customer`:

1. Acquires the process-local tenant lock and requires an active record with a refresh token and Data Source ID.
1. Refreshes the customer Service App token through `/access_token` with the Service App client ID and secret.
1. Sends `PUT /datasources/{DATA_SOURCE_ID}` with the refreshed customer bearer, a new nonce, the complete configured payload, and `status: active`.
1. Validates the response and commits the replacement token and JWS only if the tenant generation is still active.

No HTTP route, timer, job runner, or background scheduler calls `renew_customer` automatically. Invoke it directly only while experimenting with the service object or tests. Before production, add independent schedules based on returned OAuth expiries and Data Source `tokenExpiryTime`, with safety margins, retry budgets, replacement refresh-token storage, alerting, and operator recovery.

## Handle deauthorization safely

For a valid `deauthorized` event, the service deliberately bypasses the provisioning lock. This allows a newer deauthorization to advance the tenant generation even when an older authorization job is still waiting on Webex.

The sample then:

1. Marks the process-local customer record `inactive`.
1. Records the logical event key and authorization timestamp.
1. Erases the in-memory customer Service App token pair, Data Source JWS, and nonce.
1. Returns the provider-generated onboarding record ID.

If the older authorization later tries to commit, `complete_authorization` sees the changed generation or status and returns `False`; it cannot restore the erased credentials. A repeated deauthorization with the same logical event key is idempotent and returns the existing record ID.

The sample does not call a real provider gateway or disable a tenant route. It also does not delete or modify the customer's Virtual Agent feature or Flow Designer flow. Add an explicit, idempotent gateway-route disable hook and an approved non-secret audit record before production. Tell the customer to remove or redirect its customer-owned configuration when service ends.

## Run the automated tests

After installing the development dependencies, run the suite from the Python sample directory:

```bash
python -m pytest
```

The current suite contains 14 tests. A successful run reports `14 passed`. The tests cover:

- Exact Integration scopes and OAuth state in the authorization URL.
- Expiring, single-use OAuth state behavior.
- The Integration bearer and `data.authorizerOrgId` mapping in the application-token request.
- Separate customer Service App token refresh.
- Data Source create request shape.
- Data Source renewal with the complete update payload and `status: active`.
- Reconciliation of an existing Data Source whose `applicationId` matches the Service App client ID.
- Rejection of a configured route already owned by a different Service App.
- Creation of only a missing exact-match lifecycle webhook.
- Exact-raw-byte HMAC verification.
- Duplicate authorization behavior and customer mapping.
- Rejection of an invalid signature or wrong Service App.
- Deauthorization secret erasure and generation fencing.
- Customer OAuth refresh followed by Data Source renewal.

The tests use `httpx.MockTransport` and `ASGITransport`. They prove local request construction, validation, and state transitions; they do not call live Webex services, expose a public HTTPS webhook, authorize a real customer organization, or exercise Contact Center call routing.

Optionally run the configured static checks:

```bash
python -m ruff check .
```

## Troubleshooting

Do not print tokens, secrets, authorization headers, raw webhook bodies, or raw customer organization IDs while diagnosing these failures. Use provider-generated onboarding record IDs, non-secret event names, HTTP status, and Webex tracking IDs where available.

| Symptom | Safe check | Recovery |
| --- | --- | --- |
| The application fails during startup | Read the configuration error and confirm that every required variable is set. Check that `INTEGRATION_REDIRECT_URI`, `WEBHOOK_TARGET_URL`, `BYOVA_DATA_SOURCE_URL`, and `WEBEX_API_BASE_URL` are absolute HTTPS URLs and that the token lifetime is from 1 through 1440 | Correct `.env`, reload it into the shell, and restart Uvicorn. Do not paste secret values into logs or issue reports |
| `/oauth/callback` returns `400` for OAuth state | Check whether more than the configured state TTL elapsed, the callback was opened twice, or the process restarted after `/oauth/start` | Begin again at `/oauth/start` and complete the callback once within the TTL. In production, store state durably and bind it to the initiating session |
| The webhook returns `401` | Confirm that Webex and the sample use the same `WEBHOOK_SECRET`, that the request reaches the route without a proxy changing its body, and that verification runs on the original bytes | Correct the secret or proxy behavior, then deliver a newly signed request. Do not parse, normalize, or reserialize JSON before verification |
| The webhook returns `400` | Check the sanitized error for the rejected field: `resource`, `event`, `data.id`, `data.authorizerOrgId`, or `data.authorizationDate` | Send a `serviceApp` `authorized` or `deauthorized` event for the configured Service App. Provide a nonempty customer organization ID and a timezone-aware authorization timestamp |
| The webhook returns `502` | Use the returned Webex tracking ID, if present, and check provider token state, customer authorization, scopes, approved schema, approved gateway domain, and Data Source input without exposing credentials | Correct a configuration or authorization problem before retrying. For transient timeout, `429`, or `5xx` failures, add bounded retry handling in the production worker |
| Provisioning reports that the route belongs to another Service App or that several Data Sources use it | List the customer's Data Sources with an authorized Service App token and compare non-secret IDs, application ownership, and URLs | Do not overwrite or create around the conflict. Assign a unique approved route or resolve the conflicting records through an operator-reviewed process |
| Integration, customer OAuth, or Data Source credentials expire instead of renewing | Confirm that no external process invokes `renew_customer`; the sample intentionally provides no scheduler | Add independent production schedules based on returned OAuth expiries and `tokenExpiryTime`, then alert before the retry margin is exhausted |

If a process restart caused the problem, `InMemoryStore` has lost OAuth state, Integration tokens, customer records, idempotency keys, and locks. Reauthorize only for local recovery. Replace the store before relying on restart recovery in a shared environment.

## Production hardening required

Keep the following distinction explicit when you adapt the sample:

| Demonstrated in this sample | Required before production |
| --- | --- |
| Process-local `InMemoryStore` and `asyncio.Lock` instances | Durable encrypted storage, distributed locks, and atomic state transitions across workers |
| Synchronous webhook provisioning | Durable queue acceptance followed by asynchronous token and Data Source work |
| Event deduplication within one process | Durable idempotency across deliveries, restarts, regions, and worker processes |
| Exact-URL matching, a foreign-Service-App guard, and response app/schema/route checks when fields are returned | Full customer-org, Service App, schema, route, status, and Data Source identity reconciliation |
| Creation of missing exact active webhooks | Paginated listing plus explicit repair, update, disabled-subscription recovery, and missed-event reconciliation |
| Callable customer renewal method | Independent OAuth and JWS schedules, safety margins, retries, alerts, and operator recovery |
| In-memory inactive state on deauthorization | Immediate real gateway-route disablement, durable fencing, and audited secret erasure |
| `GET /healthz` process liveness | Separate liveness and readiness checks covering required storage and dependencies |

At minimum, complete these engineering tasks:

1. Replace [`InMemoryStore`](python/src/byova_onboarding/store.py) with durable encrypted storage. Store Integration and customer OAuth tokens, Service App secrets, webhook secrets, and JWS values in a production secret manager. Keep only protected references in ordinary records.
1. Replace process-local tenant locks with distributed concurrency control. Preserve the generation fence so stale authorization or renewal work cannot commit after deauthorization.
1. Persist the verified lifecycle event and queue work atomically before returning a success response. Keep customer token retrieval and Data Source calls outside the webhook request.
1. Add bounded exponential backoff and jitter for timeouts, `429`, and `5xx` responses; honor `Retry-After`. Do not retry unchanged invalid scope, schema, domain, route, or credential input.
1. Add dead-letter or operator-review state with deliberate replay from current Webex state rather than from a stale raw payload.
1. Add periodic authorization, webhook, credential-expiry, Data Source, and provider-route reconciliation. Paginate API list calls and repair disabled or drifted webhook records.
1. Match and validate the full Data Source identity. Keep the current foreign-Service-App guard, complete PUT payload, and response checks, then also prove the customer organization and reject missing identity fields when your production contract requires them.
1. Schedule Integration OAuth refresh, customer Service App OAuth refresh, and Data Source renewal from their independent returned expiries. Renew the Data Source before `tokenExpiryTime` with a fresh nonce.
1. Implement a real provider gateway-route enable and disable boundary. A database status change in this sample does not prevent runtime traffic in your gateway.
1. Run behind production TLS, define readiness separately from liveness, and apply request size, timeout, and shutdown-drain controls. Preserve the sample's FastAPI lifespan cleanup for its internally owned HTTP client and extend lifecycle-managed cleanup to any additional production clients, connection pools, queues, or workers. Monitor webhook health, renewal lag, API failures, retry exhaustion, and offboarding completion.

**Warning:** Never log customer organization IDs, raw webhook bodies, client secrets, access or refresh tokens, JWS values, or authorization headers. Use provider-generated correlation and onboarding record IDs in logs. Restrict secret access by tenant and audit access independently from general application logs.

## Official Webex references

The sample's request shapes and security boundaries are based on these Webex references:

- [Contact Center Service Apps](https://developer.webex.com/webex-contact-center/docs/contact-center-service-apps) for customer authorization and Service App token lifecycle.
- [Service Apps API](https://developer.webex.com/admin/docs/api/v1/service-apps) for the Integration-authenticated application-token endpoint and `spark:applications_token` scope.
- [Contact Center Integration scopes](https://developer.webex.com/webex-contact-center/docs/integration-scopes) for the two Service App webhook scopes and two Data Source scopes used here.
- [Service App authorization webhooks](https://developer.webex.com/blog/webhooks-for-service-app-authorizations) for the `serviceApp` events, filters, and `data.authorizerOrgId` payload field.
- [Bring Your Own Data Source](https://developer.webex.com/webex-contact-center/docs/bring-your-own-data-source-cc) and the [Data Sources API](https://developer.webex.com/webex-contact-center/docs/api/v1/data-sources) for Data Source ownership, request fields, credential renewal, and update behavior.
- [Webhooks authentication](https://developer.webex.com/meeting/docs/api/guides/webhooks#Authenticating-Requests) for raw-body `X-Spark-Signature` verification.

## Validate with a sandbox

Use a Contact Center sandbox to verify the complete lifecycle after the local test suite passes:

1. Run the service behind a test HTTPS endpoint and register the exact OAuth callback and webhook target URLs.
1. Start `/oauth/start`, authorize the provider Integration once, and confirm that both lifecycle subscriptions exist with the exact Service App ID filter.
1. Authorize the Service App in a test customer organization.
1. Confirm that the sample accepts the signed `authorized` event, maps `data.authorizerOrgId`, retrieves the customer-specific token pair, and creates exactly one active Data Source.
1. Confirm that the Data Source application, schema, route, and customer organization match the intended tenant before handing off its ID.
1. Deliver the same authorization event again and confirm that the sample returns `unchanged` without another Webex API sequence.
1. Invoke the renewal path and confirm that customer OAuth refresh precedes a Data Source PUT with a fresh nonce and `status: active`.
1. Send deauthorization while older authorization work is pending and confirm that generation fencing prevents stale completion and that in-memory secrets are erased.
1. Verify your production extension separately disables the actual customer route; the supplied sample has no gateway hook.
1. Have the customer enter the Data Source ID as the Resource Identifier, publish the intended Flow Designer flow, and run an end-to-end call through the selected [gRPC](../grpc-interface/README.md) or [WebSocket](../web-socket-interface/README.md) implementation.

Passing the 14 local tests proves the sample's mocked contracts. Creating a Data Source proves provider-side control-plane provisioning. Neither result proves public webhook delivery, runtime JWS validation, gateway readiness, customer flow configuration, or end-to-end call routing. Treat the published customer call as the final acceptance test.
