package JDBC;


import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Scanner;

public class library_management_system {

    private static final String URL = "jdbc:mysql://localhost:3306/library_db";
    private static final String USER = "root";
    private static final String PASSWORD = "praba";

    private static final double FINE_PER_DAY = 5.0;
    private static final int LOAN_DAYS = 14;

    private static final Scanner sc = new Scanner(System.in);

    // JDBC connection
    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void main(String[] args) throws SQLException {

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            while (true) {
                System.out.println("\n===== LIBRARY MANAGEMENT SYSTEM =====");
                System.out.println("1. Add Book");
                System.out.println("2. Search Books");
                System.out.println("3. Issue Book");
                System.out.println("4. Return Book");
                System.out.println("5. Calculate Fine");
                System.out.println("6. View All Books");
                System.out.println("7. View Issue History (JOIN)");
                System.out.println("8. Exit");
                System.out.print("Enter choice: ");

                int choice = Integer.parseInt(sc.nextLine());

                switch (choice) {
                    case 1:
                        addBook();
                        break;
                    case 2:
                        searchBooks();
                        break;
                    case 3:
                        issueBook();
                        break;
                    case 4:
                        returnBook();
                        break;
                    case 5:
                        calculateFine();
                        break;
                    case 6:
                        viewAllBooks();
                        break;
                    case 7:
                        viewIssueHistory();
                        break;
                    case 8:
                        System.out.println("Thank you!");
                        sc.close();
                        return;
                    default:
                        System.out.println("Invalid choice.");
                }
            }

        } catch (ClassNotFoundException e) {
            System.out.println("MySQL JDBC driver not found.");
            System.out.println("Check the Connector/J Build Path.");
        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid number.");
        }	
    }

    // 1. Add book - CREATE
    private static void addBook() {
        System.out.print("Enter book title: ");
        String title = sc.nextLine();

        System.out.print("Enter author: ");
        String author = sc.nextLine();

        System.out.print("Enter category: ");
        String category = sc.nextLine();

        System.out.print("Enter quantity: ");
        int quantity = Integer.parseInt(sc.nextLine());

        if (quantity <= 0) {
            System.out.println("Quantity must be positive.");
            return;
        }

        String sql = "INSERT INTO books " +
                "(title, author, category, quantity, available_quantity) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, title);
            ps.setString(2, author);
            ps.setString(3, category);
            ps.setInt(4, quantity);
            ps.setInt(5, quantity);

            ps.executeUpdate();
            System.out.println("Book added successfully.");

        } catch (SQLException e) {
            System.out.println("Unable to add book: " + e.getMessage());
        }
    }

    // 2. Search books - READ
    private static void searchBooks() {
        System.out.print("Enter title or author to search: ");
        String keyword = sc.nextLine();

        String sql = "SELECT * FROM books " +
                "WHERE title LIKE ? OR author LIKE ?";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, "%" + keyword + "%");
            ps.setString(2, "%" + keyword + "%");

            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;

                while (rs.next()) {
                    found = true;
                    displayBook(rs);
                }

                if (!found) {
                    System.out.println("No books found.");
                }
            }

        } catch (SQLException e) {
            System.out.println("Search failed: " + e.getMessage());
        }
    }

    // 3. Issue book - TRANSACTION
    private static void issueBook() {
        System.out.print("Enter book ID: ");
        int bookId = Integer.parseInt(sc.nextLine());

        System.out.print("Enter member ID: ");
        int memberId = Integer.parseInt(sc.nextLine());

        String checkMember =
                "SELECT member_id FROM members WHERE member_id = ?";

        String checkBook =
                "SELECT available_quantity FROM books " +
                "WHERE book_id = ? FOR UPDATE";

        String reduceQuantity =
                "UPDATE books SET available_quantity = " +
                "available_quantity - 1 " +
                "WHERE book_id = ? AND available_quantity > 0";

        String insertIssue =
                "INSERT INTO book_issues " +
                "(book_id, member_id, issue_date, due_date, status) " +
                "VALUES (?, ?, ?, ?, 'ISSUED')";

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);

            try {
                // Confirm member exists
                try (PreparedStatement ps =
                             con.prepareStatement(checkMember)) {
                    ps.setInt(1, memberId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Member not found.");
                        }
                    }
                }

                // Lock the book row during the transaction
                try (PreparedStatement ps =
                             con.prepareStatement(checkBook)) {
                    ps.setInt(1, bookId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Book not found.");
                        }

                        if (rs.getInt("available_quantity") <= 0) {
                            throw new SQLException(
                                    "No copies available.");
                        }
                    }
                }

                try (PreparedStatement ps =
                             con.prepareStatement(reduceQuantity)) {
                    ps.setInt(1, bookId);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                                "Could not update book quantity.");
                    }
                }

                LocalDate today = LocalDate.now();
                LocalDate dueDate = today.plusDays(LOAN_DAYS);

                try (PreparedStatement ps =
                             con.prepareStatement(insertIssue)) {
                    ps.setInt(1, bookId);
                    ps.setInt(2, memberId);
                    ps.setDate(3, Date.valueOf(today));
                    ps.setDate(4, Date.valueOf(dueDate));
                    ps.executeUpdate();
                }

                con.commit();
                System.out.println("Book issued successfully.");
                System.out.println("Due date: " + dueDate);

            } catch (SQLException e) {
                con.rollback();
                System.out.println(
                        "Issue failed; changes rolled back: "
                                + e.getMessage());
            } finally {
                con.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    // 4. Return book - TRANSACTION
    private static void returnBook() {
        System.out.print("Enter issue ID: ");
        int issueId = Integer.parseInt(sc.nextLine());

        String findIssue =
                "SELECT book_id, due_date FROM book_issues " +
                "WHERE issue_id = ? AND status = 'ISSUED' FOR UPDATE";

        String markReturned =
                "UPDATE book_issues SET return_date = ?, " +
                "fine = ?, status = 'RETURNED' " +
                "WHERE issue_id = ? AND status = 'ISSUED'";

        String increaseQuantity =
                "UPDATE books SET available_quantity = " +
                "available_quantity + 1 WHERE book_id = ?";

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);

            try {
                int bookId;
                LocalDate dueDate;

                try (PreparedStatement ps =
                             con.prepareStatement(findIssue)) {
                    ps.setInt(1, issueId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException(
                                    "Active issue not found.");
                        }

                        bookId = rs.getInt("book_id");
                        dueDate = rs.getDate("due_date")
                                .toLocalDate();
                    }
                }

                LocalDate today = LocalDate.now();
                double fine = calculateFineAmount(dueDate, today);

                try (PreparedStatement ps =
                             con.prepareStatement(markReturned)) {
                    ps.setDate(1, Date.valueOf(today));
                    ps.setDouble(2, fine);
                    ps.setInt(3, issueId);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                                "Return could not be recorded.");
                    }
                }

                try (PreparedStatement ps =
                             con.prepareStatement(increaseQuantity)) {
                    ps.setInt(1, bookId);
                    ps.executeUpdate();
                }

                con.commit();
                System.out.println("Book returned successfully.");
                System.out.printf("Fine: Rs. %.2f%n", fine);

            } catch (SQLException e) {
                con.rollback();
                System.out.println(
                        "Return failed; changes rolled back: "
                                + e.getMessage());
            } finally {
                con.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    // 5. Calculate fine for an active issue
    private static void calculateFine() {
        System.out.print("Enter issue ID: ");
        int issueId = Integer.parseInt(sc.nextLine());

        String sql = "SELECT due_date, return_date, status " +
                "FROM book_issues WHERE issue_id = ?";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, issueId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Issue record not found.");
                    return;
                }

                LocalDate dueDate =
                        rs.getDate("due_date").toLocalDate();

                Date returnSqlDate = rs.getDate("return_date");

                LocalDate endDate = returnSqlDate == null
                        ? LocalDate.now()
                        : returnSqlDate.toLocalDate();

                double fine = calculateFineAmount(dueDate, endDate);

                System.out.println("Status: " + rs.getString("status"));
                System.out.println("Due date: " + dueDate);
                System.out.println("Fine: Rs. " + fine);
            }

        } catch (SQLException e) {
            System.out.println("Fine calculation failed: "
                    + e.getMessage());
        }
    }

    private static double calculateFineAmount(
            LocalDate dueDate, LocalDate endDate) {

        long overdueDays = ChronoUnit.DAYS.between(dueDate, endDate);

        return Math.max(0, overdueDays) * FINE_PER_DAY;
    }

    // 6. View all books
    private static void viewAllBooks() {
        String sql = "SELECT * FROM books ORDER BY book_id";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                displayBook(rs);
            }

        } catch (SQLException e) {
            System.out.println("Unable to display books: "
                    + e.getMessage());
        }
    }

    private static void displayBook(ResultSet rs)
            throws SQLException {

        System.out.println("-----------------------------");
        System.out.println("Book ID: " + rs.getInt("book_id"));
        System.out.println("Title: " + rs.getString("title"));
        System.out.println("Author: " + rs.getString("author"));
        System.out.println("Category: " + rs.getString("category"));
        System.out.println("Total quantity: " + rs.getInt("quantity"));
        System.out.println("Available: "
                + rs.getInt("available_quantity"));
    }

    // 7. JOIN books, members and book_issues
    private static void viewIssueHistory() {
        String sql =
                "SELECT bi.issue_id, b.title, m.member_name, " +
                "bi.issue_date, bi.due_date, bi.return_date, " +
                "bi.fine, bi.status " +
                "FROM book_issues bi " +
                "JOIN books b ON bi.book_id = b.book_id " +
                "JOIN members m ON bi.member_id = m.member_id " +
                "ORDER BY bi.issue_id DESC";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            boolean found = false;

            while (rs.next()) {
                found = true;

                System.out.println("-----------------------------");
                System.out.println("Issue ID: "
                        + rs.getInt("issue_id"));
                System.out.println("Book: "
                        + rs.getString("title"));
                System.out.println("Member: "
                        + rs.getString("member_name"));
                System.out.println("Issue date: "
                        + rs.getDate("issue_date"));
                System.out.println("Due date: "
                        + rs.getDate("due_date"));
                System.out.println("Return date: "
                        + rs.getDate("return_date"));
                System.out.println("Fine: Rs. "
                        + rs.getBigDecimal("fine"));
                System.out.println("Status: "
                        + rs.getString("status"));
            }

            if (!found) {
                System.out.println("No issue history available.");
            }

        } catch (SQLException e) {
            System.out.println("History query failed: "
                    + e.getMessage());
        }
    }
}
	