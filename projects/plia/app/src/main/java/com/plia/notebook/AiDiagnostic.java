package com.plia.notebook;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.CancellationException;
import javax.net.ssl.SSLException;

/** Fixed metadata only. Never inspect throwable text, causes, bodies, URLs or study content. */
public final class AiDiagnostic {
    public enum Stage { READ_ACCOUNT, REFRESH_ACCOUNT, BUILD_REQUEST, CONNECT, SEND_REQUEST, READ_RESPONSE,
        PARSE_RESPONSE, APPLY_REPLY, COMPLETE }
    public enum Code { IN_PROGRESS, COMPLETED, CANCELLED, DNS, CONNECT, TIMEOUT, TLS, IO,
        SECURITY, HTTP_ERROR, INVALID_RESPONSE, PROTOCOL_REJECTED, OUT_OF_MEMORY, UNEXPECTED }

    public final Stage stage;
    public final Code code;
    public final int httpStatus;
    public final long elapsedMs;
    public final ResponseMetadata.ContentType contentType;
    public final ResponseMetadata.BodyShape bodyShape;

    private AiDiagnostic(Stage stage, Code code, int httpStatus, long elapsedMs) {
        this(stage, code, httpStatus, elapsedMs, ResponseMetadata.ContentType.NOT_OBSERVED,
                ResponseMetadata.BodyShape.NOT_INSPECTED);
    }

    private AiDiagnostic(Stage stage, Code code, int httpStatus, long elapsedMs,
                         ResponseMetadata.ContentType contentType, ResponseMetadata.BodyShape bodyShape) {
        this.stage = stage == null ? Stage.READ_ACCOUNT : stage;
        this.code = code == null ? Code.UNEXPECTED : code;
        this.httpStatus = boundedStatus(httpStatus);
        this.elapsedMs = Math.max(0, elapsedMs);
        this.contentType = contentType == null ? ResponseMetadata.ContentType.NOT_OBSERVED : contentType;
        this.bodyShape = bodyShape == null ? ResponseMetadata.BodyShape.NOT_INSPECTED : bodyShape;
    }

    /** Retain only closed categories, never raw headers, response bytes or exception text. */
    public AiDiagnostic withResponseMetadata(ResponseMetadata.ContentType contentType, ResponseMetadata.BodyShape bodyShape) {
        return new AiDiagnostic(stage, code, httpStatus, elapsedMs, contentType, bodyShape);
    }

    public static final class HttpFailure extends IOException {
        public final int status;
        public HttpFailure(int status) { super("AI HTTP failure."); this.status = status; }
    }

    public static final class InvalidResponse extends IOException {
        public InvalidResponse() { super("AI response was invalid."); }
    }

    public static AiDiagnostic progress(Stage stage, long elapsedMs) {
        return progress(stage, elapsedMs, 0);
    }

    public static AiDiagnostic progress(Stage stage, long elapsedMs, int knownHttpStatus) {
        return new AiDiagnostic(stage, Code.IN_PROGRESS, knownHttpStatus, elapsedMs);
    }

    public static AiDiagnostic completed(long elapsedMs) {
        return completed(elapsedMs, 0);
    }

    public static AiDiagnostic completed(long elapsedMs, int knownHttpStatus) {
        return new AiDiagnostic(Stage.COMPLETE, Code.COMPLETED, knownHttpStatus, elapsedMs);
    }

    /** Unknown enum names are discarded; numeric fields are normalized before rendering. */
    public static AiDiagnostic restore(String stage, String code, int httpStatus, long elapsedMs) {
        try { return new AiDiagnostic(Stage.valueOf(stage), Code.valueOf(code), httpStatus, elapsedMs); }
        catch (IllegalArgumentException | NullPointerException invalid) { return null; }
    }

    public static AiDiagnostic restore(String stage, String code, int httpStatus, long elapsedMs,
                                       String contentType, String bodyShape) {
        try {
            return new AiDiagnostic(Stage.valueOf(stage), Code.valueOf(code), httpStatus, elapsedMs,
                    ResponseMetadata.ContentType.valueOf(contentType), ResponseMetadata.BodyShape.valueOf(bodyShape));
        } catch (IllegalArgumentException | NullPointerException invalid) { return null; }
    }

    public static AiDiagnostic classify(Stage stage, Throwable error, long elapsedMs) {
        return classify(stage, error, elapsedMs, 0);
    }

