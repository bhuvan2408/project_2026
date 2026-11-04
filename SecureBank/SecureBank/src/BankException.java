/** Used for normal business problems: wrong PIN, locked account, bad input... */
public class BankException extends Exception {
    public BankException(String message) { super(message); }
}
