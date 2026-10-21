package JDBC;
import java.sql.*;
import java.util.Scanner;

public class bus_reservation_system {
	private static final String URL = "jdbc:mysql://localhost:3306/bus_reservation";
	    private static final String USER = "root";
	    private static final String PASSWORD = "praba";
	    
	    @SuppressWarnings("resource")
		private static final Scanner sc = new Scanner(System.in);
	    static {
	        try {
	            Class.forName("com.mysql.cj.jdbc.Driver");
	        } catch (ClassNotFoundException e) {
	            throw new RuntimeException(
	                "MySQL JDBC driver not found.", e);
	        }
	    }

	    private static Connection getConnection()
	            throws SQLException {
	        return DriverManager.getConnection(URL, USER, PASSWORD);
	    }
	    
	public static void main(String[] args) {
		while (true) {
            System.out.println("\n===== BUS RESERVATION SYSTEM =====");
            System.out.println("1. Search Buses");
            System.out.println("2. Seat Booking");
            System.out.println("3. Ticket Cancellation");
            System.out.println("4. Passenger Details");
            System.out.println("5. Exit");
            System.out.print("Enter your choice: ");

            int choice = readInt();

            try {
                switch (choice) {
                    case 1:
                        searchBuses();
                        break;
                    case 2:
                        bookTicket();
                        break;
                    case 3:
                        cancelTicket();
                        break;
                    case 4:
                        passengerDetails();
                        break;
                    case 5:
                        System.out.println("Thank you!");
                        sc.close();
                        return;
                    default:
                        System.out.println("Invalid choice.");
                }
            } catch (SQLException e) {
                System.out.println("Database error: "
                    + e.getMessage());
            } catch (IllegalArgumentException e) {
                System.out.println("Input error: "
                    + e.getMessage());
            }
        }
    }

    private static int readInt() {
        while (!sc.hasNextInt()) {
            System.out.print("Enter a valid number: ");
            sc.next();
        }
        return sc.nextInt();
    }

    private static void searchBuses() throws SQLException {
        sc.nextLine();

        System.out.print("Enter source: ");
        String source = sc.nextLine().trim();

        System.out.print("Enter destination: ");
        String destination = sc.nextLine().trim();

        System.out.print("Enter travel date (YYYY-MM-DD): ");
        Date date = Date.valueOf(sc.nextLine().trim());

        String sql = "SELECT * FROM buses "
            + "WHERE LOWER(source) = LOWER(?) "
            + "AND LOWER(destination) = LOWER(?) "
            + "AND travel_date = ? "
            + "ORDER BY departure_time";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, source);
            ps.setString(2, destination);
            ps.setDate(3, date);

            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;

                while (rs.next()) {
                    found = true;
                    System.out.println("\nBus ID: "
                        + rs.getInt("bus_id"));
                    System.out.println("Bus Name: "
                        + rs.getString("bus_name"));
                    System.out.println("Route: "
                        + rs.getString("source") + " -> "
                        + rs.getString("destination"));
                    System.out.println("Departure: "
                        + rs.getTime("departure_time"));
                    System.out.println("Fare: Rs. "
                        + rs.getBigDecimal("fare"));
                    System.out.println("Available Seats: "
                        + rs.getInt("available_seats"));
                }