    /** knownHttpStatus belongs to this request only and may describe a successful HTTP response. */
    public static AiDiagnostic classify(Stage stage, Throwable error, long elapsedMs, int knownHttpStatus) {
        Code code;
        int status = boundedStatus(knownHttpStatus);
        if (error instanceof HttpFailure) {
            code = Code.HTTP_ERROR;
            status = ((HttpFailure)error).status;
        } else if (error instanceof AuthDiagnostic.HttpFailure) {
            code = Code.HTTP_ERROR;
            status = ((AuthDiagnostic.HttpFailure)error).status;
        } else if (error instanceof InvalidResponse) code = Code.INVALID_RESPONSE;
        else if (error instanceof SiwcInference.ProtocolException) {
            code = Code.PROTOCOL_REJECTED;
            if (status == 0) status = ((SiwcInference.ProtocolException)error).httpStatus;
        } else if (error instanceof SiwcProtocol.ProtocolException) code = Code.PROTOCOL_REJECTED;
        else if (error instanceof AuthDiagnostic.Failure) code = authFailureCode(((AuthDiagnostic.Failure)error).code);
        else if (error instanceof CancellationException || error instanceof InterruptedException) code = Code.CANCELLED;
        else if (error instanceof UnknownHostException) code = Code.DNS;
        else if (error instanceof ConnectException) code = Code.CONNECT;
        else if (error instanceof SSLException) code = Code.TLS;
        else if (error instanceof SocketTimeoutException || error instanceof InterruptedIOException) code = Code.TIMEOUT;
        else if (error instanceof SecurityException) code = Code.SECURITY;
        else if (error instanceof OutOfMemoryError) code = Code.OUT_OF_MEMORY;
        else if (error instanceof IOException) code = Code.IO;
        else code = Code.UNEXPECTED;
        return new AiDiagnostic(stage, code, status, elapsedMs);
    }

    private static Code authFailureCode(AuthDiagnostic.Code code) {
        if (code == null) return Code.IO;
        switch (code) {
            case CANCELLED: return Code.CANCELLED;
            case DNS: return Code.DNS;
            case CONNECT: return Code.CONNECT;
            case TIMEOUT:
            case CALLBACK_TIMEOUT: return Code.TIMEOUT;
            case TLS: return Code.TLS;
            case HTTP_ERROR: return Code.HTTP_ERROR;
            case INVALID_RESPONSE: return Code.INVALID_RESPONSE;
            case PERMISSION_MISSING: return Code.SECURITY;
            case INVALID_INPUT:
            case CALLBACK_REJECTED:
            case OAUTH_ERROR:
            case ATTEMPT_EXPIRED:
            case UNKNOWN_KEY:
            case TOKEN_REJECTED: return Code.PROTOCOL_REJECTED;
            case UNEXPECTED: return Code.UNEXPECTED;
            default: return Code.IO;
        }
    }

    private static int boundedStatus(int status) { return status >= 100 && status <= 599 ? status : 0; }

    public String logLine() {
        return "stage=" + stage.name() + " code=" + code.name()
                + (httpStatus == 0 ? "" : " http_status=" + httpStatus) + " elapsed_ms=" + elapsedMs
                + (contentType == ResponseMetadata.ContentType.NOT_OBSERVED ? "" : " content_type=" + contentType.name())
                + (bodyShape == ResponseMetadata.BodyShape.NOT_INSPECTED ? "" : " body_shape=" + bodyShape.name());
    }

    public String userMessage() {
        String result;
        switch (code) {
            case IN_PROGRESS: result = "No completion recorded yet."; break;
            case COMPLETED: result = "The AI reply was applied to the page."; break;
            case CANCELLED: result = "The AI request was cancelled."; break;
            case DNS: result = "The AI service address could not be resolved. Check the network connection."; break;
            case CONNECT: result = "The connection to the AI service could not be opened."; break;
            case TIMEOUT: result = "The AI connection timed out."; break;
            case TLS: result = "The secure AI connection could not be established."; break;
            case SECURITY: result = "A required permission or security check prevented the AI request."; break;
            case HTTP_ERROR: result = "The AI service returned an unsuccessful HTTP status."; break;
            case INVALID_RESPONSE: result = "The AI response was incomplete or invalid."; break;
            case PROTOCOL_REJECTED: result = "The AI request or response did not pass protocol validation."; break;
            case OUT_OF_MEMORY: result = "The device ran out of memory while handling the AI request."; break;
            case IO: result = stage == Stage.READ_ACCOUNT ? "Saved connection data could not be read."
                    : stage == Stage.APPLY_REPLY ? "The AI reply could not be applied to the page."
                    : "The AI operation could not complete."; break;
            default: result = "The AI operation stopped unexpectedly.";
        }
        return "Last recorded AI event\n" + stageLabel() + "\n" + result
                + "\nDiagnostic: " + stage.name() + " / " + code.name()
                + (httpStatus == 0 ? "" : " / HTTP " + httpStatus)
                + "\nElapsed: " + elapsedMs + " ms"
                + (contentType == ResponseMetadata.ContentType.NOT_OBSERVED && bodyShape == ResponseMetadata.BodyShape.NOT_INSPECTED
                    ? "" : "\nDeclared content type: " + contentType.name() + "\nResponse prefix: " + bodyShape.name());
    }

    private String stageLabel() {
        switch (stage) {
            case READ_ACCOUNT: return "Reading saved connection";
            case REFRESH_ACCOUNT: return "Refreshing saved connection";
            case BUILD_REQUEST: return "Preparing the AI request";
            case CONNECT: return "Opening the AI connection";
            case SEND_REQUEST: return "Sending the AI request";
            case READ_RESPONSE: return "Reading the AI response";
            case PARSE_RESPONSE: return "Validating the AI response";
            case APPLY_REPLY: return "Applying the AI reply";
            case COMPLETE: return "AI request complete";
            default: return "AI request";
        }
    }
}
