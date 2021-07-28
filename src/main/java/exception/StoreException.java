package exception;

public class StoreException extends Exception {
  
  private static final long serialVersionUID = 1L;

  public StoreException() {
    super();
  }
  
  public StoreException(String message) {
    super(message);
  }
}
