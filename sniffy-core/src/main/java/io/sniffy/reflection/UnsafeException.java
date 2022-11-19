package io.sniffy.reflection;

public class UnsafeException extends Exception {

    public UnsafeException() {
    }

    public UnsafeException(String message) {
        super(message);
    }

    public UnsafeException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsafeException(Throwable cause) {
        super(cause);
    }

}
