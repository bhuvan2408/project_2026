import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Parent class. Abstract, so only Savings/Current accounts can really be created. */
public abstract class Account {
    public static final int MAX_PIN_ATTEMPTS = 3;

    private final int accountNo;
    private final String holderName;
    private double balance;            // private: changed only through methods
    private final String pinHash;      // PIN is never stored as plain text
    private final String salt;
    private int failedAttempts;
    private boolean locked;

    protected Account(int accountNo, String holderName, double balance,
                      String pinHash, String salt, int failedAttempts, boolean locked) {
        this.accountNo = accountNo;
        this.holderName = holderName;
        this.balance = balance;
        this.pinHash = pinHash;
        this.salt = salt;
        this.failedAttempts = failedAttempts;
        this.locked = locked;
    }

    // ---------- getters ----------
    public int getAccountNo() { return accountNo; }
    public String getHolderName() { return holderName; }
    public double getBalance() { return balance; }
    public String getPinHash() { return pinHash; }
    public String getSalt() { return salt; }
    public int getFailedAttempts() { return failedAttempts; }
    public boolean isLocked() { return locked; }

    // ---------- money ----------
    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive.");
        balance += amount;
    }

    /** Child classes call this after they have checked their own rules. */
    protected void debit(double amount) { balance -= amount; }

    /** Every account type has its own withdrawal rules (overridden). */
    public abstract void withdraw(double amount) throws InsufficientFundsException;

    public abstract String getType();
    public abstract String getRule();
    /** Maximum amount that can be withdrawn right now. */
    public abstract double getWithdrawLimit();

    public String displayDetails() {
        return String.format("Account %d | %s | %s | Balance: Rs. %,.2f",
                accountNo, getType(), holderName, balance);
    }

    // ---------- PIN handling ----------
    public boolean pinMatches(String pin) { return hash(pin, salt).equals(pinHash); }

    /** @return true if this wrong attempt locked the account */
    public boolean registerFailedAttempt() {
        failedAttempts++;
        if (failedAttempts >= MAX_PIN_ATTEMPTS) locked = true;
        return locked;
    }
    public void resetAttempts() { failedAttempts = 0; }
    public void unlock() { locked = false; failedAttempts = 0; }

    public static String newSalt() {
        byte[] b = new byte[12];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public static String hash(String pin, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((salt + pin).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
