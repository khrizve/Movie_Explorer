package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User class to encapsulate user data (username and password).
 * Implements Serializable to allow saving and loading user objects to/from files.
 */
class User implements Serializable {
    private static final long serialVersionUID = 1L; // For serialization version control
    private String username;
    private String password; // Storing password in plain text for simplicity; in a real app, hash it.

    /**
     * Constructs a new User object.
     * @param username The user's chosen username.
     * @param password The user's chosen password.
     */
    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Retrieves the username of this user.
     * @return The username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Retrieves the password of this user.
     * @return The password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets a new password for this user.
     * @param password The new password.
     */
    public void setPassword(String password) {
        this.password = password;
    }
}

/**
 * UserManager class to handle user registration, login, and profile management.
 * Manages a collection of User objects and handles their persistence to a file.
 * Also includes admin specific functionality.
 */
class UserManager {
    // File to store user data using serialization
    public static final String USERS_FILE = "users.ser";
    private static Map<String, User> users = new HashMap<>(); // Stores users by username
    private static User currentUser = null; // Keeps track of the currently logged-in user

    // Admin credentials (not final anymore, so they can be changed and persisted)
    public static String ADMIN_USERNAME = "admin"; // Default admin username
    public static String ADMIN_PASSWORD = "adminpass"; // Default admin password

    // Static block: executed once when the class is loaded.
    // Used to load existing users from the file and ensure admin user exists.
    static {
        loadUsers();
        // Ensure the default admin user exists. If not, add it.
        if (!users.containsKey(ADMIN_USERNAME)) {
            users.put(ADMIN_USERNAME, new User(ADMIN_USERNAME, ADMIN_PASSWORD));
            saveUsers(); // Save users after adding default admin
        }
        // Add a general test user if no other users are present (excluding admin)
        if (users.size() == 1 && users.containsKey(ADMIN_USERNAME)) {
            users.put("testuser", new User("testuser", "password123"));
            saveUsers();
        }
    }

    /**
     * Registers a new user with the provided username and password.
     * @param username The username to register.
     * @param password The password for the new user.
     * @return true if registration is successful, false if the username already exists.
     */
    public static boolean registerUser(String username, String password) {
        if (users.containsKey(username)) {
            return false; // User already exists
        }
        users.put(username, new User(username, password));
        saveUsers(); // Save users after registration
        return true; // User registered successfully
    }

    /**
     * Authenticates a regular user by checking their username and password.
     * @param username The username to authenticate.
     * @param password The password to check against.
     * @return true if authentication is successful, false otherwise.
     */
    public static boolean authenticateUser(String username, String password) {
        User user = users.get(username);
        if (user != null && user.getPassword().equals(password)) {
            currentUser = user; // Set current user on successful login
            return true;
        }
        return false;
    }

    /**
     * Authenticates an admin user.
     * This method now checks against the 'admin' user stored in the 'users' map.
     * @param username The username to authenticate.
     * @param password The password to check against.
     * @return true if authentication is successful and credentials match admin, false otherwise.
     */
    public static boolean isAdmin(String username, String password) {
        User adminUser = users.get(ADMIN_USERNAME);
        return adminUser != null && ADMIN_USERNAME.equals(username) && adminUser.getPassword().equals(password);
    }

    /**
     * Updates the password for a given user.
     * @param username The username of the user whose password is to be updated.
     * @param newPassword The new password.
     * @return true if the password was updated, false if the user was not found.
     */
    public static boolean updatePassword(String username, String newPassword) {
        User user = users.get(username);
        if (user != null) {
            user.setPassword(newPassword);
            saveUsers(); // Save users after password update
            return true;
        }
        return false;
    }

    /**
     * Updates the admin user's password.
     * This specifically targets the hardcoded admin username.
     * @param newPassword The new password for the admin.
     * @return true if the admin password was updated, false otherwise.
     */
    public static boolean updateAdminPassword(String newPassword) {
        User adminUser = users.get(ADMIN_USERNAME);
        if (adminUser != null) {
            adminUser.setPassword(newPassword);
            // Also update the static ADMIN_PASSWORD variable for immediate use, though it will be reloaded on next app start.
            // This is mainly for consistency in the current runtime.
            ADMIN_PASSWORD = newPassword;
            saveUsers(); // Save users after admin password update
            return true;
        }
        return false;
    }

    /**
     * Deletes a user from the system.
     * @param username The username of the user to delete.
     * @return true if the user was deleted, false if the user was not found or is the admin user.
     */
    public static boolean deleteUser(String username) {
        if (username.equals(ADMIN_USERNAME)) {
            // Prevent deleting the admin user
            return false;
        }
        if (users.containsKey(username)) {
            users.remove(username);
            saveUsers();
            return true;
        }
        return false;
    }

    /**
     * Retrieves the currently logged-in user.
     * @return The current User object, or null if no user is logged in (e.g., guest mode).
     */
    public static User getCurrentUser() {
        return currentUser;
    }

    /**
     * Sets the current user. Useful for logging in/out or setting guest mode.
     * @param user The User object to set as current.
     */
    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    /**
     * Returns a list of all registered users.
     * @return A List of User objects.
     */
    public static List<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    /**
     * Saves the current map of users to the USERS_FILE using object serialization.
     */
    private static void saveUsers() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(USERS_FILE))) {
            oos.writeObject(users);
        } catch (IOException e) {
            // Print stack trace for debugging, but doesn't stop the app
            e.printStackTrace();
        }
    }

    /**
     * Loads users from the USERS_FILE using object deserialization.
     * Handles cases where the file doesn't exist or is empty/corrupt.
     */
    private static void loadUsers() {
        File usersFile = new File(USERS_FILE);
        // If the file doesn't exist or is empty, initialize with an empty map and return.
        // This prevents EOFException on first run.
        if (!usersFile.exists() || usersFile.length() == 0) {
            System.out.println("Users file not found or is empty. Starting with default users.");
            users = new HashMap<>(); // Ensure it's an empty map
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(usersFile))) {
            users = (Map<String, User>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            // Catch other IO errors (like corrupted file) or ClassNotFoundException
            System.err.println("Error loading users: " + e.getMessage());
            e.printStackTrace(); // Print stack trace for debugging
            users = new HashMap<>(); // Reset to empty map on error
        }
    }
}

/**
 * Review class to encapsulate movie review data.
 * Implements Serializable for persistence.
 */
class Review implements Serializable {
    private static final long serialVersionUID = 1L;
    private int movieId;
    private String username;
    private int rating; // 1 to 5 stars
    private String reviewText;
    private long timestamp; // Timestamp of when the review was created

    /**
     * Constructs a new Review object.
     * @param movieId The ID of the movie being reviewed.
     * @param username The username of the reviewer.
     * @param rating The rating given (1-5 stars).
     * @param reviewText The text content of the review.
     */
    public Review(int movieId, String username, int rating, String reviewText) {
        this.movieId = movieId;
        this.username = username;
        this.rating = rating;
        this.reviewText = reviewText;
        this.timestamp = System.currentTimeMillis(); // Record creation time
    }

    // Getter methods for review properties
    public int getMovieId() {
        return movieId;
    }

    public String getUsername() {
        return username;
    }

    public int getRating() {
        return rating;
    }

