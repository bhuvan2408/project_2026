# SecureBank (Java + SQLite + Web UI)

## Run it
1. Install a Java JDK (version 11 or newer). Check with: `javac -version`
2. Windows: double-click `run.bat`   |   Mac/Linux: `./run.sh`
3. Your browser opens at http://localhost:8080 (the console prints the address).

Demo accounts: 1001 / PIN 1234 (Savings), 2001 / PIN 4321 (Current). Staff code: `admin123`.
To reset all data, stop the app and delete `securebank.db`.

## Files
- `src/Account.java` parent class (abstract, private balance, PIN hashing, lock logic)
- `src/SavingsAccount.java` / `CurrentAccount.java` children (override withdraw(), displayDetails(); final limits)
- `src/InsufficientFundsException.java` custom checked exception with shortfall
- `src/Bank.java` finds accounts, deposits, withdrawals, transfers (all saved in SQLite)
- `src/Database.java` creates tables `accounts` and `transactions`
- `src/BankApp.java` main(): tiny web server, no frameworks
- `web/index.html` the whole user interface
- `lib/sqlite-jdbc.jar` the database driver
