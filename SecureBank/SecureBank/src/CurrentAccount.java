public class CurrentAccount extends Account {
    public static final double OVERDRAFT_LIMIT = 5000;   // final: cannot change

    public CurrentAccount(int no, String name, double bal, String pinHash, String salt, int failed, boolean locked) {
        super(no, name, bal, pinHash, salt, failed, locked);
    }

    @Override
    public void withdraw(double amount) throws InsufficientFundsException {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive.");
        double after = getBalance() - amount;
        if (after < -OVERDRAFT_LIMIT) {
            throw new InsufficientFundsException(
                "Current account overdraft limit of Rs. 5,000 exceeded.", -OVERDRAFT_LIMIT - after);
        }
        debit(amount);
    }

    @Override public String getType() { return "Current"; }
    @Override public String getRule() { return "Overdraft up to Rs. 5,000"; }
    @Override public double getWithdrawLimit() { return getBalance() + OVERDRAFT_LIMIT; }

    @Override
    public String displayDetails() {
        return super.displayDetails() + " | Overdraft limit: Rs. 5,000";
    }
}
