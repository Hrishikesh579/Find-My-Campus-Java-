import javax.swing.*;
import java.awt.*;
import java.sql.*;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

public class FindMyCampus {
    private JFrame frame;
    private JTextField currentLocationBox;
    private JTextField destinationBox;
    private JTextArea routeInfo;
    private Connection connection;
    private JFXPanel mapPanel;

    public FindMyCampus() {
        initializeGUI();
        connectToDatabase();
    }

    private void initializeGUI() {
        frame = new JFrame("Find My Campus");
        frame.setSize(900, 700);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JLabel header = new JLabel("Find My Campus", JLabel.CENTER);
        header.setFont(new Font("Arial", Font.BOLD, 36));
        header.setOpaque(true);
        header.setBackground(new Color(0, 102, 204));
        header.setForeground(Color.BLUE);
        header.setPreferredSize(new Dimension(900, 60));
        frame.add(header, BorderLayout.NORTH);

        JPanel searchPanel = new JPanel();
        searchPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 10));
        currentLocationBox = new JTextField(15);
        destinationBox = new JTextField(15);
        JButton routeButton = new JButton("Find Route");
        routeButton.setFont(new Font("Arial", Font.BOLD, 16));
        routeButton.setBackground(new Color(34, 139, 34));
        routeButton.setForeground(Color.WHITE);
        routeButton.setPreferredSize(new Dimension(120, 30));
        routeButton.addActionListener(e -> findRoute());
        searchPanel.add(new JLabel("Current Location:"));
        searchPanel.add(currentLocationBox);
        searchPanel.add(new JLabel("Destination:"));
        searchPanel.add(destinationBox);
        searchPanel.add(routeButton);
        frame.add(searchPanel, BorderLayout.NORTH);

        routeInfo = new JTextArea("Enter locations and click Find Route.");
        routeInfo.setFont(new Font("Arial", Font.PLAIN, 16));
        routeInfo.setLineWrap(true);
        routeInfo.setWrapStyleWord(true);
        routeInfo.setEditable(false);
        routeInfo.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        JScrollPane scrollPane = new JScrollPane(routeInfo);
        scrollPane.setPreferredSize(new Dimension(880, 150));
        frame.add(scrollPane, BorderLayout.SOUTH);

        mapPanel = new JFXPanel();
        mapPanel.setPreferredSize(new Dimension(880, 400));
        mapPanel.setLayout(new BorderLayout());
        frame.add(mapPanel, BorderLayout.CENTER);

        Platform.runLater(this::loadMap);

        frame.setVisible(true);
    }

    private void connectToDatabase() {
        try {
            String url = "jdbc:mysql://localhost:3306/campusdb";
            String user = "root";
            String password = "hrishikesh@24680";
            connection = DriverManager.getConnection(url, user, password);
            System.out.println("Database connected successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Database connection failed.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadMap() {
        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();

        String mapHTML = """
                <!DOCTYPE html>
                <html>
                <head>
                    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                    <script src="https://unpkg.com/leaflet-routing-machine@3.2.12/dist/leaflet-routing-machine.js"></script>
                    <style>
                        #map { height: 100%; width: 100%; position: absolute; top: 0; left: 0; }
                        html, body { margin: 0; padding: 0; height: 100%; width: 100%; overflow: hidden; }
                    </style>
                </head>
                <body>
                    <div id="map"></div>
                    <script>
                        var map = L.map('map').setView([12.82366520149584, 80.04131195769666], 15);
                        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                            attribution: '© OpenStreetMap contributors'
                        }).addTo(map);

                        var control = L.Routing.control({
                            waypoints: [],
                            routeWhileDragging: true,
                            createMarker: function(i, wp) {
                                return L.marker(wp.latLng, { draggable: true });
                            }
                        }).addTo(map);

                        function setRoute(startLat, startLng, endLat, endLng) {
                            control.setWaypoints([
                                L.latLng(startLat, startLng),
                                L.latLng(endLat, endLng)
                            ]);
                        }
                    </script>
                </body>
                </html>
                """;

        webEngine.loadContent(mapHTML);
        webEngine.setOnError(event -> System.out.println("WebEngine error: " + event.getMessage()));
        mapPanel.setScene(new Scene(webView));
    }

    private void findRoute() {
        String currentLocation = currentLocationBox.getText().trim();
        String destination = destinationBox.getText().trim();

        if (currentLocation.isEmpty() || destination.isEmpty()) {
            routeInfo.setText("Please enter both current location and destination.");
            return;
        }

        String query = "SELECT * FROM buildings WHERE name LIKE ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            // Retrieve current location coordinates
            stmt.setString(1, "%" + currentLocation + "%");
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                routeInfo.setText("Current location not found.");
                return;
            }
            double startLat = rs.getDouble("latitude");
            double startLng = rs.getDouble("longitude");

            // Retrieve destination coordinates
            stmt.setString(1, "%" + destination + "%");
            rs = stmt.executeQuery();
            if (!rs.next()) {
                routeInfo.setText("Destination not found.");
                return;
            }
            double endLat = rs.getDouble("latitude");
            double endLng = rs.getDouble("longitude");

            routeInfo.setText(String.format("Route from %s to %s:\nStart: %.6f, %.6f\nEnd: %.6f, %.6f",
                    currentLocation, destination, startLat, startLng, endLat, endLng));

            Platform.runLater(() -> updateMapRoute(startLat, startLng, endLat, endLng));
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error fetching data.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateMapRoute(double startLat, double startLng, double endLat, double endLng) {
        WebView webView = (WebView) mapPanel.getScene().getRoot();
        WebEngine webEngine = webView.getEngine();
        String script = String.format("setRoute(%f, %f, %f, %f);", startLat, startLng, endLat, endLng);
        webEngine.executeScript(script);
    }

    public static void main(String[] args) {
        Platform.startup(() -> SwingUtilities.invokeLater(FindMyCampus::new));
    }
}
