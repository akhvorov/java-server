package ru.ifmo.java.server;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.*;
import java.util.List;

public class TestServerGUI {
    private static final List<String> metrics = Arrays.asList(Constants.Metric.CLIENT, Constants.Metric.RESPONSE, Constants.Metric.HANDLE);

    private static final JButton runButton = new JButton("Run");
    private static final JFXPanel chartPanelFX = new JFXPanel();
    private static final List<List<XYChart.Series<Number, Number>>> data = Arrays.asList(
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    private static final XYChart<Number, Number> chart = new LineChart<>(new NumberAxis(), new NumberAxis());

    private static int curChart = 0;


    public static void main(String[] args) {
        JFrame frame = new JFrame("Server architectures benchmark");

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new GridBagLayout());

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        JPanel serverChoosePanel = new JPanel();
        serverChoosePanel.setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();

        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;

        constraints.insets = new Insets(10, 0, 10, 10);
        constraints.gridwidth = 1;

        constraints.gridy = 0;
        serverChoosePanel.add(new JLabel("Array size N:", SwingConstants.RIGHT), constraints);

        constraints.gridy = 1;
        serverChoosePanel.add(new JLabel("Clients num M:", SwingConstants.RIGHT), constraints);

        constraints.gridy = 2;
        serverChoosePanel.add(new JLabel("Requests num X:", SwingConstants.RIGHT), constraints);

        constraints.gridy = 3;
        serverChoosePanel.add(new JLabel("Delay:", SwingConstants.RIGHT), constraints);


        constraints.gridx = 1;

        JTextField nVal = new JTextField("0", 10);
        constraints.gridy = 0;
        serverChoosePanel.add(nVal, constraints);

        JTextField mVal = new JTextField("4", 10);
        constraints.gridy = 1;
        serverChoosePanel.add(mVal, constraints);

        JTextField xVal = new JTextField("5", 10);
        constraints.gridy = 2;
        serverChoosePanel.add(xVal, constraints);

        JTextField delayVal = new JTextField("1", 10);
        constraints.gridy = 3;
        serverChoosePanel.add(delayVal, constraints);

        constraints.insets = new Insets(10, 30, 10, 10);
        constraints.gridx = 2;

        constraints.gridy = 0;
        serverChoosePanel.add(new JLabel("Change param:", SwingConstants.RIGHT), constraints);

        constraints.gridy = 1;
        serverChoosePanel.add(new JLabel("Range:", SwingConstants.RIGHT), constraints);

        constraints.gridy = 2;
        serverChoosePanel.add(new JLabel("Step:", SwingConstants.RIGHT), constraints);

        constraints.gridy = 3;
        serverChoosePanel.add(new JLabel("Server IP:", SwingConstants.RIGHT), constraints);


        constraints.gridx = 3;
        constraints.insets = new Insets(10, 0, 10, 10);

        JComboBox<String> paramsCombo = new JComboBox<>(new String[]{"N", "M", "X", "Delay"});
        constraints.gridy = 0;
        serverChoosePanel.add(paramsCombo, constraints);

        constraints.gridy = 1;
        JPanel rangePanel = new JPanel();
        JTextField minRangeVal = new JTextField("100", 5);
        rangePanel.add(minRangeVal);
        rangePanel.add(new JLabel("-"));
        JTextField maxRangeVal = new JTextField("51000", 5);
        rangePanel.add(maxRangeVal);
        serverChoosePanel.add(rangePanel, constraints);

        JTextField stepVal = new JTextField("5000", 10);
        constraints.gridy = 2;
        serverChoosePanel.add(stepVal, constraints);

        constraints.gridy = 3;
        JTextField ipVal = new JTextField("localhost", 10);
        serverChoosePanel.add(ipVal, constraints);

        constraints.gridy = 4;
        constraints.gridx = 0;
        constraints.gridwidth = 4;
        constraints.fill = GridBagConstraints.NONE;


        constraints.gridy = 6;
        serverChoosePanel.add(runButton, constraints);

        topPanel.add(serverChoosePanel);
        JPanel chartPanel = new JPanel();
        chartPanel.setLayout(new BoxLayout(chartPanel, BoxLayout.Y_AXIS));
        chartPanelFX.setScene(new Scene(chart));
        chartPanelFX.setPreferredSize(new Dimension(1000, 600));
        chartPanel.add(chartPanelFX);

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        JButton prevButton = new JButton("<");
        JButton nextButton = new JButton(">");

        prevButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        nextButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        buttons.add(prevButton);
        buttons.add(nextButton);

        chartPanel.add(buttons);
        topPanel.add(chartPanel);

        frame.add(topPanel);

        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();

        nVal.setEnabled(false);
        Map<String, JTextField> varFields = new HashMap<>();
        varFields.put("N", nVal);
        varFields.put("M", mVal);
        varFields.put("X", xVal);
        varFields.put("Delay", delayVal);
        paramsCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                varFields.get(e.getItem()).setEnabled(false);
            }
            if (e.getStateChange() == ItemEvent.DESELECTED) {
                varFields.get(e.getItem()).setEnabled(true);
            }
        });

        runButton.addActionListener(e -> {
            runButton.setEnabled(false);
            int n = Integer.parseInt(nVal.getText());
            int m = Integer.parseInt(mVal.getText());
            int x = Integer.parseInt(xVal.getText());
            int delay = Integer.parseInt(delayVal.getText());
            int from = Integer.parseInt(minRangeVal.getText());
            int to = Integer.parseInt(maxRangeVal.getText());
            int step = Integer.parseInt(stepVal.getText());
            String change = (String) paramsCombo.getSelectedItem();
            new Thread(() -> runTest(n, m, x, delay, change, from, to, step, ipVal.getText())).start();
        });

        chart.setAnimated(false);
        nextButton.addActionListener(e -> {
            curChart = (curChart + 1) % data.size();
            Platform.runLater(TestServerGUI::updateChart);
        });

        prevButton.addActionListener(e -> {
            curChart = (curChart - 1 + data.size()) % data.size();
            Platform.runLater(TestServerGUI::updateChart);
        });
    }

    private static void runTest(int n, int m, int x, int delay, String changeName, int from, int to,
                                int step, String serverIp) {
        for (List<XYChart.Series<Number, Number>> aData : data) {
            aData.clear();
        }

        List<String> metrics = Arrays.asList(Constants.Metric.CLIENT, Constants.Metric.RESPONSE, Constants.Metric.HANDLE);
        ClientsRunner clientsRunner = new ClientsRunner();
        for (String serverType : Arrays.asList(
                Constants.ServerTypes.BLOCKING,
                Constants.ServerTypes.NON_BLOCKING,
                Constants.ServerTypes.ASYNCHRONOUS
                )
        ) {
            Map<Integer, Map<String, Double>> res = clientsRunner.runCompare(serverType, m, x, n, delay, changeName, from, to, step, serverIp);

            for (int i = 0; i < metrics.size(); i++) {
                List<XYChart.Data<Integer, Double>> dataset = new ArrayList<>();
                List<Integer> vals = new ArrayList<>(res.keySet());
                vals.sort(null);
                for (int val : vals) {
                    dataset.add(new XYChart.Data<>(val, res.get(val).get(metrics.get(i))));
                }
                data.get(i).add(new XYChart.Series(serverType, FXCollections.observableArrayList(dataset)));
            }
        }

        Platform.runLater(() -> {
            chart.getXAxis().setLabel(changeName);
            chart.getYAxis().setLabel("ms");
            updateChart();
        });
        SwingUtilities.invokeLater(() -> runButton.setEnabled(true));
    }

    private static void updateChart() {
        chart.setTitle(metrics.get(curChart));
        chart.setData(FXCollections.observableArrayList(data.get(curChart)));
    }
}