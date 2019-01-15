package io.yggdrash.core.exception;

public class FailedOperationException extends RuntimeException {
    public static final int code = -10004;

    public FailedOperationException(String msg) {
        super(msg);
    }

    public FailedOperationException(Throwable e) {
        super(e);
    }
}
