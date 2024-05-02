package com.pms.Reports;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;


@WebServlet(name = "GenRepDuration", value = "/GenRepDuration")
public class GenRepDuration extends HttpServlet {

    private static Properties getConnectionData() {
        Properties props = new Properties();
        String fileName = "db.properties";

        try (InputStream in = GenRepDuration.class.getClassLoader().getResourceAsStream(fileName)) {
            props.load(in);
        } catch (IOException ex) {
            Logger lgr = Logger.getLogger(GenRepDuration.class.getName());
            lgr.log(Level.SEVERE, ex.getMessage(), ex);
        }
        return props;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String[] selectedYears = request.getParameterValues("selectedItems[]");

        if (selectedYears != null) {
            try {
                JsonArray reportData = generateDurationReport(selectedYears);
                JsonObject responseObject = new JsonObject();
                responseObject.add("reportData", reportData);
                String finalResponse = responseObject.toString();

                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(finalResponse);
            } catch (Exception ex) {
                ex.printStackTrace(); // Log the exception for debugging
                response.getWriter().write("Error: " + ex.getMessage());
            }
        } else {
            response.getWriter().write("Invalid request parameters.");
        }
    }

    private JsonArray generateDurationReport(String[] years) {
        JsonArray reportDataArray = new JsonArray();

        Properties props = getConnectionData();
        String url = props.getProperty("db.url");
        String user = props.getProperty("db.user");
        String passwd = props.getProperty("db.passwd");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        try (Connection con = DriverManager.getConnection(url, user, passwd)) {
            StringBuilder queryBuilder = new StringBuilder("SELECT * FROM Accepted WHERE Duration IN (");
            for (int i = 0; i < years.length; i++) {
                queryBuilder.append("?");
                if (i < years.length - 1) {
                    queryBuilder.append(",");
                }
            }
            queryBuilder.append(")");
            String query = queryBuilder.toString();
            System.out.println(query);

            try (PreparedStatement preparedStatement = con.prepareStatement(query)) {
                for (int i = 0; i < years.length; i++) {
                    int year = Integer.parseInt(years[i]);
                    preparedStatement.setInt(i + 1, year);
                }
                ResultSet resultSet = preparedStatement.executeQuery();

                if (!resultSet.isBeforeFirst()) {
                    // Result set is empty
                    JsonObject reportObject = new JsonObject();
                    reportObject.addProperty("mean", 0.0);
                    reportObject.addProperty("mode", 0.0);
                    reportObject.addProperty("median", 0.0);
                    reportObject.addProperty("min", 0.0);
                    reportObject.addProperty("max", 0.0);
                    reportDataArray.add(reportObject);

                    return reportDataArray;
                }

                List<Double> ctcValues = new ArrayList<>(); // Store CTC values
                while (resultSet.next()) {
                    double ctc = resultSet.getDouble("CTC");
                    ctcValues.add(ctc);
                }

                System.out.println(ctcValues.size());


                JsonObject reportObject = new JsonObject();
                reportObject.addProperty("mean", calculateMean(ctcValues));
                reportObject.addProperty("mode", calculateMode(ctcValues));
                reportObject.addProperty("median", calculateMedian(ctcValues));
                reportObject.addProperty("min", Collections.min(ctcValues));
                reportObject.addProperty("max", Collections.max(ctcValues));
                reportDataArray.add(reportObject);
            }catch (SQLException ex) {
                System.err.println("SQL Exception: " + ex.getMessage());
                System.err.println("SQL State: " + ex.getSQLState());
                System.err.println("Error Code: " + ex.getErrorCode());
                ex.printStackTrace();
            }
        } catch (SQLException ex) {
            ex.printStackTrace(); // Log the exception for debugging
        }

        return reportDataArray;
    }

    private double calculateMean(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private double calculateMode(List<Double> values) {
        Map<Double, Integer> frequencyMap = new HashMap<>();
        for (double value : values) {
            frequencyMap.put(value, frequencyMap.getOrDefault(value, 0) + 1);
        }

        double mode = 0.0;
        int maxFrequency = 0;
        for (Map.Entry<Double, Integer> entry : frequencyMap.entrySet()) {
            if (entry.getValue() > maxFrequency) {
                mode = entry.getKey();
                maxFrequency = entry.getValue();
            }
        }
        return mode;
    }

    private double calculateMedian(List<Double> packages) {
        if (packages.isEmpty()) {
            return 0.0; // Handle empty list
        }

        Collections.sort(packages); // Sort the list in ascending order

        int size = packages.size();
        int middle = size / 2;

        if (size % 2 == 0) {
            // Even number of elements, take the average of the middle two
            return (packages.get(middle - 1) + packages.get(middle)) / 2.0;
        } else {
            // Odd number of elements, return the middle element
            return packages.get(middle);
        }
    }
}


