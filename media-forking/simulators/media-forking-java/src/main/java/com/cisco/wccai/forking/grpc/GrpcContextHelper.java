package com.cisco.wccai.forking.grpc;

import io.grpc.Context;
import io.grpc.Metadata;
import java.util.UUID;

/**
 * Helpers for propagating an "rpc-id" correlation identifier across a streaming gRPC call.
 *
 * <p>The id is extracted from the {@code x-rpc-id} metadata header on inbound calls (or generated
 * when absent) and stashed in the gRPC {@link Context} so that downstream code on the same call
 * thread can retrieve it via {@link #getCurrentRpcId()}.
 */
public final class GrpcContextHelper {

    public static final String RPC_ID_HEADER_NAME = "x-rpc-id";
    public static final Metadata.Key<String> RPC_ID_KEY =
            Metadata.Key.of(RPC_ID_HEADER_NAME, Metadata.ASCII_STRING_MARSHALLER);

    private static final Context.Key<String> RPC_ID_CONTEXT_KEY = Context.key("rpcId");

    private GrpcContextHelper() {
    }

    /** Returns the rpc id attached to the current {@link Context}, or {@code null} if absent. */
    public static String getCurrentRpcId() {
        return RPC_ID_CONTEXT_KEY.get();
    }

    /** Returns a derived {@link Context} that carries the supplied rpc id. */
    public static Context withRpcId(String rpcId) {
        return Context.current().withValue(RPC_ID_CONTEXT_KEY, rpcId);
    }

    /** Returns the rpc id carried on the supplied metadata, generating a new one if absent. */
    public static String extractOrGenerateRpcId(Metadata metadata) {
        String rpcId = metadata.get(RPC_ID_KEY);
        if (rpcId == null || rpcId.isBlank()) {
            rpcId = "RPC-ID-" + UUID.randomUUID();
        }
        return rpcId;
    }
}