    public String getReviewText() {
        return reviewText;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns a string representation of the review.
     * @return A formatted string showing rating and review text.
     */
    @Override
    public String toString() {
        return "Rating: " + rating + "/5 by " + username + "\nReview: " + reviewText;
    }
}

/**
 * ReviewManager class to handle storing and retrieving movie reviews.
 * Manages a map of movie IDs to lists of reviews for each movie.
 */
class ReviewManager {
    // File to store review data using serialization
    public static final String REVIEWS_FILE = "reviews.ser";
    // Map from movieId to a list of reviews for that movie
    private static Map<Integer, List<Review>> movieReviews = new HashMap<>();

    // Static block: executed once when the class is loaded.
    // Used to load existing reviews from the file.
    static {
        loadReviews();
    }

    /**
     * Adds a new review to the collection.
     * @param review The Review object to add.
     */
    public static void addReview(Review review) {
        // Use computeIfAbsent to ensure a list exists for the movieId before adding
        movieReviews.computeIfAbsent(review.getMovieId(), k -> new ArrayList<>()).add(review);
        saveReviews(); // Save reviews after adding a new one
    }

    /**
     * Retrieves all reviews for a specific movie ID.
     * @param movieId The ID of the movie.
     * @return A list of Review objects for the specified movie, or an empty list if none exist.
     */
    public static List<Review> getReviewsForMovie(int movieId) {
        return movieReviews.getOrDefault(movieId, Collections.emptyList());
    }

