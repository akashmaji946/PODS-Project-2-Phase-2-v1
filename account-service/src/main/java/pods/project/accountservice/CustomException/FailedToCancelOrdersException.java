package pods.project.accountservice.CustomException;

public class FailedToCancelOrdersException extends RuntimeException {

    public FailedToCancelOrdersException(String message) {
        super(message);
    }

    public FailedToCancelOrdersException(String message, Throwable cause) {
        super(message, cause);
    }
}
