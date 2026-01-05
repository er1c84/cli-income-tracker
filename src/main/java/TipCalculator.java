import java.sql.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Scanner;

public class TipCalculator {

    // constants for wage (server or host)
    private static final double SERVER_WAGE = 3.00;
    private static final double HOST_WAGE = 11.50;

    // SQLite database file saved in your project folder
    private static final String DB_URL = "jdbc:sqlite:tip_calculator.db";

    public static void main(String[] args) {
        initDatabase();

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\n========== Tip Calculator ==========");
                System.out.println("1. Log a shift (save to database)");
                System.out.println("2. Monthly summary (avg $/hr)");
                System.out.println("3. List shifts for a month");
                System.out.println("4. Exit");
                System.out.print("Choose an option (1-4): ");

                String choice = scanner.nextLine().trim();
                switch (choice) {
                    case "1" -> logShift(scanner);
                    case "2" -> monthlySummary(scanner);
                    case "3" -> listShifts(scanner);
                    case "4" -> {
                        System.out.println("Goodbye!");
                        return;
                    }
                    default -> System.out.println("Invalid option. Please enter 1-4.");
                }
            }
        }
    }

    // ---------- OPTION 1: LOG SHIFT ----------

    private static void logShift(Scanner scanner) {
        int roleChoice = readIntInRange(scanner,
                "\nAre you a server or host?\n1. Server\n2. Host\nChoose (1-2): ", 1, 2);

        String role = (roleChoice == 1) ? "SERVER" : "HOST";
        double wageRate = (roleChoice == 1) ? SERVER_WAGE : HOST_WAGE;

        LocalDate date = readDate(scanner, "Enter date (YYYY-MM-DD) or press Enter for today: ");

        double tips = readDoubleMin(scanner, "Tips made tonight ($): ", 0.0);
        double hoursWorked = readDoubleMin(scanner, "Hours worked tonight: ", 0.01);

        double wageEarnings = wageRate * hoursWorked;
        double totalEarnings = wageEarnings + tips;
        double earningsPerHour = totalEarnings / hoursWorked;

        insertShift(date, role, hoursWorked, tips, wageRate);

        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);
        System.out.println("\n=================== Shift Saved ===================");
        System.out.println("Date: " + date);
        System.out.println("Role: " + role);
        System.out.println("Wage Rate: " + currency.format(wageRate) + "/hour");
        System.out.println("Tips: " + currency.format(tips));
        System.out.println("Hours Worked: " + hoursWorked);
        System.out.println("Wage Earnings: " + currency.format(wageEarnings));
        System.out.println("Total Earnings: " + currency.format(totalEarnings));
        System.out.println("Earnings Per Hour: " + currency.format(earningsPerHour));
    }

    // ---------- OPTION 2: MONTHLY SUMMARY ----------

    private static void monthlySummary(Scanner scanner) {
        YearMonth ym = readYearMonth(scanner, "Enter month (YYYY-MM): ");
        MonthlySummary ms = getMonthlySummary(ym);

        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);

        System.out.println("\n=================== Monthly Summary ===================");
        System.out.println("Month: " + ym);
        System.out.println("Shifts Logged: " + ms.shiftCount);
        System.out.println("Total Hours: " + round2(ms.totalHours));
        System.out.println("Total Tips: " + currency.format(ms.totalTips));
        System.out.println("Total Wage Earnings: " + currency.format(ms.totalWages));
        System.out.println("Total Earnings: " + currency.format(ms.totalEarnings));

        if (ms.totalHours > 0) {
            System.out.println("Average Earnings Per Hour: " + currency.format(ms.totalEarnings / ms.totalHours));
        } else {
            System.out.println("Average Earnings Per Hour: N/A (no hours logged)");
        }
    }

    // ---------- OPTION 3: LIST SHIFTS ----------

    private static void listShifts(Scanner scanner) {
        YearMonth ym = readYearMonth(scanner, "Enter month (YYYY-MM): ");
        listShiftsForMonth(ym);
    }

    // ---------- DATABASE SETUP ----------

    private static void initDatabase() {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS shifts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                shift_date TEXT NOT NULL,      -- YYYY-MM-DD
                role TEXT NOT NULL,            -- SERVER or HOST
                hours_worked REAL NOT NULL,
                tips REAL NOT NULL,
                wage_rate REAL NOT NULL
            );
        """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
        } catch (SQLException e) {
            System.out.println("Failed to initialize database: " + e.getMessage());
        }
    }

    private static void insertShift(LocalDate date, String role, double hoursWorked, double tips, double wageRate) {
        String sql = """
            INSERT INTO shifts (shift_date, role, hours_worked, tips, wage_rate)
            VALUES (?, ?, ?, ?, ?);
        """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, date.toString());
            ps.setString(2, role);
            ps.setDouble(3, hoursWorked);
            ps.setDouble(4, tips);
            ps.setDouble(5, wageRate);

            ps.executeUpdate();

        } catch (SQLException e) {
            System.out.println("Failed to save shift: " + e.getMessage());
        }
    }

    private static MonthlySummary getMonthlySummary(YearMonth ym) {
        String start = ym.atDay(1).toString();
        String end = ym.atEndOfMonth().toString();

        String sql = """
            SELECT
                COUNT(*) AS shift_count,
                COALESCE(SUM(hours_worked), 0) AS total_hours,
                COALESCE(SUM(tips), 0) AS total_tips,
                COALESCE(SUM(hours_worked * wage_rate), 0) AS total_wages,
                COALESCE(SUM(tips + (hours_worked * wage_rate)), 0) AS total_earnings
            FROM shifts
            WHERE shift_date BETWEEN ? AND ?;
        """;

        MonthlySummary ms = new MonthlySummary();

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, start);
            ps.setString(2, end);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ms.shiftCount = rs.getInt("shift_count");
                    ms.totalHours = rs.getDouble("total_hours");
                    ms.totalTips = rs.getDouble("total_tips");
                    ms.totalWages = rs.getDouble("total_wages");
                    ms.totalEarnings = rs.getDouble("total_earnings");
                }
            }

        } catch (SQLException e) {
            System.out.println("Failed to fetch monthly summary: " + e.getMessage());
        }

        return ms;
    }

    private static void listShiftsForMonth(YearMonth ym) {
        String start = ym.atDay(1).toString();
        String end = ym.atEndOfMonth().toString();

        String sql = """
            SELECT shift_date, role, hours_worked, tips, wage_rate,
                   (tips + hours_worked * wage_rate) AS total_earnings,
                   ((tips + hours_worked * wage_rate) / hours_worked) AS earnings_per_hour
            FROM shifts
            WHERE shift_date BETWEEN ? AND ?
            ORDER BY shift_date ASC, id ASC;
        """;

        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, start);
            ps.setString(2, end);

            try (ResultSet rs = ps.executeQuery()) {
                int count = 0;
                System.out.println("\n=================== Shifts for " + ym + " ===================");
                while (rs.next()) {
                    count++;

                    String date = rs.getString("shift_date");
                    String role = rs.getString("role");
                    double hours = rs.getDouble("hours_worked");
                    double tips = rs.getDouble("tips");
                    double wage = rs.getDouble("wage_rate");
                    double total = rs.getDouble("total_earnings");
                    double eph = rs.getDouble("earnings_per_hour");

                    System.out.println("[" + date + "] " + role
                            + " | Hours: " + round2(hours)
                            + " | Tips: " + currency.format(tips)
                            + " | Wage: " + currency.format(wage) + "/hr"
                            + " | Total: " + currency.format(total)
                            + " | $/hr: " + currency.format(eph));
                }

                if (count == 0) {
                    System.out.println("No shifts logged for this month.");
                }
            }

        } catch (SQLException e) {
            System.out.println("Failed to list shifts: " + e.getMessage());
        }
    }

    // ---------- INPUT HELPERS ----------

    private static int readIntInRange(Scanner scanner, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim();
            try {
                int v = Integer.parseInt(s);
                if (v < min || v > max) {
                    System.out.println("Please enter a number between " + min + " and " + max + ".");
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private static double readDoubleMin(Scanner scanner, String prompt, double min) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim();
            try {
                double v = Double.parseDouble(s);
                if (v < min) {
                    System.out.println("Please enter a value >= " + min);
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private static LocalDate readDate(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim();
            if (s.isEmpty()) return LocalDate.now();
            try {
                return LocalDate.parse(s); // YYYY-MM-DD
            } catch (Exception e) {
                System.out.println("Invalid date. Use YYYY-MM-DD (example: 2026-01-04).");
            }
        }
    }

    private static YearMonth readYearMonth(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim();
            try {
                return YearMonth.parse(s); // YYYY-MM
            } catch (Exception e) {
                System.out.println("Invalid month. Use YYYY-MM (example: 2026-01).");
            }
        }
    }

    private static String round2(double v) {
        return String.format("%.2f", v);
    }

    private static class MonthlySummary {
        int shiftCount = 0;
        double totalHours = 0;
        double totalTips = 0;
        double totalWages = 0;
        double totalEarnings = 0;
    }
}