                if (!found) {
                    System.out.println("No buses found.");
                }
            }
        }
    }

    private static void bookTicket() throws SQLException {
        System.out.print("Enter Bus ID: ");
        int busId = readInt();

        System.out.print("Number of seats to book: ");
        int seats = readInt();

        if (seats <= 0) {
            throw new IllegalArgumentException(
                "Seats must be greater than zero.");
        }

        sc.nextLine();

        System.out.print("Passenger name: ");
        String name = sc.nextLine().trim();

        System.out.print("Age: ");
        int age = readInt();

        if (age <= 0) {
            throw new IllegalArgumentException(
                "Age must be greater than zero.");
        }

        sc.nextLine();

        System.out.print("Gender: ");
        String gender = sc.nextLine().trim();

        System.out.print("Phone: ");
        String phone = sc.nextLine().trim();

        System.out.print("Email: ");
        String email = sc.nextLine().trim();

        if (name.isEmpty() || phone.isEmpty()) {
            throw new IllegalArgumentException(
                "Name and phone are required.");
        }

        String lockBus = "SELECT available_seats FROM buses "
            + "WHERE bus_id = ? FOR UPDATE";

        String insertPassenger = "INSERT INTO passengers "
            + "(passenger_name, age, gender, phone, email) "
            + "VALUES (?, ?, ?, ?, ?)";

        String updateBus = "UPDATE buses "
            + "SET available_seats = available_seats - ? "
            + "WHERE bus_id = ? AND available_seats >= ?";

        String insertBooking = "INSERT INTO bookings "
            + "(passenger_id, bus_id, seats_booked, status) "
            + "VALUES (?, ?, ?, 'CONFIRMED')";

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);

            try {
                // Lock bus row to prevent concurrent overbooking.
                int available;

                try (PreparedStatement ps =
                         con.prepareStatement(lockBus)) {
                    ps.setInt(1, busId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException(
                                "Bus ID not found.");
                        }
                        available = rs.getInt("available_seats");
                    }
                }

                if (available < seats) {
                    throw new SQLException(
                        "Only " + available
                        + " seats are available.");
                }

                int passengerId;

                try (PreparedStatement ps =
                         con.prepareStatement(
                             insertPassenger,
                             Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name);
                    ps.setInt(2, age);
                    ps.setString(3, gender);
                    ps.setString(4, phone);
                    ps.setString(5,
                        email.isEmpty() ? null : email);
                    ps.executeUpdate();

                    try (ResultSet keys =
                             ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                "Could not create passenger.");
                        }
                        passengerId = keys.getInt(1);
                    }
                }

                try (PreparedStatement ps =
                         con.prepareStatement(updateBus)) {
                    ps.setInt(1, seats);
                    ps.setInt(2, busId);
                    ps.setInt(3, seats);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                            "Seat availability changed.");
                    }
                }

                int bookingId;

                try (PreparedStatement ps =
                         con.prepareStatement(
                             insertBooking,
                             Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, passengerId);
                    ps.setInt(2, busId);
                    ps.setInt(3, seats);
                    ps.executeUpdate();

                    try (ResultSet keys =
                             ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                "Could not create booking.");
                        }
                        bookingId = keys.getInt(1);
                    }
                }

                con.commit();

                System.out.println("\nBooking successful!");
                System.out.println("Booking ID: " + bookingId);
                System.out.println("Passenger ID: "
                    + passengerId);
                System.out.println("Seats booked: " + seats);

            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    private static void cancelTicket() throws SQLException {
        System.out.print("Enter Booking ID to cancel: ");
        int bookingId = readInt();

        String lockBooking = "SELECT bus_id, seats_booked, status "
            + "FROM bookings WHERE booking_id = ? FOR UPDATE";

        String cancelBooking = "UPDATE bookings "
            + "SET status = 'CANCELLED' "
            + "WHERE booking_id = ? AND status = 'CONFIRMED'";

        String lockBus = "SELECT bus_id FROM buses "
            + "WHERE bus_id = ? FOR UPDATE";

        String restoreSeats = "UPDATE buses "
            + "SET available_seats = available_seats + ? "
            + "WHERE bus_id = ? "
            + "AND available_seats + ? <= total_seats";

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);

            try {
                int busId;
                int seats;
                String status;

                try (PreparedStatement ps =
                         con.prepareStatement(lockBooking)) {
                    ps.setInt(1, bookingId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException(
                                "Booking not found.");
                        }

                        busId = rs.getInt("bus_id");
                        seats = rs.getInt("seats_booked");
                        status = rs.getString("status");
                    }
                }

                if (!"CONFIRMED".equals(status)) {
                    throw new SQLException(
                        "Booking is already cancelled.");
                }

                // Lock the bus row before restoring seats.
                try (PreparedStatement ps =
                         con.prepareStatement(lockBus)) {
                    ps.setInt(1, busId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException(
                                "Bus not found.");
                        }
                    }
                }

                try (PreparedStatement ps =
                         con.prepareStatement(cancelBooking)) {
                    ps.setInt(1, bookingId);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                            "Could not cancel booking.");
                    }
                }

                try (PreparedStatement ps =
                         con.prepareStatement(restoreSeats)) {
                    ps.setInt(1, seats);
                    ps.setInt(2, busId);
                    ps.setInt(3, seats);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                            "Could not restore seats.");
                    }
                }

                con.commit();
                System.out.println(
                    "Ticket cancelled successfully.");

            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    private static void passengerDetails()
            throws SQLException {

        System.out.print(
            "Enter Passenger ID (0 to view all): ");
        int passengerId = readInt();

        String sql = "SELECT p.passenger_id, "
            + "p.passenger_name, p.age, p.gender, "
            + "p.phone, p.email, b.booking_id, "
            + "b.bus_id, b.seats_booked, b.status, "
            + "bu.bus_name, bu.source, bu.destination, "
            + "bu.travel_date "
            + "FROM passengers p "
            + "LEFT JOIN bookings b "
            + "ON p.passenger_id = b.passenger_id "
            + "LEFT JOIN buses bu ON b.bus_id = bu.bus_id "
            + "WHERE (? = 0 OR p.passenger_id = ?) "
            + "ORDER BY p.passenger_id, b.booking_id";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, passengerId);
            ps.setInt(2, passengerId);

            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;

                while (rs.next()) {
                    found = true;

                    System.out.println("\nPassenger ID: "
                        + rs.getInt("passenger_id"));
                    System.out.println("Name: "
                        + rs.getString("passenger_name"));
                    System.out.println("Age: "
                        + rs.getInt("age"));
                    System.out.println("Gender: "
                        + rs.getString("gender"));
                    System.out.println("Phone: "
                        + rs.getString("phone"));
                    System.out.println("Email: "
                        + rs.getString("email"));

                    int id = rs.getInt("booking_id");

                    if (rs.wasNull()) {
                        System.out.println(
                            "No bookings found.");
                    } else {
                        System.out.println("Booking ID: " + id);
                        System.out.println("Bus ID: "
                            + rs.getInt("bus_id"));
                        System.out.println("Bus: "
                            + rs.getString("bus_name"));
                        System.out.println("Route: "
                            + rs.getString("source") + " -> "
                            + rs.getString("destination"));
                        System.out.println("Travel Date: "
                            + rs.getDate("travel_date"));
                        System.out.println("Seats: "
                            + rs.getInt("seats_booked"));
                        System.out.println("Status: "
                            + rs.getString("status"));
                    }
                    System.out.println("----------------------");
                }

                if (!found) {
                    System.out.println(
                        "No passenger records found.");
                }
            }
        }
    }


	}


