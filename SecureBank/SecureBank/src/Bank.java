import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Stores accounts (in the database), finds them by number and performs transfers. */
public class Bank {
    public static final String STAFF_CODE = "admin123";
    private static final double MAX_AMOUNT = 10_000_000;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static String money(double v) { return String.format(Locale.US, "%,.2f", v); }

    // =============== database helpers ===============
    private Account load(Connection c, int no) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM accounts WHERE account_no=?")) {
            ps.setInt(1, no);
            ResultSet r = ps.executeQuery();
            if (!r.next()) return null;
            String type = r.getString("type");
            Account a;   // polymorphism: an Account variable holding a child object
            if (type.equals("Savings"))
                a = new SavingsAccount(no, r.getString("holder_name"), r.getDouble("balance"),
                        r.getString("pin_hash"), r.getString("salt"), r.getInt("failed_attempts"), r.getInt("locked") == 1);
            else
                a = new CurrentAccount(no, r.getString("holder_name"), r.getDouble("balance"),
                        r.getString("pin_hash"), r.getString("salt"), r.getInt("failed_attempts"), r.getInt("locked") == 1);
            return a;
        }
    }

    private void save(Connection c, Account a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE accounts SET balance=?, failed_attempts=?, locked=? WHERE account_no=?")) {
            ps.setDouble(1, Math.round(a.getBalance() * 100) / 100.0);
            ps.setInt(2, a.getFailedAttempts());
            ps.setInt(3, a.isLocked() ? 1 : 0);
            ps.setInt(4, a.getAccountNo());
            ps.executeUpdate();
        }
    }

    private void log(Connection c, int no, String type, double amt, double balAfter, String note) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO transactions(account_no,type,amount,balance_after,note,created_at) VALUES(?,?,?,?,?,?)")) {
            ps.setInt(1, no); ps.setString(2, type); ps.setDouble(3, amt);
            ps.setDouble(4, Math.round(balAfter * 100) / 100.0); ps.setString(5, note);
            ps.setString(6, LocalDateTime.now().format(TS));
            ps.executeUpdate();
        }
    }

    /** PIN check with retry limit: 3 wrong attempts lock the account. */
    private Account authenticate(Connection c, int no, String pin) throws SQLException, BankException {
        Account a = load(c, no);
        if (a == null) throw new BankException("Account not found.");
        if (a.isLocked())
            throw new BankException("This account is locked after 3 wrong PIN attempts. Please contact bank staff to unlock it.");
        if (pin == null || !a.pinMatches(pin)) {
            boolean nowLocked = a.registerFailedAttempt();
            save(c, a);
            if (nowLocked) throw new BankException("Wrong PIN. Your account is now locked. Please contact bank staff.");
            throw new BankException("Wrong PIN. " + (Account.MAX_PIN_ATTEMPTS - a.getFailedAttempts()) + " attempt(s) left.");
        }
        if (a.getFailedAttempts() > 0) { a.resetAttempts(); save(c, a); }
        return a;
    }

    // =============== public operations ===============
    public synchronized int count() throws SQLException {
        try (Connection c = Database.connect(); Statement s = c.createStatement()) {
            ResultSet r = s.executeQuery("SELECT COUNT(*) FROM accounts");
            return r.next() ? r.getInt(1) : 0;
        }
    }

    public synchronized Account openAccount(String type, String name, String pin, double initial)
            throws SQLException, BankException {
        name = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (name.length() < 2 || name.length() > 50) throw new BankException("Please enter a name (2 to 50 characters).");
        if (pin == null || !pin.matches("\\d{4}")) throw new BankException("PIN must be exactly 4 digits.");
        boolean savings = "Savings".equals(type);
        if (!savings && !"Current".equals(type)) throw new BankException("Choose Savings or Current.");
        if (savings && initial < SavingsAccount.MIN_BALANCE)
            throw new BankException("Savings account needs an opening deposit of at least Rs. 1,000.");
        if (initial < 0 || initial > MAX_AMOUNT) throw new BankException("Invalid opening deposit.");

        try (Connection c = Database.connect()) {
            int no;
            try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(account_no),?) FROM accounts WHERE type=?")) {
                ps.setInt(1, savings ? 1000 : 2000); ps.setString(2, type);
                ResultSet r = ps.executeQuery(); r.next(); no = r.getInt(1) + 1;
            }
            String salt = Account.newSalt();
            String hash = Account.hash(pin, salt);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO accounts(account_no,type,holder_name,balance,pin_hash,salt,created_at) VALUES(?,?,?,?,?,?,?)")) {
                ps.setInt(1, no); ps.setString(2, type); ps.setString(3, name); ps.setDouble(4, initial);
                ps.setString(5, hash); ps.setString(6, salt); ps.setString(7, LocalDateTime.now().format(TS));
                ps.executeUpdate();
            }
            log(c, no, "OPEN", initial, initial, "Account opened");
            return load(c, no);
        }
    }

    public synchronized Account login(int no, String pin) throws SQLException, BankException {
        try (Connection c = Database.connect()) { return authenticate(c, no, pin); }
    }

    public synchronized Account view(int no) throws SQLException {
        try (Connection c = Database.connect()) { return load(c, no); }
    }

    public synchronized String deposit(int no, String pin, double amt) throws SQLException, BankException {
        try (Connection c = Database.connect()) {
            Account a = authenticate(c, no, pin);
            a.deposit(amt);
            save(c, a);
            log(c, no, "DEPOSIT", amt, a.getBalance(), "Cash deposit");
            return "Deposited Rs. " + money(amt) + ". New balance: Rs. " + money(a.getBalance());
        }
    }

    public synchronized String withdraw(int no, String pin, double amt)
            throws SQLException, BankException, InsufficientFundsException {
        try (Connection c = Database.connect()) {
            Account a = authenticate(c, no, pin);
            a.withdraw(amt);   // polymorphism: Savings or Current rules run here
            save(c, a);
            log(c, no, "WITHDRAW", amt, a.getBalance(), "Cash withdrawal");
            String how = a.getBalance() < 0 ? "Withdrawn using overdraft. " : "Withdrawn Rs. " + money(amt) + ". ";
            return how + "New balance: Rs. " + money(a.getBalance());
        }
    }

    public synchronized String transfer(int from, String pin, int to, double amt)
            throws SQLException, BankException, InsufficientFundsException {
        try (Connection c = Database.connect()) {
            Account src = authenticate(c, from, pin);
            if (from == to) throw new BankException("You cannot transfer to the same account.");
            Account dst = load(c, to);
            if (dst == null) throw new BankException("Destination account " + to + " not found.");
            c.setAutoCommit(false);
            try {
                src.withdraw(amt);
                dst.deposit(amt);
                save(c, src); save(c, dst);
                log(c, from, "TRANSFER_OUT", amt, src.getBalance(), "Transfer to account " + to);
                log(c, to, "TRANSFER_IN", amt, dst.getBalance(), "Transfer from account " + from);
                c.commit();   // both sides succeed together, or neither does
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
            return "Transferred Rs. " + money(amt) + " to account " + to + ". New balance: Rs. " + money(src.getBalance());
        }
    }

    public synchronized String applyInterest(int no, String pin) throws SQLException, BankException {
        try (Connection c = Database.connect()) {
            Account a = authenticate(c, no, pin);
            if (!(a instanceof SavingsAccount)) throw new BankException("Interest is only available on Savings accounts.");
            double interest = ((SavingsAccount) a).addInterest();
            save(c, a);
            log(c, no, "INTEREST", interest, a.getBalance(), "4% yearly interest");
            return "Interest of Rs. " + money(interest) + " added. New balance: Rs. " + money(a.getBalance());
        }
    }

    /** Last 5 transactions in a fixed-size array (newest first, unused slots stay null). */
    public synchronized Transaction[] lastFive(int no) throws SQLException {
        Transaction[] last = new Transaction[5];
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM transactions WHERE account_no=? ORDER BY id DESC LIMIT 5")) {
            ps.setInt(1, no);
            ResultSet r = ps.executeQuery();
            int i = 0;
            while (r.next() && i < 5)
                last[i++] = new Transaction(r.getLong("id"), r.getString("type"), r.getDouble("amount"),
                        r.getDouble("balance_after"), r.getString("note"), r.getString("created_at"));
        }
        return last;
    }

    /** Returns a masked name like "R**** K****" so people can confirm a recipient, or null. */
    public synchronized String lookupMasked(int no) throws SQLException {
        Account a = view(no);
        if (a == null) return null;
        StringBuilder sb = new StringBuilder();
        for (String w : a.getHolderName().split(" ")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(w.charAt(0));
            for (int i = 1; i < w.length(); i++) sb.append('*');
        }
        return sb.toString();
    }

    // =============== staff ===============
    private void checkStaff(String code) throws BankException {
        if (!STAFF_CODE.equals(code)) throw new BankException("Invalid staff code.");
    }

    public synchronized List<Account> staffList(String code) throws SQLException, BankException {
        checkStaff(code);
        List<Account> list = new ArrayList<>();
        try (Connection c = Database.connect(); Statement s = c.createStatement()) {
            ResultSet r = s.executeQuery("SELECT account_no FROM accounts ORDER BY account_no");
            List<Integer> nos = new ArrayList<>();
            while (r.next()) nos.add(r.getInt(1));
            for (int n : nos) list.add(load(c, n));
        }
        return list;
    }

    public synchronized String unlock(String code, int no) throws SQLException, BankException {
        checkStaff(code);
        try (Connection c = Database.connect()) {
            Account a = load(c, no);
            if (a == null) throw new BankException("Account not found.");
            a.unlock();
            save(c, a);
            return "Account " + no + " unlocked.";
        }
    }
}
