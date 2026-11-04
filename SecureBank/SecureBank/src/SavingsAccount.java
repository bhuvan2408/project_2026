public class SavingsAccount extends Account {
    public static final double MIN_BALANCE = 1000;     // final: cannot change
    public static final double INTEREST_RATE = 0.04;   // 4% per year

    public SavingsAccount(int no, String name, double bal, String pinHash, String salt, int failed, boolean locked) {
        super(no, name, bal, pinHash, salt, failed, locked);   // constructor chaining
    }

    @Override
    public void withdraw(double amount) throws InsufficientFundsException {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive.");
        double after = getBalance() - amount;
        if (after < MIN_BALANCE) {
            throw new InsufficientFundsException(
                "Savings account must keep Rs. 1,000 minimum.", MIN_BALANCE - after);
        }
        debit(amount);
    }

    /** Only savings accounts have this method. */
    public double addInterest() {
        double interest = Math.round(getBalance() * INTEREST_RATE * 100) / 100.0;
        if (interest > 0) deposit(interest);
        return interest;
    }

    @Override public String getType() { return "Savings"; }
    @Override public String getRule() { return "Minimum balance Rs. 1,000 | 4% yearly interest"; }
    @Override public double getWithdrawLimit() { return Math.max(0, getBalance() - MIN_BALANCE); }

    @Override
    public String displayDetails() {
        return super.displayDetails() + " | Min balance: Rs. 1,000";
    }
}
