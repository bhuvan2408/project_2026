/** One line of an account statement. */
public class Transaction {
    public final long id;
    public final String type;      // OPEN, DEPOSIT, WITHDRAW, TRANSFER_OUT, TRANSFER_IN, INTEREST
    public final double amount;
    public final double balanceAfter;
    public final String note;
    public final String time;

    public Transaction(long id, String type, double amount, double balanceAfter, String note, String time) {
        this.id = id; this.type = type; this.amount = amount;
        this.balanceAfter = balanceAfter; this.note = note; this.time = time;
    }
}
