import requests
import uuid

auth_url = "https://idbrokerbts.webex.com/idb/oauth2/v1/access_token"

def refresh_token(refresh_token, client_id, client_secret):
    data = {
            "grant_type": "refresh_token",
            "refresh_token": refresh_token
        }
    auth = (client_id, client_secret)
    response = requests.post(auth_url, data=data, auth=auth)
    response_json = response.json()
    print(response_json)
    if response_json['access_token']:
        print(response_json['access_token'])
        return response_json['access_token']
    raise Exception(f"Error obtaining token: {response}")

def update_datasource(env,data_source_id, access_token,proxy_url = None, client_id=None, client_secret=None):
    payload = {"audience": "audience", "errorMessage": "",
               "schemaId": "5397013b-7920-4ffc-807c-e8a3e0a18f43", "status": "active", "subject": "sub",
               "tokenLifetimeMinutes": 1440,
               "url": proxy_url if proxy_url is not None else f"https://byova-ai-simulator.{env}.ciscoccservice.com",
               'nonce': str(uuid.uuid4())}
    _url = f"https://developer-applications-intb.ciscospark.com/v1/dataSources/{data_source_id}"
    headers = {"Authorization": f"Bearer {access_token}", "Content-Type": "application/json"}
    response = requests.put(_url, headers=headers, json=payload)
    print(f"PUT Status: {response.text}")
    print(f"Response: {response}, Error: {response.text if not response.ok else 'No error'}")
    print(f"Token for {env} and url {payload['url']} = {response.json()['jwsToken']}")
    if True:
        auth = (client_id, client_secret)
        revoke_url = f"https://idbrokerbts.webex.com/idb/oauth2/v1/logout?access_token={access_token}"
        headers = {"Authorization": f"{auth}", "Accept": "application/json"}
        response = requests.get(revoke_url, headers=headers,auth=auth)
        if response.status_code == 200:
            print(f"Token revoked successfully for {env}")
        else:
            print(f"Failed to revoke token for {env}: {response.status_code} - {response.text}")



#dev
#refresh_token("NWFjYTEzNGMtODEyMS00NTUxLThhMzQtZjQwZjMwZGM3MWU5MWMyYmYyNTctODI0_A52D_d7ac6456-c494-4b29-82f2-97a6f4712d16", "C16073ec1ce32ae189d01892b5f21f30ac4c052a680b666199681728df54867ca", "0338c2904d770ebe4740c1c475ed0647090c830f308431c0eda9a75ae1b6bf62")

#int
#refresh_token("M2M3ZWVkMWMtZDZmMC00NWFkLTkzMTktOTFhMDJkY2NmMzZmYWQ1M2NhNTctMGYx_A52D_5a54948f-aeb3-4cb9-884d-f4440be66a6c", "C8c9bde5bdeeea44824500832f121452a0d60de5f81a48cc77c541f4c04533476", "a31c8387d6bd5ca38edabdab3ef903f6568530e483a832592bf11e2f6fcc6499")

#exit(0)

#32
url = "https://poet-flip-consistently-poetry.trycloudflare.com"
if url is None:
    update_datasource("devus1","16ab66d1-04d6-4de2-8b24-52302b1e353a", refresh_token("R2VhMzFmNjUtZTBkNi00Y2VlLWJkOWItOTgyYTRmMjNiMmM2ZjQyYTA2ODQtMzU2_A52D_d7ac6456-c494-4b29-82f2-97a6f4712d16", "C16073ec1ce32ae189d01892b5f21f30ac4c052a680b666199681728df54867ca", "0338c2904d770ebe4740c1c475ed0647090c830f308431c0eda9a75ae1b6bf62"), client_id="C16073ec1ce32ae189d01892b5f21f30ac4c052a680b666199681728df54867ca", client_secret="0338c2904d770ebe4740c1c475ed0647090c830f308431c0eda9a75ae1b6bf62")
else:
    update_datasource("devus1","16ab66d1-04d6-4de2-8b24-52302b1e353a",refresh_token("R2VhMzFmNjUtZTBkNi00Y2VlLWJkOWItOTgyYTRmMjNiMmM2ZjQyYTA2ODQtMzU2_A52D_d7ac6456-c494-4b29-82f2-97a6f4712d16", "C16073ec1ce32ae189d01892b5f21f30ac4c052a680b666199681728df54867ca", "0338c2904d770ebe4740c1c475ed0647090c830f308431c0eda9a75ae1b6bf62"),proxy_url=url, client_id="C16073ec1ce32ae189d01892b5f21f30ac4c052a680b666199681728df54867ca", client_secret="0338c2904d770ebe4740c1c475ed0647090c830f308431c0eda9a75ae1b6bf62")

#exit(0)
#31
update_datasource("intgus1","b2a6797c-1a84-4397-a9d2-1ba946b47453", refresh_token("YmNjNGNhMDQtMzI3Zi00YmMzLTljNDEtMzQ3ZjVmZWFiZTk1YzRiNGFiNjUtMDEw_A52D_d7ac6456-c494-4b29-82f2-97a6f4712d16", "C10e668822345575343f51cecc1129ef841a6e3ed4bc1285f8a47c35f0d445545", "bd1f2cd01ac152dc9db10c070a161fd5750296bb130f2700ee0634bda9ddd376"), client_id="C10e668822345575343f51cecc1129ef841a6e3ed4bc1285f8a47c35f0d445545", client_secret="bd1f2cd01ac152dc9db10c070a161fd5750296bb130f2700ee0634bda9ddd376")
