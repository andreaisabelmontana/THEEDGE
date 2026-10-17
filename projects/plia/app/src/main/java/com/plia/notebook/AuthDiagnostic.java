package com.plia.notebook;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;

/** Allowlisted diagnostics only. Never inspect exception text, causes, URLs or response bodies. */
public final class AuthDiagnostic {
    public enum Stage { READ_STORE, BIND_CALLBACK, PREPARE_REQUEST, OPEN_BROWSER, WAIT_CALLBACK,
        TOKEN_EXCHANGE, FETCH_KEYS, VERIFY_IDENTITY, VALIDATE_GRANT, SAVE_ACCOUNT, COMPLETE }
    public enum Code { IN_PROGRESS, COMPLETED, CANCELLED, BROWSER_UNAVAILABLE, BROWSER_DISABLED, BROWSER_BLOCKED, CALLBACK_TIMEOUT,
        DNS, CONNECT, TIMEOUT, TLS, IO, HTTP_ERROR, INVALID_RESPONSE, PERMISSION_MISSING,
        STORE_READ, STORE_WRITE, INVALID_INPUT, CALLBACK_REJECTED, OAUTH_ERROR, ATTEMPT_EXPIRED,
        UNKNOWN_KEY, TOKEN_REJECTED, UNEXPECTED }

    public final Stage stage;
    public final Code code;
    public final int httpStatus;
    public final long elapsedMs;

