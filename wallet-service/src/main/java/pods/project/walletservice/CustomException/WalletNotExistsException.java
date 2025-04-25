package pods.project.walletservice.CustomException;

public class WalletNotExistsException extends RuntimeException {

    public WalletNotExistsException(String message) {
        super(message);
    }

    public WalletNotExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
