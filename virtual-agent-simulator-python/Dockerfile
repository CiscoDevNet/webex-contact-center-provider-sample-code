FROM python:3.10-alpine

RUN mkdir -p /app
COPY . /app
WORKDIR /app/code

RUN pip install --no-cache-dir -r requirements.txt

# Fetch proto schemas from GitHub
RUN python fetch_proto_schema.py

# Generate gRPC code from proto files
WORKDIR /app/code/src
RUN python -m grpc_tools.protoc -I./proto --python_out=./proto --grpc_python_out=./proto ./proto/*.proto

WORKDIR /app/code/src/server

EXPOSE 8086

CMD ["python", "-u", "AIAgentServer.py"]