    private AuthDiagnostic(Stage stage, Code code, int httpStatus, long elapsedMs) {
        this.stage = stage == null ? Stage.READ_STORE : stage;
        this.code = code == null ? Code.UNEXPECTED : code;
        this.httpStatus = this.code == Code.HTTP_ERROR && httpStatus >= 100 && httpStatus <= 599 ? httpStatus : 0;
        this.elapsedMs = Math.max(0, elapsedMs);
    }
    public static final class HttpFailure extends IOException {
        public final int status;
        public HttpFailure(int status) { super("Sign-in HTTP failure."); this.status = status; }
    }
    public static final class Failure extends IOException {
        public final Code code;
        public Failure(Code code) { super("Sign-in operation failed."); this.code = code; }
    }
    public static AuthDiagnostic progress(Stage stage, long elapsedMs) {
        return new AuthDiagnostic(stage, Code.IN_PROGRESS, 0, elapsedMs);
    }
    public static AuthDiagnostic completed(long elapsedMs) {
        return new AuthDiagnostic(Stage.COMPLETE, Code.COMPLETED, 0, elapsedMs);
    }
    /** Only these four fields are persisted; unknown or corrupt enum values are discarded. */
    public static AuthDiagnostic restore(String stage, String code, int httpStatus, long elapsedMs) {
        try { return new AuthDiagnostic(Stage.valueOf(stage), Code.valueOf(code), httpStatus, elapsedMs); }
        catch (IllegalArgumentException | NullPointerException invalid) { return null; }
    }
    public static AuthDiagnostic classify(Stage stage, Throwable error, long elapsedMs) {
        Code code; int status = 0;
        if (error instanceof HttpFailure) { code = Code.HTTP_ERROR; status = ((HttpFailure)error).status; }
        else if (error instanceof Failure) code = ((Failure)error).code;
        else if (error instanceof SiwcProtocol.ProtocolException) {
            switch (((SiwcProtocol.ProtocolException)error).failure) {
                case INVALID_INPUT: code = Code.INVALID_INPUT; break;
                case CALLBACK_REJECTED: code = Code.CALLBACK_REJECTED; break;
                case OAUTH_ERROR: code = Code.OAUTH_ERROR; break;
                case ATTEMPT_EXPIRED: code = Code.ATTEMPT_EXPIRED; break;
                case UNKNOWN_KEY: code = Code.UNKNOWN_KEY; break;
                case TOKEN_REJECTED: code = Code.TOKEN_REJECTED; break;
                default: code = Code.UNEXPECTED;
            }
        }
        else if (error instanceof UnknownHostException) code = Code.DNS;
        else if (error instanceof ConnectException) code = Code.CONNECT;
        else if (error instanceof SSLException) code = Code.TLS;
        else if (error instanceof SocketTimeoutException || error instanceof InterruptedIOException) code = Code.TIMEOUT;
        else if (error instanceof IOException) {
            code = stage == Stage.READ_STORE ? Code.STORE_READ : stage == Stage.SAVE_ACCOUNT ? Code.STORE_WRITE : Code.IO;
        } else code = Code.UNEXPECTED;
        return new AuthDiagnostic(stage, code, status, elapsedMs);
    }
    public String logLine() {
        return "stage=" + stage.name() + " code=" + code.name()
                + (httpStatus == 0 ? "" : " http_status=" + httpStatus) + " elapsed_ms=" + elapsedMs;
    }
    public String userMessage() {
        String result;
        switch (code) {
            case IN_PROGRESS: result = "No completion recorded yet."; break;
            case COMPLETED: result = "ChatGPT sign-in verified."; break;
            case CANCELLED: result = "Sign-in cancelled."; break;
            case BROWSER_UNAVAILABLE: result = "Firefox could not be opened."; break;
            case BROWSER_DISABLED: result = "Firefox is disabled on this device. Enable or unfreeze Firefox, then retry from Plia."; break;
            case BROWSER_BLOCKED: result = "Android blocked opening Firefox. Check its app settings, then retry from Plia."; break;
            case CALLBACK_TIMEOUT: result = "No accepted browser response arrived within five minutes."; break;
            case DNS: result = "The sign-in service address could not be resolved."; break;
            case CONNECT: result = "The connection could not be opened."; break;
            case TIMEOUT: result = "The connection timed out."; break;
            case TLS: result = "The secure connection could not be verified."; break;
            case HTTP_ERROR: result = "The sign-in service returned an unsuccessful HTTP status."; break;
            case INVALID_RESPONSE: result = "The sign-in response was incomplete or invalid."; break;
            case PERMISSION_MISSING: result = "The required model permission was not granted."; break;
            case STORE_READ: result = "Saved sign-in data could not be read."; break;
            case STORE_WRITE: result = "Verified sign-in data could not be saved."; break;
            case INVALID_INPUT: result = "The sign-in request could not be prepared."; break;
            case CALLBACK_REJECTED: result = "The browser response did not pass validation."; break;
            case OAUTH_ERROR: result = "The browser returned a sign-in error."; break;
            case ATTEMPT_EXPIRED: result = "The sign-in attempt expired."; break;
            case UNKNOWN_KEY: result = "The identity signing key could not be verified."; break;
            case TOKEN_REJECTED: result = "The account identity did not pass verification."; break;
            case IO: result = "The sign-in operation could not complete."; break;
            default: result = "Sign-in stopped unexpectedly.";
        }
        return "Last recorded sign-in event\n" + stageLabel() + "\n" + result
                + "\nDiagnostic: " + stage.name() + " / " + code.name()
                + (httpStatus == 0 ? "" : " / HTTP " + httpStatus);
    }
    private String stageLabel() {
        switch (stage) {
            case READ_STORE: return "Reading saved connection";
            case BIND_CALLBACK: return "Opening the local browser callback";
            case PREPARE_REQUEST: return "Preparing sign-in";
            case OPEN_BROWSER: return "Opening Firefox";
            case WAIT_CALLBACK: return "Waiting for the browser response";
            case TOKEN_EXCHANGE: return "Exchanging the sign-in response";
            case FETCH_KEYS: return "Fetching identity verification keys";
            case VERIFY_IDENTITY: return "Verifying account identity";
            case VALIDATE_GRANT: return "Checking granted permissions";
            case SAVE_ACCOUNT: return "Saving the verified account";
            case COMPLETE: return "Sign-in complete";
            default: return "Sign-in";
        }
    }
}