    /**
     * Retrieves all reviews from all movies.
     * @return A flattened list of all Review objects.
     */
    public static List<Review> getAllReviews() {
        return movieReviews.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * Deletes a specific review.
     * @param movieId The ID of the movie the review belongs to.
     * @param username The username of the reviewer.
     * @param timestamp The timestamp of the review (for unique identification).
     * @return true if the review was found and deleted, false otherwise.
     */
    public static boolean deleteReview(int movieId, String username, long timestamp) {
        List<Review> reviews = movieReviews.get(movieId);
        if (reviews != null) {
            boolean removed = reviews.removeIf(r -> r.getUsername().equals(username) && r.getTimestamp() == timestamp);
            if (reviews.isEmpty()) {
                movieReviews.remove(movieId); // Remove movie entry if no reviews left
            }
            if (removed) {
                saveReviews();
            }
            return removed;
        }
        return false;
    }

    /**
     * Updates an existing review's rating and text.
     * @param movieId The ID of the movie the review belongs to.
     * @param username The username of the reviewer.
     * @param oldTimestamp The original timestamp of the review (for unique identification).
     * @param newRating The new rating for the review.
     * @param newReviewText The new review text.
     * @return true if the review was found and updated, false otherwise.
     */
    public static boolean updateReview(int movieId, String username, long oldTimestamp, int newRating, String newReviewText) {
        List<Review> reviews = movieReviews.get(movieId);
        if (reviews != null) {
            for (int i = 0; i < reviews.size(); i++) {
                Review r = reviews.get(i);
                if (r.getUsername().equals(username) && r.getTimestamp() == oldTimestamp) {
                    // Create a new Review object with updated details (timestamp remains the same for identification)
                    reviews.set(i, new Review(movieId, username, newRating, newReviewText));
                    saveReviews();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Saves the current map of movie reviews to the REVIEWS_FILE using object serialization.
     */
    private static void saveReviews() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(REVIEWS_FILE))) {
            oos.writeObject(movieReviews);
        } catch (IOException e) {
            // Print stack trace for debugging
            e.printStackTrace();
        }
    }

    /**
     * Loads movie reviews from the REVIEWS_FILE using object deserialization.
     * Handles cases where the file doesn't exist or is empty/corrupt.
     */
    private static void loadReviews() {
        File reviewsFile = new File(REVIEWS_FILE);
        // If the file doesn't exist or is empty, initialize with an empty map and return.
        // This prevents EOFException on first run.
        if (!reviewsFile.exists() || reviewsFile.length() == 0) {
            System.out.println("Reviews file not found or is empty. Starting with no reviews.");
            movieReviews = new HashMap<>(); // Ensure it's an empty map
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(reviewsFile))) {
            movieReviews = (Map<Integer, List<Review>>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            // Catch other IO errors (like corrupted file) or ClassNotFoundException
            System.err.println("Error loading reviews: " + e.getMessage());
            e.printStackTrace(); // Print stack trace for debugging
            movieReviews = new HashMap<>(); // Reset to empty map on error
        }
    }
}

/**
 * Abstract base class for all JFrame windows in the application.
 * Provides common functionalities like API key, genre map loading, and URL reading.
 */
abstract class AbstractAppFrame extends JFrame {
    protected String apiKey = "88e1a73e31a99950ece7af523fa99460"; // TMDb API Key
    protected Map<Integer, String> genreMap = new HashMap<>(); // Map to store genre IDs and names

    /**
     * Constructs an AbstractAppFrame.
     * @param title The title of the JFrame window.
     */
    public AbstractAppFrame(String title) {
        super(title);
        setDefaultCloseOperation(EXIT_ON_CLOSE); // Close operation for the frame
        setSize(800, 600); // Default size for authentication pages
        setLocationRelativeTo(null); // Center the window on the screen
    }

    /**
     * Loads movie genres from the TMDb API and populates the genreMap.
     */
    protected void loadGenreMap() {
        try {
            String urlStr = "https://api.themoviedb.org/3/genre/movie/list?api_key=" + apiKey;
            String response = readUrl(urlStr);
            JSONObject json = new JSONObject(response);
            JSONArray genres = json.getJSONArray("genres");
            for (int i = 0; i < genres.length(); i++) {
                JSONObject genre = genres.getJSONObject(i);
                genreMap.put(genre.getInt("id"), genre.getString("name"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading genres. Please check your internet connection.", "API Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Helper method to read content from a given URL.
     * @param urlStr The URL string to read from.
     * @return The content read from the URL as a String.
     * @throws IOException If an I/O error occurs during the connection or reading.
     */
    protected String readUrl(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        conn.disconnect();
        return content.toString();
    }
}

/**
 * Welcome, Page: The initial entry point for the application.
 * Allows users to navigate to Log in, Sign Up, continue as a Guest, or Admin Login.
 * Now includes a PNG logo loaded from a local file for visual appeal.
 */
class WelcomePage extends AbstractAppFrame {
    // Define the path to your logo image file.
    // This assumes 'logo.png' is in the same directory as your compiled .class files.
    // If it's in a subfolder (e.g., 'images/logo.png'), use "images/logo.png".
    private static final String LOGO_FILE_PATH = "logo.png";

    public WelcomePage() {
        super("\uD83C\uDFAC Welcome to Movie Explorer"); // Movie camera emoji in title
        setLayout(new GridBagLayout()); // Use GridBagLayout for better centering and control

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10); // Padding around components

        // --- Logo ---
        JLabel logoLabel = new JLabel();
        try {
            // Load image from local file
            File logoFile = new File(LOGO_FILE_PATH);
            if (logoFile.exists()) {
                Image logoImage = ImageIO.read(logoFile);
                // Scale the logo image for display (adjust width/height as needed)
                int preferredWidth = 256;
                int preferredHeight = 256;
                Image scaledLogo = logoImage.getScaledInstance(preferredWidth, preferredHeight, Image.SCALE_SMOOTH);
                logoLabel.setIcon(new ImageIcon(scaledLogo));
            } else {
                System.err.println("Logo file not found: " + LOGO_FILE_PATH);
                logoLabel.setText("Movie Logo (Image Missing)"); // Fallback text if image file is not found
            }
        } catch (IOException e) {
            System.err.println("Error loading logo image from file: " + e.getMessage());
            logoLabel.setText("Movie Logo (Error Loading)"); // Fallback text on I/O error
        }
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3; // Span across three columns
        gbc.anchor = GridBagConstraints.CENTER;
        add(logoLabel, gbc);

        // Welcome to label
        JLabel welcomeLabel = new JLabel("Welcome to Movie Explorer!");
        welcomeLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        gbc.gridy = 1; // Move below the logo
        add(welcomeLabel, gbc);

        // Login button
        JButton loginButton = new JButton("Login");
        loginButton.setFont(new Font("SansSerif", Font.PLAIN, 18));
        gbc.gridy = 2; // Move below welcome label
        gbc.gridwidth = 1; // Reset gridwidth
        gbc.anchor = GridBagConstraints.CENTER; // Center the button
        add(loginButton, gbc);

        // Sign Up button
        JButton signupButton = new JButton("Sign Up");
        signupButton.setFont(new Font("SansSerif", Font.PLAIN, 18));
        gbc.gridx = 1;
        gbc.gridy = 2;
        add(signupButton, gbc);

        // New "Continue as Guest" button
        JButton guestButton = new JButton("Continue without login");
        guestButton.setFont(new Font("SansSerif", Font.PLAIN, 18));
        gbc.gridx = 2; // Position next to signup
        gbc.gridy = 2;
        add(guestButton, gbc);

        // Admin Login button
        JButton adminLoginButton = new JButton("Are you an admin?");
        adminLoginButton.setFont(new Font("SansSerif", Font.PLAIN, 18));
        gbc.gridx = 0;
        gbc.gridy = 3; // Move below other buttons
        gbc.gridwidth = 3; // Span across all columns
        add(adminLoginButton, gbc);


        // Action listeners for buttons
        loginButton.addActionListener(e -> {
            new LoginPage().setVisible(true); // Open login page
            dispose(); // Close welcome page
        });

        signupButton.addActionListener(e -> {
            new SignupPage().setVisible(true); // Open signup page
            dispose(); // Close welcome page
        });

        guestButton.addActionListener(e -> {
            UserManager.setCurrentUser(null); // Set current user to null for guest mode
            new MovieExplorerApp().setVisible(true); // Open main app as guest
            dispose(); // Close welcome page
        });

        adminLoginButton.addActionListener(e -> {
            new AdminLoginPage().setVisible(true); // Open admin login page
            dispose(); // Close welcome page
        });
    }
}

/**
 * Login Page: Allows existing users to log in to the application.
 */
class LoginPage extends AbstractAppFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;

    public LoginPage() {
        super("\uD83C\uDFAC Login to Movie Explorer");
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // Padding
        gbc.fill = GridBagConstraints.HORIZONTAL; // Components fill horizontally

        // Login label
        JLabel loginLabel = new JLabel("Login");
        loginLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        add(loginLabel, gbc);

        gbc.gridwidth = 1; // Reset gridwidth for subsequent components

        // Username field
        gbc.gridx = 0;
        gbc.gridy = 1;
        add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        usernameField = new JTextField(20);
        add(usernameField, gbc);

        // Password field
        gbc.gridx = 0;
        gbc.gridy = 2;
        add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        passwordField = new JPasswordField(20);
        add(passwordField, gbc);

        // Login button
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        JButton loginButton = new JButton("Login");
        add(loginButton, gbc);

        // Back button
        gbc.gridy = 4;
        JButton backButton = new JButton("Back to Welcome");
        add(backButton, gbc);

        // Action listener for login button
        loginButton.addActionListener(e -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());

            if (UserManager.authenticateUser(username, password)) {
                JOptionPane.showMessageDialog(this, "Login Successful! Welcome, " + UserManager.getCurrentUser().getUsername() + "!");
                new MovieExplorerApp().setVisible(true); // Open main app
                dispose(); // Close login page
            } else {
                JOptionPane.showMessageDialog(this, "Invalid Username or Password.", "Login Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Action listener for back button
        backButton.addActionListener(e -> {
            new WelcomePage().setVisible(true); // Go back to welcome page
            dispose(); // Close login page
        });
    }
}

/**
 * Signup Page: Allows new users to register for an account.
 */
class SignupPage extends AbstractAppFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JPasswordField confirmPasswordField;

    public SignupPage() {
        super("\uD83C\uDFAC Sign Up for Movie Explorer");
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Sign Up label
        JLabel signupLabel = new JLabel("Sign Up");
        signupLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        add(signupLabel, gbc);

        gbc.gridwidth = 1; // Reset gridwidth

        // Username field
        gbc.gridx = 0;
        gbc.gridy = 1;
        add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        usernameField = new JTextField(20);
        add(usernameField, gbc);

        // Password field
        gbc.gridx = 0;
        gbc.gridy = 2;
        add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        passwordField = new JPasswordField(20);
        add(passwordField, gbc);

        // Confirm Password field
        gbc.gridx = 0;
        gbc.gridy = 3;
        add(new JLabel("Confirm Password:"), gbc);
        gbc.gridx = 1;
        confirmPasswordField = new JPasswordField(20);
        add(confirmPasswordField, gbc);

        // Sign Up button
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        JButton signupButton = new JButton("Sign Up");
        add(signupButton, gbc);

        // Back button
        gbc.gridy = 5;
        JButton backButton = new JButton("Back to Welcome");
        add(backButton, gbc);

        // Action listener for signup button
        signupButton.addActionListener(e -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            String confirmPassword = new String(confirmPasswordField.getPassword());

            if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                JOptionPane.showMessageDialog(this, "All fields are required.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!password.equals(confirmPassword)) {
                JOptionPane.showMessageDialog(this, "Passwords do not match.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (UserManager.registerUser(username, password)) {
                JOptionPane.showMessageDialog(this, "Registration Successful! You can now log in.");
                new LoginPage().setVisible(true); // Go to login page after successful signup
                dispose(); // Close signup page
            } else {
                JOptionPane.showMessageDialog(this, "Username already exists.", "Registration Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Action listener for back button
        backButton.addActionListener(e -> {
            new WelcomePage().setVisible(true); // Go back to welcome page
            dispose(); // Close signup page
        });
    }
}

/**
 * User Profile Page: Allows logged-in users to manage their profile, specifically to change their password.
 */
class UserProfilePage extends AbstractAppFrame {
    private JPasswordField newPasswordField;
    private JPasswordField confirmNewPasswordField;
    private JLabel usernameLabel;

    public UserProfilePage() {
        super("\uD83C\uDFAC User Profile");
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Profile label
        JLabel profileLabel = new JLabel("User Profile");
        profileLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        add(profileLabel, gbc);

        gbc.gridwidth = 1;

        // Display current username
        gbc.gridx = 0;
        gbc.gridy = 1;
        add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        usernameLabel = new JLabel(UserManager.getCurrentUser().getUsername());
        usernameLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        add(usernameLabel, gbc);

        // New password fields
        gbc.gridx = 0;
        gbc.gridy = 2;
        add(new JLabel("New Password:"), gbc);
        gbc.gridx = 1;
        newPasswordField = new JPasswordField(20);
        add(newPasswordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        add(new JLabel("Confirm New Password:"), gbc);
        gbc.gridx = 1;
        confirmNewPasswordField = new JPasswordField(20);
        add(confirmNewPasswordField, gbc);

        // Change password button
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        JButton changePasswordButton = new JButton("Change Password");
        add(changePasswordButton, gbc);

        // Back button
        gbc.gridy = 5;
        JButton backButton = new JButton("Back to Main App");
        add(backButton, gbc);

        // Action listener for change password button
        changePasswordButton.addActionListener(e -> {
            String newPass = new String(newPasswordField.getPassword());
            String confirmNewPass = new String(confirmNewPasswordField.getPassword());

            if (newPass.isEmpty() || confirmNewPass.isEmpty()) {
                JOptionPane.showMessageDialog(this, "New password fields cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!newPass.equals(confirmNewPass)) {
                JOptionPane.showMessageDialog(this, "New passwords do not match.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (UserManager.updatePassword(UserManager.getCurrentUser().getUsername(), newPass)) {
                JOptionPane.showMessageDialog(this, "Password updated successfully!");
                newPasswordField.setText(""); // Clear fields after successful update
                confirmNewPasswordField.setText("");
            } else {
                JOptionPane.showMessageDialog(this, "Failed to update password.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Action listener for back button
        backButton.addActionListener(e -> {
            new MovieExplorerApp().setVisible(true); // Go back to the main app
            dispose();
        });
    }
}

/**
 * Admin Login Page: Allows an administrator to log in to the admin panel.
 */
class AdminLoginPage extends AbstractAppFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;

    public AdminLoginPage() {
        super("\uD83D\uDD12 Admin Login"); // Lock emoji for admin
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel loginLabel = new JLabel("Admin Login");
        loginLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        add(loginLabel, gbc);

        gbc.gridwidth = 1;

        gbc.gridx = 0;
        gbc.gridy = 1;
        add(new JLabel("Username:"), gbc);

        gbc.gridx = 1;
        usernameField = new JTextField(20);
        add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        add(new JLabel("Password:"), gbc);

        gbc.gridx = 1;
        passwordField = new JPasswordField(20);
        add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        JButton loginButton = new JButton("Login");
        add(loginButton, gbc);

        gbc.gridy = 4;
        JButton backButton = new JButton("Back to Welcome");
        add(backButton, gbc);

        loginButton.addActionListener(e -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());

            if (UserManager.isAdmin(username, password)) {
                JOptionPane.showMessageDialog(this, "Admin Login Successful!");
                new AdminPanel().setVisible(true); // Open admin panel
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Invalid Admin Credentials.", "Login Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        backButton.addActionListener(e -> {
            new WelcomePage().setVisible(true);
            dispose();
        });
    }
}

/**
 * Admin Profile Page: Allows the admin to change their own password.
 */
class AdminProfilePage extends AbstractAppFrame {
    private JPasswordField newPasswordField;
    private JPasswordField confirmNewPasswordField;
    private JLabel usernameLabel;

    public AdminProfilePage() {
        super("\uD83D\uDD12 Admin Password Change");
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("Change Admin Password");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        add(titleLabel, gbc);

        gbc.gridwidth = 1;

        gbc.gridx = 0;
        gbc.gridy = 1;
        add(new JLabel("Admin Username:"), gbc);
        gbc.gridx = 1;
        usernameLabel = new JLabel(UserManager.ADMIN_USERNAME); // Display admin username
        usernameLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        add(usernameLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        add(new JLabel("New Password:"), gbc);
        gbc.gridx = 1;
        newPasswordField = new JPasswordField(20);
        add(newPasswordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        add(new JLabel("Confirm New Password:"), gbc);
        gbc.gridx = 1;
        confirmNewPasswordField = new JPasswordField(20);
        add(confirmNewPasswordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        JButton changePasswordButton = new JButton("Change Password");
        add(changePasswordButton, gbc);

        gbc.gridy = 5;
        JButton backButton = new JButton("Back to Admin Panel");
        add(backButton, gbc);

        changePasswordButton.addActionListener(e -> {
            String newPass = new String(newPasswordField.getPassword());
            String confirmNewPass = new String(confirmNewPasswordField.getPassword());

            if (newPass.isEmpty() || confirmNewPass.isEmpty()) {
                JOptionPane.showMessageDialog(this, "New password fields cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!newPass.equals(confirmNewPass)) {
                JOptionPane.showMessageDialog(this, "New passwords do not match.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (UserManager.updateAdminPassword(newPass)) {
                JOptionPane.showMessageDialog(this, "Admin password updated successfully!");
                newPasswordField.setText("");
                confirmNewPasswordField.setText("");
            } else {
                JOptionPane.showMessageDialog(this, "Failed to update admin password.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        backButton.addActionListener(e -> {
            new AdminPanel().setVisible(true); // Go back to admin panel
            dispose();
        });
    }
}


/**
 * Admin Panel: Provides CRUD operations for users and reviews.
 */
class AdminPanel extends AbstractAppFrame {
    private DefaultTableModel userTableModel;
    private JTable userTable;
    private DefaultTableModel reviewTableModel;
    private JTable reviewTable;

    public AdminPanel() {
        super("\uD83D\uDD12 Admin Panel");
        setSize(1000, 700); // Larger size for admin panel
        setLayout(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();

        // --- User Management Tab ---
        JPanel userManagementPanel = new JPanel(new BorderLayout());
        String[] userColumnNames = {"Username", "Password"};
        userTableModel = new DefaultTableModel(userColumnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Make all cells non-editable directly in the table
                return false;
            }
        };
        userTable = new JTable(userTableModel);
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // Only allow single selection
        JScrollPane userScrollPane = new JScrollPane(userTable);
        userManagementPanel.add(userScrollPane, BorderLayout.CENTER);

        JPanel userButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton addUserButton = new JButton("Add User");
        JButton editUserButton = new JButton("Edit User (Change Password)");
        JButton deleteUserButton = new JButton("Delete User");
        JButton changeAdminPassButton = new JButton("Change Admin Password"); // New button

        userButtonPanel.add(addUserButton);
        userButtonPanel.add(editUserButton);
        userButtonPanel.add(deleteUserButton);
        userButtonPanel.add(changeAdminPassButton); // Add new button
        userManagementPanel.add(userButtonPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("User Management", userManagementPanel);

        // Action listeners for User Management
        addUserButton.addActionListener(e -> showAddUserDialog());
        editUserButton.addActionListener(e -> showEditUserDialog());
        deleteUserButton.addActionListener(e -> deleteSelectedUser());
        changeAdminPassButton.addActionListener(e -> { // Action for new button
            new AdminProfilePage().setVisible(true);
            dispose();
        });

        // --- Review Management Tab ---
        JPanel reviewManagementPanel = new JPanel(new BorderLayout());
        String[] reviewColumnNames = {"Movie ID", "Username", "Rating", "Review Text", "Timestamp"};
        reviewTableModel = new DefaultTableModel(reviewColumnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Allow editing Rating and Review Text directly in the table
                return column == 2 || column == 3; // Rating and Review Text columns
            }
        };
        reviewTable = new JTable(reviewTableModel);
        reviewTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane reviewScrollPane = new JScrollPane(reviewTable);
        reviewManagementPanel.add(reviewScrollPane, BorderLayout.CENTER);

        JPanel reviewButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton editReviewButton = new JButton("Edit Review");
        JButton deleteReviewButton = new JButton("Delete Review");

        reviewButtonPanel.add(editReviewButton);
        reviewButtonPanel.add(deleteReviewButton);
        reviewManagementPanel.add(reviewButtonPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("Review Management", reviewManagementPanel);

        // Action listeners for Review Management
        editReviewButton.addActionListener(e -> showEditReviewDialog());
        deleteReviewButton.addActionListener(e -> deleteSelectedReview());


        add(tabbedPane, BorderLayout.CENTER);

        // Back to Welcome button
        JButton backButton = new JButton("Back to Welcome");
        backButton.addActionListener(e -> {
            new WelcomePage().setVisible(true);
            dispose();
        });
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(backButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // Initial data load
        loadUserData();
        loadReviewData();
    }

    /**
     * Loads all user data into the user table model.
     */
    private void loadUserData() {
        userTableModel.setRowCount(0); // Clear existing data
        for (User user : UserManager.getAllUsers()) {
            userTableModel.addRow(new Object[]{user.getUsername(), user.getPassword()});
        }
    }

    /**
     * Displays a dialog to add a new user.
     */
    private void showAddUserDialog() {
        JTextField usernameField = new JTextField(15);
        JPasswordField passwordField = new JPasswordField(15);

        JPanel panel = new JPanel(new GridLayout(0, 2));
        panel.add(new JLabel("Username:"));
        panel.add(usernameField);
        panel.add(new JLabel("Password:"));
        panel.add(passwordField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add New User",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username and password cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (UserManager.registerUser(username, password)) {
                JOptionPane.showMessageDialog(this, "User added successfully.");
                loadUserData(); // Refresh table
            } else {
                JOptionPane.showMessageDialog(this, "Username already exists.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Displays a dialog to edit the password of a selected user.
     */
    private void showEditUserDialog() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a user to edit.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String username = (String) userTableModel.getValueAt(selectedRow, 0);
        JPasswordField newPasswordField = new JPasswordField(15);
        JPasswordField confirmPasswordField = new JPasswordField(15);

        JPanel panel = new JPanel(new GridLayout(0, 2));
        panel.add(new JLabel("Editing User:"));
        panel.add(new JLabel(username));
        panel.add(new JLabel("New Password:"));
        panel.add(newPasswordField);
        panel.add(new JLabel("Confirm New Password:"));
        panel.add(confirmPasswordField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit User Password",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String newPassword = new String(newPasswordField.getPassword());
            String confirmPassword = new String(confirmPasswordField.getPassword());

            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                JOptionPane.showMessageDialog(this, "New password fields cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                JOptionPane.showMessageDialog(this, "Passwords do not match.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (UserManager.updatePassword(username, newPassword)) {
                JOptionPane.showMessageDialog(this, "Password updated successfully.");
                loadUserData(); // Refresh table
            } else {
                JOptionPane.showMessageDialog(this, "Failed to update password for " + username, "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Deletes the selected user from the system.
     */
    private void deleteSelectedUser() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a user to delete.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String username = (String) userTableModel.getValueAt(selectedRow, 0);
        if (username.equals(UserManager.ADMIN_USERNAME)) {
            JOptionPane.showMessageDialog(this, "Cannot delete the admin user.", "Deletion Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete user: " + username + "?", "Confirm Deletion", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            if (UserManager.deleteUser(username)) {
                JOptionPane.showMessageDialog(this, "User " + username + " deleted successfully.");
                loadUserData(); // Refresh table
            } else {
                JOptionPane.showMessageDialog(this, "Failed to delete user " + username, "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Loads all review data into the review table model.
     */
    private void loadReviewData() {
        reviewTableModel.setRowCount(0); // Clear existing data
        for (Review review : ReviewManager.getAllReviews()) {
            reviewTableModel.addRow(new Object[]{
                    review.getMovieId(),
                    review.getUsername(),
                    review.getRating(),
                    review.getReviewText(),
                    review.getTimestamp() // Timestamp is hidden but used for unique identification
            });
        }
    }

    /**
     * Displays a dialog to edit the rating and text of a selected review.
     */
    private void showEditReviewDialog() {
        int selectedRow = reviewTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a review to edit.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int movieId = (int) reviewTableModel.getValueAt(selectedRow, 0);
        String username = (String) reviewTableModel.getValueAt(selectedRow, 1);
        int currentRating = (int) reviewTableModel.getValueAt(selectedRow, 2);
        String currentReviewText = (String) reviewTableModel.getValueAt(selectedRow, 3);
        long timestamp = (long) reviewTableModel.getValueAt(selectedRow, 4); // Get timestamp for identification

        SpinnerModel spinnerModel = new SpinnerNumberModel(currentRating, 1, 5, 1);
        JSpinner ratingSpinner = new JSpinner(spinnerModel);
        JTextArea reviewTextArea = new JTextArea(currentReviewText, 5, 20);
        reviewTextArea.setWrapStyleWord(true);
        reviewTextArea.setLineWrap(true);
        JScrollPane reviewScrollPane = new JScrollPane(reviewTextArea);

        JPanel panel = new JPanel(new GridLayout(0, 2));
        panel.add(new JLabel("Movie ID:"));
        panel.add(new JLabel(String.valueOf(movieId)));
        panel.add(new JLabel("Reviewer:"));
        panel.add(new JLabel(username));
        panel.add(new JLabel("New Rating (1-5):"));
        panel.add(ratingSpinner);
        panel.add(new JLabel("New Review Text:"));
        panel.add(reviewScrollPane);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Review",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            int newRating = (Integer) ratingSpinner.getValue();
            String newReviewText = reviewTextArea.getText().trim();

            if (newReviewText.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Review text cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (ReviewManager.updateReview(movieId, username, timestamp, newRating, newReviewText)) {
                JOptionPane.showMessageDialog(this, "Review updated successfully.");
                loadReviewData(); // Refresh table
            } else {
                JOptionPane.showMessageDialog(this, "Failed to update review.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Deletes the selected review from the system.
     */
    private void deleteSelectedReview() {
        int selectedRow = reviewTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a review to delete.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int movieId = (int) reviewTableModel.getValueAt(selectedRow, 0);
        String username = (String) reviewTableModel.getValueAt(selectedRow, 1);
        long timestamp = (long) reviewTableModel.getValueAt(selectedRow, 4); // Use timestamp for unique identification

        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this review?", "Confirm Deletion", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            if (ReviewManager.deleteReview(movieId, username, timestamp)) {
                JOptionPane.showMessageDialog(this, "Review deleted successfully.");
                loadReviewData(); // Refresh table
            } else {
                JOptionPane.showMessageDialog(this, "Failed to delete review.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}


// Interface for polymorphism (used for custom ListCellRenderer)
interface AbstractMovieRenderer extends ListCellRenderer<MovieInfo> {}

/**
 * Main Movie Explorer Application: Provides movie search, genre filtering,
 * random movie display, watchlist management, and rating/review functionality.
 */
public class MovieExplorerApp extends AbstractAppFrame {

    private JTextField searchField;
    private JComboBox<String> genreFilter;
    private JPanel resultPanel; // Panel to display movie search results
    private JScrollPane scrollPane;
    private DefaultListModel<MovieInfo> watchlistModel; // Model for the watchlist JList
    private JList<MovieInfo> watchlistUI; // UI component for the watchlist
    private JLabel welcomeMessageLabel; // Label to display welcome message

    /**
     * Constructs the main Movie Explorer application window.
     */
    public MovieExplorerApp() {
        super("\uD83C\uDFAC Movie Explorer"); // Call super constructor
        setSize(1200, 800); // Set specific size for the main app
        setMinimumSize(new Dimension(800, 600)); // Minimum size to ensure usability
        setLayout(new BorderLayout());

        // --- Top Panel: Search, Genre Filter, and User Actions ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT)); // Left-aligned flow layout
        searchField = new JTextField(30);
        JButton searchButton = new JButton("Search");
        JButton randomButton = new JButton("Random");

        genreFilter = new JComboBox<>();
        genreFilter.addItem("All Genres"); // Default option
        loadGenreMap(); // Load genres from TMDb API
        // Populate genre filter dropdown
        for (String genre : genreMap.values()) {
            genreFilter.addItem(genre);
        }

        topPanel.add(new JLabel("Search: "));
        topPanel.add(searchField);
        topPanel.add(searchButton);
        topPanel.add(genreFilter);
        topPanel.add(randomButton);

        // Add welcome message and profile/logout buttons if a user is logged in
        if (UserManager.getCurrentUser() != null) {
            welcomeMessageLabel = new JLabel("Welcome, " + UserManager.getCurrentUser().getUsername() + "!");
            welcomeMessageLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            topPanel.add(Box.createHorizontalStrut(20)); // Spacer for separation
            topPanel.add(welcomeMessageLabel);

            JButton profileButton = new JButton("Profile");
            profileButton.addActionListener(e -> {
                new UserProfilePage().setVisible(true); // Open profile page
                dispose(); // Close main app
            });
            topPanel.add(profileButton);

            JButton logoutButton = new JButton("Logout");
            logoutButton.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to logout?", "Confirm Logout", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    UserManager.setCurrentUser(null); // Clear current user
                    new WelcomePage().setVisible(true); // Go back to welcome page
                    dispose(); // Close main app
                }
            });
            topPanel.add(logoutButton);
        } else {
            // For guest users, display a guest message
            welcomeMessageLabel = new JLabel("You are browsing as Guest.");
            welcomeMessageLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
            topPanel.add(Box.createHorizontalStrut(20)); // Spacer
            topPanel.add(welcomeMessageLabel);
        }

        add(topPanel, BorderLayout.NORTH); // Add top panel to the north region of the frame

        // --- Main Content Area: Split Pane for Movie Results and Watchlist ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.75); // 75% for results, 25% for watchlist

        // Left component: Movie search results panel
        resultPanel = new JPanel();
        resultPanel.setLayout(new BoxLayout(resultPanel, BoxLayout.Y_AXIS)); // Vertical layout for movie panels
        scrollPane = new JScrollPane(resultPanel); // Add scrollability to results
        scrollPane.getVerticalScrollBar().setUnitIncrement(20); // Smooth scrolling
        splitPane.setLeftComponent(scrollPane);

        // Right component: Watchlist panel
        watchlistModel = new DefaultListModel<>(); // Data model for the watchlist JList
        watchlistUI = new JList<>(watchlistModel); // UI component for the watchlist
        watchlistUI.setCellRenderer(new WatchlistRenderer()); // Custom renderer for watchlist items
        loadWatchlist(); // Load watchlist from file on startup

        JScrollPane watchlistScroll = new JScrollPane(watchlistUI); // Add scrollability to watchlist
        watchlistScroll.setBorder(BorderFactory.createTitledBorder("Watchlist")); // Add a titled border
        watchlistScroll.setPreferredSize(new Dimension(300, getHeight())); // Set preferred width
        watchlistScroll.getVerticalScrollBar().setUnitIncrement(20);

        // Watchlist management buttons
        JButton deleteButton = new JButton("Remove Selected");
        deleteButton.addActionListener(e -> removeSelectedFromWatchlist());

        JButton clearAllButton = new JButton("Clear All");
        clearAllButton.addActionListener(e -> clearWatchlist());

        // Mouse listener for watchlist items: clicking an item searches for that movie
        watchlistUI.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 1) { // Single click
                    int index = watchlistUI.locationToIndex(evt.getPoint()); // Get index of clicked item
                    if (index >= 0) {
                        MovieInfo info = watchlistModel.get(index);
                        searchField.setText(info.getTitle()); // Set search field with movie title
                        fetchAndDisplayMovies(info.getTitle()); // Perform search
                    }
                }
            }
        });

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2)); // Buttons for watchlist management
        buttonPanel.add(deleteButton);
        buttonPanel.add(clearAllButton);

        JPanel watchlistContainer = new JPanel(new BorderLayout()); // Container for watchlist and buttons
        watchlistContainer.add(watchlistScroll, BorderLayout.CENTER);
        watchlistContainer.add(buttonPanel, BorderLayout.SOUTH);
        splitPane.setRightComponent(watchlistContainer);

        add(splitPane, BorderLayout.CENTER); // Add the split pane to the center of the frame

        // Action listener for search button
        searchButton.addActionListener(e -> {
            String query = searchField.getText().trim();
            if (!query.isEmpty()) {
                fetchAndDisplayMovies(query);
            }
        });

        // Action listener for random movie button
        randomButton.addActionListener(e -> fetchRandomMovie());
    }

    /**
     * Fetches movies from TMDb API based on a search query and selected genre, then displays them.
     * @param query The search term for movies.
     */
    private void fetchAndDisplayMovies(String query) {
        resultPanel.removeAll(); // Clear previous results
        resultPanel.revalidate(); // Revalidate layout
        resultPanel.repaint(); // Repaint component

        // Use SwingUtilities.invokeLater to ensure UI updates happen on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8"); // Encode query for URL
                String urlStr = "https://api.themoviedb.org/3/search/movie?api_key=" + apiKey + "&query=" + encoded;
                String response = readUrl(urlStr); // Fetch response from API
                JSONObject json = new JSONObject(response);
                JSONArray results = json.getJSONArray("results");

                String selectedGenre = (String) genreFilter.getSelectedItem(); // Get selected genre from dropdown

                if (results.length() == 0) {
                    resultPanel.add(new JLabel("No movies found for your search."));
                }

                // Iterate through search results and create movie panels
                for (int i = 0; i < results.length(); i++) {
                    JSONObject movie = results.getJSONObject(i);
                    // Skip if no poster path is available for a better visual experience
                    if (movie.isNull("poster_path")) continue;

                    // Filter by genre if "All Genres" is not selected
                    if (!"All Genres".equals(selectedGenre)) {
                        JSONArray movieGenreIds = movie.getJSONArray("genre_ids");
                        boolean match = false;
                        for (int j = 0; j < movieGenreIds.length(); j++) {
                            int genreId = movieGenreIds.getInt(j);
                            // Check if the movie's genre ID matches the selected genre name
                            if (genreMap.get(genreId) != null && genreMap.get(genreId).equals(selectedGenre)) {
                                match = true;
                                break;
                            }
                        }
                        if (!match) continue; // Skip if genre doesn't match
                    }

                    JPanel moviePanel = createMoviePanel(movie); // Create UI panel for the movie
                    resultPanel.add(moviePanel);
                    resultPanel.add(Box.createVerticalStrut(10)); // Add some vertical spacing between movies
                }

                resultPanel.revalidate();
                resultPanel.repaint();
                scrollPane.getVerticalScrollBar().setValue(0); // Scroll to top of results
            } catch (Exception e) {
                e.printStackTrace();
                resultPanel.add(new JLabel("Error fetching movies. Please try again."));
            }
        });
    }

    /**
     * Creates a JPanel for a single movie display, including poster, title, description,
     * trailer button, add to watchlist button, and rating/review functionality.
     * @param movie The JSONObject containing movie details from TMDb API.
     * @return A JPanel representing the movie.
     */
    private JPanel createMoviePanel(JSONObject movie) {
        JPanel panel = new JPanel(new BorderLayout(10, 10)); // BorderLayout with gaps
        panel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1)); // Border for visual separation
        panel.setBackground(Color.WHITE);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300)); // Ensure panel doesn't grow too large vertically

        try {
            String title = movie.getString("title");
            String posterPath = movie.getString("poster_path");
            int movieId = movie.getInt("id");
            String overview = movie.optString("overview", "No description available"); // Get overview, with fallback
            String imageUrl = "https://image.tmdb.org/t/p/w185" + posterPath; // Construct full poster URL

            // Image label for movie poster
            JLabel imgLabel = new JLabel();
            ImageIcon originalIcon = new ImageIcon(new URL(imageUrl));
            Image originalImage = originalIcon.getImage();

            // Scale image to a fixed width, maintaining aspect ratio
            int fixedWidth = 120;
            int scaledHeight = (int) ((double) fixedWidth / originalIcon.getIconWidth() * originalIcon.getIconHeight());
            Image scaledImage = originalImage.getScaledInstance(fixedWidth, scaledHeight, Image.SCALE_SMOOTH);
            imgLabel.setIcon(new ImageIcon(scaledImage));
            imgLabel.setPreferredSize(new Dimension(fixedWidth, scaledHeight));
            panel.add(imgLabel, BorderLayout.WEST); // Add image to the west (left)

            // Movie title label
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
            titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Movie description (overview) text area
            JTextArea descriptionArea = new JTextArea("Description:\n" + overview);
            descriptionArea.setWrapStyleWord(true); // Wrap words at line breaks
            descriptionArea.setLineWrap(true); // Enable line wrapping
            descriptionArea.setEditable(false); // Make it read-only
            descriptionArea.setOpaque(false); // Make background transparent
            descriptionArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
            descriptionArea.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Trailer button
            JButton trailerBtn = new JButton("\uD83C\uDF9E️ Trailer"); // Movie camera emoji
            trailerBtn.addActionListener(e -> openTrailer(movieId));

            // Add to Watchlist button
            JButton addToWatchlist = new JButton("+ Watchlist");
            addToWatchlist.addActionListener(e -> addMovieToWatchlist(new MovieInfo(movieId, title, imageUrl)));

            // Rating and Review button (only visible if a user is logged in)
            JButton rateReviewBtn = new JButton("Rate/Review");
            rateReviewBtn.addActionListener(e -> openRatingReviewDialog(movieId, title));

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT)); // Panel for action buttons
            buttonPanel.add(trailerBtn);
            buttonPanel.add(addToWatchlist);
            // Only allow rating/review if a user is logged in
            if (UserManager.getCurrentUser() != null) {
                buttonPanel.add(rateReviewBtn);
            }

            // Text content panel (title, description, buttons)
            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS)); // Vertical box layout
            textPanel.add(titleLabel);
            textPanel.add(Box.createVerticalStrut(5)); // Spacer
            textPanel.add(descriptionArea);
            textPanel.add(Box.createVerticalStrut(10)); // Spacer
            textPanel.add(buttonPanel);

            // Display existing reviews for the movie
            List<Review> reviews = ReviewManager.getReviewsForMovie(movieId);
            if (!reviews.isEmpty()) {
                JPanel reviewsPanel = new JPanel();
                reviewsPanel.setLayout(new BoxLayout(reviewsPanel, BoxLayout.Y_AXIS));
                reviewsPanel.setBorder(BorderFactory.createTitledBorder("User Reviews")); // Titled border for reviews
                reviewsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

                for (Review review : reviews) {
                    // Display each review with HTML for rich text formatting
                    JLabel reviewLabel = new JLabel(
                            "<html><b>" + review.getUsername() + "</b> rated: " + review.getRating() + "/5<br>" +
                                    review.getReviewText() + "</html>"
                    );
                    reviewLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5)); // Padding for review text
                    reviewsPanel.add(reviewLabel);
                }
                textPanel.add(Box.createVerticalStrut(10)); // Spacer
                textPanel.add(reviewsPanel);
            }

            panel.add(textPanel, BorderLayout.CENTER); // Add text content to the center
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback for image loading or other errors
            panel.add(new JLabel("Error loading movie details."), BorderLayout.CENTER);
        }

        return panel;
    }

    /**
     * Opens a dialog for users to rate and review a movie.
     * @param movieId The ID of the movie to rate/review.
     * @param movieTitle The title of the movie.
     */
    private void openRatingReviewDialog(int movieId, String movieTitle) {
        // Ensure a user is logged in before allowing rating/review
        if (UserManager.getCurrentUser() == null) {
            JOptionPane.showMessageDialog(this, "You must be logged in to rate and review movies.", "Login Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "Rate & Review: " + movieTitle, true); // Modal dialog
        dialog.setLayout(new GridBagLayout());
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this); // Center dialog relative to main frame

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Rating spinner (1-5 stars)
        JLabel ratingLabel = new JLabel("Rating (1-5):");
        gbc.gridx = 0;
        gbc.gridy = 0;
        dialog.add(ratingLabel, gbc);

        SpinnerModel spinnerModel = new SpinnerNumberModel(3, 1, 5, 1); // Default 3, min 1, max 5, step 1
        JSpinner ratingSpinner = new JSpinner(spinnerModel);
        gbc.gridx = 1;
        gbc.gridy = 0;
        dialog.add(ratingSpinner, gbc);

        // Review text area
        JLabel reviewLabel = new JLabel("Your Review:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        dialog.add(reviewLabel, gbc);

        JTextArea reviewTextArea = new JTextArea(5, 20);
        reviewTextArea.setWrapStyleWord(true);
        reviewTextArea.setLineWrap(true);
        JScrollPane reviewScrollPane = new JScrollPane(reviewTextArea); // Add scrollability to text area
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH; // Fill both horizontally and vertically
        gbc.weightx = 1.0; // Allow horizontal expansion
        gbc.weighty = 1.0; // Allow vertical expansion
        dialog.add(reviewScrollPane, gbc);

        // Submit button
        JButton submitButton = new JButton("Submit Review");
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0; // Do not expand vertically
        submitButton.addActionListener(e -> {
            int rating = (Integer) ratingSpinner.getValue();
            String reviewText = reviewTextArea.getText().trim();
            String username = UserManager.getCurrentUser().getUsername();

            if (reviewText.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Review text cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Review review = new Review(movieId, username, rating, reviewText);
            ReviewManager.addReview(review); // Add the new review
            JOptionPane.showMessageDialog(dialog, "Review submitted successfully!");
            dialog.dispose(); // Close the dialog
            // Refresh the movie display to show the newly added review
            fetchAndDisplayMovies(searchField.getText().trim());
        });
        dialog.add(submitButton, gbc);

        dialog.setVisible(true); // Make the dialog visible
    }

    /**
     * Custom renderer for the watchlist JList.
     * Displays movie poster and title for each watchlist item.
     */
    private class WatchlistRenderer extends JPanel implements AbstractMovieRenderer {
        private JLabel imageLabel = new JLabel();
        private JLabel titleLabel = new JLabel();

        public WatchlistRenderer() {
            setLayout(new BorderLayout(5, 5)); // BorderLayout with small gaps
            add(imageLabel, BorderLayout.WEST); // Image on the left
            add(titleLabel, BorderLayout.CENTER); // Title in the center
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5)); // Padding
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends MovieInfo> list, MovieInfo value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            try {
                ImageIcon originalIcon = new ImageIcon(new URL(value.getPosterUrl()));
                Image image = originalIcon.getImage();
                int width = 60; // Fixed width for watchlist poster
                // Calculate height to maintain aspect ratio
                int height = (int) ((double) originalIcon.getIconHeight() / originalIcon.getIconWidth() * width);
                imageLabel.setIcon(new ImageIcon(image.getScaledInstance(width, height, Image.SCALE_SMOOTH)));
            } catch (Exception e) {
                imageLabel.setIcon(null); // Clear image on error or if URL is bad
                System.err.println("Error loading watchlist image for " + value.getTitle() + ": " + e.getMessage());
            }
            titleLabel.setText(value.getTitle()); // Set movie title

            // Set background and foreground for selected items to provide visual feedback
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            return this;
        }
    }

    /**
     * Opens the trailer for a given movie ID in the default web browser.
     * Fetches trailer links from TMDb API.
     * @param movieId The ID of the movie whose trailer is to be opened.
     */
    private void openTrailer(int movieId) {
        try {
            String urlStr = "https://api.themoviedb.org/3/movie/" + movieId + "/videos?api_key=" + apiKey;
            String response = readUrl(urlStr); // Fetch video data
            JSONObject json = new JSONObject(response);
            JSONArray results = json.getJSONArray("results");
            for (int i = 0; i < results.length(); i++) {
                JSONObject video = results.getJSONObject(i);
                // Look for a YouTube trailer
                if ("Trailer".equalsIgnoreCase(video.getString("type")) && "YouTube".equalsIgnoreCase(video.getString("site"))) {
                    String youtubeKey = video.getString("key");
                    Desktop.getDesktop().browse(new URI("https://www.youtube.com/watch?v=" + youtubeKey)); // Open in browser
                    return; // Exit after finding the first trailer
                }
            }
            JOptionPane.showMessageDialog(this, "Trailer not available for this movie.");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error opening trailer.");
        }
    }

    /**
     * Adds a movie to the watchlist if it's not already present.
     * @param movieInfo The MovieInfo object to add.
     */
    private void addMovieToWatchlist(MovieInfo movieInfo) {
        // Check for duplicates before adding
        for (int i = 0; i < watchlistModel.size(); i++) {
            if (watchlistModel.get(i).getId() == movieInfo.getId()) {
                JOptionPane.showMessageDialog(this, "Movie is already in your watchlist.");
                return;
            }
        }
        watchlistModel.addElement(movieInfo); // Add movie to the list model
        saveWatchlist(); // Save watchlist to file after adding
        JOptionPane.showMessageDialog(this, movieInfo.getTitle() + " added to watchlist.");
    }

    /**
     * Removes the currently selected movie from the watchlist.
     */
    private void removeSelectedFromWatchlist() {
        int selectedIndex = watchlistUI.getSelectedIndex();
        if (selectedIndex >= 0) { // Check if an item is selected
            MovieInfo removedMovie = watchlistModel.remove(selectedIndex); // Remove from model
            saveWatchlist(); // Save watchlist to file after removing
            JOptionPane.showMessageDialog(this, removedMovie.getTitle() + " removed from watchlist.");
        } else {
            JOptionPane.showMessageDialog(this, "Please select a movie to remove.");
        }
    }

    /**
     * Clears all movies from the watchlist after user confirmation.
     */
    private void clearWatchlist() {
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to clear your entire watchlist?", "Confirm Clear", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            watchlistModel.clear(); // Clear all items from the model
            saveWatchlist(); // Save empty watchlist
            JOptionPane.showMessageDialog(this, "Watchlist cleared.");
        }
    }

    /**
     * Saves the current watchlist to a text file.
     * The filename is user-specific (or guest-specific).
     */
    private void saveWatchlist() {
        // Watchlist is saved per user. If no user is logged in (guest), save to a generic file.
        String filename = (UserManager.getCurrentUser() != null) ?
                "watchlist_" + UserManager.getCurrentUser().getUsername() + ".txt" : "watchlist_guest.txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (int i = 0; i < watchlistModel.size(); i++) {
                MovieInfo movie = watchlistModel.get(i);
                // Write movie details separated by "||"
                writer.write(movie.getId() + "||" + movie.getTitle() + "||" + movie.getPosterUrl());
                writer.newLine(); // New line for each movie
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving watchlist.", "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Loads the watchlist from a text file.
     * The filename is user-specific (or guest-specific).
     */
    private void loadWatchlist() {
        // Watchlist is loaded per user. If no user is logged in (guest), load from a generic file.
        String filename = (UserManager.getCurrentUser() != null) ?
                "watchlist_" + UserManager.getCurrentUser().getUsername() + ".txt" : "watchlist_guest.txt";
        File file = new File(filename);
        if (!file.exists()) {
            watchlistModel.clear(); // Clear existing if file doesn't exist for this user/guest
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath()); // Read all lines from the file
            watchlistModel.clear(); // Clear current watchlist before loading to prevent duplicates
            for (String line : lines) {
                String[] parts = line.split("\\|\\|"); // Split line by "||" delimiter
                if (parts.length == 3) { // Ensure correct format
                    try {
                        int id = Integer.parseInt(parts[0]);
                        String title = parts[1];
                        String poster = parts[2];
                        watchlistModel.addElement(new MovieInfo(id, title, poster)); // Add to model
                    } catch (NumberFormatException e) {
                        System.err.println("Skipping malformed watchlist entry: " + line);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading watchlist.", "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Fetches and displays a random movie from TMDb API.
     */
    private void fetchRandomMovie() {
        resultPanel.removeAll(); // Clear previous results
        // Use SwingUtilities.invokeLater for UI updates
        SwingUtilities.invokeLater(() -> {
            try {
                // Fetch a random page from 1 to 50 for more variety in random movies
                int page = new Random().nextInt(50) + 1;
                String urlStr = "https://api.themoviedb.org/3/discover/movie?api_key=" + apiKey + "&page=" + page;
                String response = readUrl(urlStr);
                JSONObject json = new JSONObject(response);
                JSONArray results = json.getJSONArray("results");
                if (results.length() > 0) {
                    // Select a random movie from the results
                    int randIndex = new Random().nextInt(results.length());
                    JSONObject movie = results.getJSONObject(randIndex);
                    // Ensure the random movie has a poster for proper display
                    if (!movie.isNull("poster_path")) {
                        JPanel panel = createMoviePanel(movie);
                        resultPanel.add(panel);
                        resultPanel.revalidate();
                        resultPanel.repaint();
                    } else {
                        // If selected random movie has no poster, try fetching another random one
                        fetchRandomMovie(); // Recursive call to get another random movie
                    }
                } else {
                    resultPanel.add(new JLabel("No random movies found."));
                }
            } catch (Exception e) {
                e.printStackTrace();
                resultPanel.add(new JLabel("Error fetching random movie."));
            }
        });
    }

    /**
     * Main method to start the application.
     * Ensures data files exist and then launches the WelcomePage.
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        // Ensure the users and reviews files exist or are created.
        // This creates empty files if they don't exist, which are then handled by load methods.
        try {
            Files.createDirectories(Paths.get(".")); // Ensure current directory is writable
            new File(UserManager.USERS_FILE).createNewFile();
            new File(ReviewManager.REVIEWS_FILE).createNewFile();
        } catch (IOException e) {
            System.err.println("Could not create data files: " + e.getMessage());
        }

        // Launch the Swing application on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> new WelcomePage().setVisible(true));
    }
}

/**
 * Encapsulated MovieInfo class to hold basic movie details.
 * Implements Serializable for persistence in the watchlist.
 */
class MovieInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private String title;
    private String posterUrl;

    /**
     * Constructs a new MovieInfo object.
     * @param id The TMDb ID of the movie.
     * @param title The title of the movie.
     * @param posterUrl The URL of the movie's poster image.
     */
    public MovieInfo(int id, String title, String posterUrl) {
        this.id = id;
        this.title = title;
        this.posterUrl = posterUrl;
    }

    // Getter and setter methods for movie properties
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    /**
     * Returns the title of the movie as its string representation.
     * Useful for displaying in JList or similar components.
     * @return The movie title.
     */
    @Override
    public String toString() {
        return title;
    }
}
