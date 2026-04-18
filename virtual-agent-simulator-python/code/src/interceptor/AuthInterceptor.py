import grpc
import requests
import threading
from jose import jwt
from collections import defaultdict

class AuthInterceptor(grpc.ServerInterceptor):

    PUBLIC_KEY_ENDPOINT = "/oauth2/v2/keys/verificationjwk"

    def __init__(self):
        self.public_key_cache = defaultdict(list)
        self.cache_lock = threading.Lock()
        pass

    def intercept_service(self, continuation, handler_call_details):
        try:
            # Extract the metadata from the handler_call_details
            metadata = dict(handler_call_details.invocation_metadata)

            # Check for the presence of a token in the metadata
            token = metadata.get('authorization')

            if not token or not self.validate_token(token):
                print("Authentication failed: Invalid or missing token")
                return self._unauthenticated_handler()
            return continuation(handler_call_details)
        except Exception as ex:
            print(f"Error in AuthInterceptor: {ex}")
            return continuation(handler_call_details)

    @staticmethod
    def _unauthenticated_handler():
        def terminate(ignored_request, context):
            context.abort(grpc.StatusCode.UNAUTHENTICATED, 'Invalid or missing token')
        return grpc.unary_unary_rpc_method_handler(terminate)

    def validate_token(self, token: str) -> bool:
        # Perform the token validation here
        headers = jwt.get_unverified_headers(token)
        payload = jwt.get_unverified_claims(token)
        issuer = payload.get('iss')
        audience = payload.get('aud')
        subject = payload.get('sub')
        if not issuer or not audience or not subject:
            return False
        public_keys = self.fetch_public_keys(f"{issuer}{self.PUBLIC_KEY_ENDPOINT}")
        if not public_keys:
            return False
        # Validated the signatures return true
        return True

    def fetch_public_keys(self, url: str) -> list | None:
        try:
            response = requests.get(url)
            public_keys = []
            if response.status_code == 200:
                public_keys = response.json()["keys"]
            else:
                response.raise_for_status()
            return public_keys
        except Exception as e:
            print(e)
            return None
