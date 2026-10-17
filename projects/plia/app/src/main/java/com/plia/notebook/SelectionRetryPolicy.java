package com.plia.notebook;

/** A manual retry never overrides an account, entitlement, refusal or HTTP limit failure. */
public final class SelectionRetryPolicy {
    private SelectionRetryPolicy() {}

    public static boolean allows(Throwable error, AiDiagnostic diagnostic) {
        if (diagnostic == null || diagnostic.httpStatus != 200
                || diagnostic.stage != AiDiagnostic.Stage.PARSE_RESPONSE
                || !(error instanceof SiwcInference.ProtocolException)) return false;
        switch (((SiwcInference.ProtocolException) error).code) {
            case "empty_response":
            case "interrupted_stream":
            case "incomplete_response":
            case "invalid_stream":
            case "invalid_protocol":
            case "invalid_completion": return true;
            default: return false;
        }
    }
}
