import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;

/** Starts the web server. Holds main(); only handles web requests and calls Bank. */
public class BankApp {
    private static final Bank bank = new Bank();
    private static final Path WEB = Paths.get("web").toAbsolutePath().normalize();

    public static void main(String[] args) throws Exception {
        Database.init();
        if (bank.count() == 0) {   // demo accounts so you can try it immediately
            bank.openAccount("Savings", "Demo Saver", "1234", 25000);
            bank.openAccount("Current", "Demo Business", "4321", 10000);
        }
        HttpServer server = null;
        int port = 8080;
        for (int p = 8080; p < 8100 && server == null; p++) {
            try { server = HttpServer.create(new InetSocketAddress("127.0.0.1", p), 0); port = p; }
            catch (BindException ignored) { }
        }
        if (server == null) { System.out.println("No free port between 8080-8099."); return; }
        server.createContext("/api/", BankApp::api);
        server.createContext("/", BankApp::staticFile);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        String url = "http://localhost:" + port;
        System.out.println("=====================================");
        System.out.println(" SecureBank is running at " + url);
        System.out.println(" Press Ctrl+C to stop.");
        System.out.println("=====================================");
        try { java.awt.Desktop.getDesktop().browse(new URI(url)); } catch (Throwable ignored) { }
    }

    // ---------------- API ----------------
    private static void api(HttpExchange ex) throws IOException {
        int status = 200;
        String json;
        try {
            Map<String, String> p = new HashMap<>();
            parseForm(ex.getRequestURI().getRawQuery(), p);
            if ("POST".equals(ex.getRequestMethod())) {
                byte[] body = ex.getRequestBody().readNBytes(10_000);
                parseForm(new String(body, StandardCharsets.UTF_8), p);
            }
            json = route(ex.getRequestURI().getPath(), p);
        } catch (InsufficientFundsException e) {
            status = 400;
            json = "{\"ok\":false,\"error\":" + q(e.getMessage()) + ",\"shortfall\":" + num(e.getShortfall()) + "}";
        } catch (BankException | IllegalArgumentException e) {
            status = 400;
            json = "{\"ok\":false,\"error\":" + q(e.getMessage()) + "}";
        } catch (Exception e) {
            e.printStackTrace();
            status = 500;
            json = "{\"ok\":false,\"error\":\"Something went wrong on the server. Please try again.\"}";
        }
        send(ex, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private static String route(String path, Map<String, String> p) throws Exception {
        switch (path) {
            case "/api/open": {
                Account a = bank.openAccount(p.get("type"), p.get("name"), p.get("pin"), amount(p, "deposit", true));
                return "{\"ok\":true,\"message\":" + q("Account opened successfully!") + ",\"accountNo\":" + a.getAccountNo() + "}";
            }
            case "/api/login": {
                Account a = bank.login(intOf(p, "acc"), p.get("pin"));
                return state(a.getAccountNo(), "Welcome back, " + a.getHolderName() + "!");
            }
            case "/api/deposit": {
                String m = bank.deposit(intOf(p, "acc"), p.get("pin"), amount(p, "amount", false));
                return state(intOf(p, "acc"), m);
            }
            case "/api/withdraw": {
                String m = bank.withdraw(intOf(p, "acc"), p.get("pin"), amount(p, "amount", false));
                return state(intOf(p, "acc"), m);
            }
            case "/api/transfer": {
                String m = bank.transfer(intOf(p, "acc"), p.get("pin"), intOf(p, "to"), amount(p, "amount", false));
                return state(intOf(p, "acc"), m);
            }
            case "/api/interest": {
                String m = bank.applyInterest(intOf(p, "acc"), p.get("pin"));
                return state(intOf(p, "acc"), m);
            }
            case "/api/lookup": {
                String name = bank.lookupMasked(intOf(p, "acc"));
                return "{\"ok\":true,\"name\":" + (name == null ? "null" : q(name)) + "}";
            }
            case "/api/staff/list": {
                StringBuilder sb = new StringBuilder("{\"ok\":true,\"accounts\":[");
                boolean first = true;
                for (Account a : bank.staffList(p.get("code"))) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append(accountJson(a));
                }
                return sb.append("]}").toString();
            }
            case "/api/staff/unlock": {
                String m = bank.unlock(p.get("code"), intOf(p, "acc"));
                return "{\"ok\":true,\"message\":" + q(m) + "}";
            }
            default:
                throw new IllegalArgumentException("Unknown request.");
        }
    }

    /** Fresh account details + last 5 transactions + a message. */
    private static String state(int no, String message) throws Exception {
        Account a = bank.view(no);
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"message\":" + q(message) + ",\"account\":" + accountJson(a) + ",\"transactions\":[");
        boolean first = true;
        for (Transaction t : bank.lastFive(no)) {
            if (t == null) continue;   // empty slot in the fixed array
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"type\":").append(q(t.type)).append(",\"amount\":").append(num(t.amount))
              .append(",\"balanceAfter\":").append(num(t.balanceAfter)).append(",\"note\":").append(q(t.note))
              .append(",\"time\":").append(q(t.time)).append('}');
        }
        return sb.append("]}").toString();
    }

    private static String accountJson(Account a) {
        return "{\"accountNo\":" + a.getAccountNo() + ",\"type\":" + q(a.getType()) + ",\"name\":" + q(a.getHolderName())
            + ",\"balance\":" + num(a.getBalance()) + ",\"locked\":" + a.isLocked() + ",\"rule\":" + q(a.getRule())
            + ",\"available\":" + num(a.getWithdrawLimit()) + ",\"summary\":" + q(a.displayDetails()) + "}";
    }

    // ---------------- helpers ----------------
    private static int intOf(Map<String, String> p, String key) {
        try { return Integer.parseInt(p.getOrDefault(key, "").trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Please enter a valid account number."); }
    }

    private static double amount(Map<String, String> p, String key, boolean allowZero) {
        double v;
        try { v = Double.parseDouble(p.getOrDefault(key, "").trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Please enter a valid amount."); }
        if (Double.isNaN(v) || Double.isInfinite(v)) throw new IllegalArgumentException("Please enter a valid amount.");
        if (v < 0 || (!allowZero && v == 0)) throw new IllegalArgumentException("Amount must be positive.");
        if (v > 10_000_000) throw new IllegalArgumentException("Amount is too large (max Rs. 1,00,00,000).");
        return Math.round(v * 100) / 100.0;
    }

    private static void parseForm(String s, Map<String, String> out) throws UnsupportedEncodingException {
        if (s == null || s.isEmpty()) return;
        for (String pair : s.split("&")) {
            int i = pair.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), "UTF-8");
            String v = i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), "UTF-8");
            out.put(k, v);
        }
    }

    private static String num(double d) { return String.format(Locale.US, "%.2f", d); }

    private static String q(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                case '\t': sb.append(' '); break;
                default: if (c < 0x20) sb.append(' '); else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    // ---------------- static website files ----------------
    private static void staticFile(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        Path file = WEB.resolve(path.substring(1)).normalize();
        if (!file.startsWith(WEB) || !Files.isRegularFile(file)) {
            send(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String type = "application/octet-stream";
        String n = file.getFileName().toString();
        if (n.endsWith(".html")) type = "text/html; charset=utf-8";
        else if (n.endsWith(".css")) type = "text/css; charset=utf-8";
        else if (n.endsWith(".js")) type = "application/javascript; charset=utf-8";
        else if (n.endsWith(".svg")) type = "image/svg+xml";
        send(ex, 200, type, Files.readAllBytes(file));
    }

    private static void send(HttpExchange ex, int status, String type, byte[] data) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }
}
