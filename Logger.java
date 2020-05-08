import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.bulenkov.darcula.DarculaLaf;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Logger extends javax.swing.JFrame {
    public static final File ConfigFile = new File("config.xml");
    private static class FTP_Data {
        public static String Host;
        public static int Port;
        public static FTPClient Client;
        public static FTPReply Reply;

        private static class Credentials {
            public static String Name;
            public static String Password;
        }

        private static boolean Connect() {
            boolean returnVal = false;
            try {
                if (FTP_Data.Client != null) FTP_Data.Client.disconnect();
                FTP_Data.Client = new FTPClient();
                FTP_Data.Client.connect(FTP_Data.Host, FTP_Data.Port);
                FTP_Data.Client.setConnectTimeout(5000);
                FTP_Data.Client.setAutodetectUTF8(true);
                //FTP.Client.setCharset(CharsetUtil.UTF_8);
                FTP_Data.Client.enterLocalPassiveMode();
                FTP_Data.Client.setFileType(FTP.BINARY_FILE_TYPE);
                FTP_Data.Client.login(FTP_Data.Credentials.Name, FTP_Data.Credentials.Password);
                if (FTP_Data.Client.isConnected()) {
                    returnVal = true;
                    System.out.println("[INFO] Connected to FTP server!");
                }
            } catch (Exception e) {
                System.out.println("[ERROR] " + e.getMessage());
                JOptionPane.showMessageDialog(null, e.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            }
            return returnVal;
        }

        private static void Disconnect() {
            if (FTP_Data.Client == null) return;
            try {
                FTP_Data.Client.logout();
                FTP_Data.Client.disconnect();
                FTP_Data.Client = null;
                System.out.println("[INFO] Disconnected from FTP server!");
            } catch (Exception e) {
                System.out.println("[ERROR] " + e.getMessage());
                JOptionPane.showMessageDialog(null, e.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
            }
        }

        private static void ReloadConfig() {
            System.out.println("[INFO] Reloading config (" + ConfigFile + ")..");
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(ConfigFile);
                doc.getDocumentElement().normalize();
                NodeList nodeList = doc.getElementsByTagName("ftp");
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Node node = nodeList.item(i);
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        Element eElement = (Element) node;
                        FTP_Data.Host = eElement.getElementsByTagName("host").item(0).getTextContent();
                        FTP_Data.Port = Integer.parseInt(eElement.getElementsByTagName("port").item(0).getTextContent());
                        FTP_Data.Credentials.Name = eElement.getElementsByTagName("user").item(0).getTextContent();
                        FTP_Data.Credentials.Password = eElement.getElementsByTagName("password").item(0).getTextContent();

                        System.out.println(".. " + FTP_Data.Host);
                        System.out.println(".. " + FTP_Data.Port);
                        System.out.println(".. " + FTP_Data.Credentials.Name);
                        System.out.println(".. " + FTP_Data.Credentials.Password);
                    }
                }
                System.out.println("[INFO] Config loaded!");
            } catch (FileNotFoundException fnfe) {
                System.out.println("[ERROR] Could not locate the config file!");
                JOptionPane.showMessageDialog(null, "Konfigurációs fájl nem található!", "ERROR", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            } catch (NullPointerException nre) {
                System.out.println("[ERROR] Wrong config file format!");
                JOptionPane.showMessageDialog(null, "Konfigurációs fájl rossz formátumban van!", "ERROR", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            } catch (NumberFormatException nfe) {
                System.out.println("[ERROR] Wrong config file format (" + nfe.getMessage() + ")!");
                JOptionPane.showMessageDialog(null, "Rossz konfigurációs elem!\n\n" + nfe.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            } catch (IOException | ParserConfigurationException | DOMException | SAXException e) {
                System.out.println("[ERROR] " + e.getMessage());
                JOptionPane.showMessageDialog(null, e.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            }
        }
    }

    public Logger() {
        initComponents();

        FTP_Data.ReloadConfig();
        if (!FTP_Data.Connect()) {
            System.out.println("[ERROR] Couldn't connect to the FTP server!");
            JOptionPane.showMessageDialog(null, "Sikertelen csatlakozás az FTP szerverhez!", "ERROR", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
        SetCurrentDateTime();
        RefreshLogTypeComboBox();
        typeBox.setSelectedIndex(0);
        int logTypeCount = GetLogTypes().size();
        infoLabel.setText("Info: " + logTypeCount + " találat a(z) '" + typeBox.getSelectedItem() + "' típusban!");
    }

    private ArrayList<String> GetLogFiles(String logType) {
        if (FTP_Data.Client == null) return null;
        ArrayList<String> logFiles = new ArrayList<>();
        try {
            for (FTPFile i : FTP_Data.Client.listFiles(logType)) {
                logFiles.add(i.getName());
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return logFiles;
    }

    private ArrayList<String> GetLogTypes() {
        if (FTP_Data.Client == null) return null;
        ArrayList<String> logTypes = new ArrayList<>();
        try {
            for (FTPFile i : FTP_Data.Client.listDirectories()) {
                logTypes.add(i.getName());
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return logTypes;
    }

    boolean IsDirectoryExists(String dirPath) {
        try {
            FTP_Data.Client.changeWorkingDirectory(dirPath);
            int returnCode = FTP_Data.Client.getReplyCode();
            return returnCode != 550;
        } catch (Exception e) {
            return false;
        }
    }

    private ArrayList<String> GetLinesFromFile(String filePath) {
        if (FTP_Data.Client == null) return null;
        ArrayList<String> logLines = new ArrayList<>();

        try (InputStream inputStream = FTP_Data.Client.retrieveFileStream(filePath)) {
            if (inputStream == null) return null;
            String logContent = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            Collections.addAll(logLines, logContent.split("\\r?\\n"));

            if(!FTP_Data.Client.completePendingCommand()) {
                System.out.println("[ERROR] File transfer failed!");
                JOptionPane.showMessageDialog(null, "A fájlátvitel nem sikerült!", "ERROR", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            JOptionPane.showMessageDialog(null, e.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
        }
        return logLines;
    }

    private void RefreshLogTypeComboBox() {
        ArrayList<String> logTypes = GetLogTypes();
        if (logTypes.size() > 0) {
            for (String item : logTypes) {
                typeBox.addItem(item);
            }
        } else {
            System.out.println("[ERROR] No log types found!");
            JOptionPane.showMessageDialog(null, "Nincsenek log típusok!!", "ERROR", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void ExportSearch() {
        int userSelection = exportFileChooser.showSaveDialog(null);
        File fileToSave = null;
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            fileToSave = exportFileChooser.getSelectedFile();
            System.out.println("[INFO] Export path: '" + fileToSave.getAbsolutePath());
        }
        if (fileToSave != null) {
            String logStr = logTextPane.getText();
            String style = null;
            logStr = logStr.trim();
            logStr += String.format("<style>body{color: rgb(%d,%d,%d);background-color:rgb(%d,%d,%d);</style>",
                    logTextPane.getForeground().getRed(), logTextPane.getForeground().getGreen(), logTextPane.getForeground().getBlue(),
                    logTextPane.getBackground().getRed(), logTextPane.getBackground().getGreen(), logTextPane.getBackground().getBlue());

            try (FileWriter fw = new FileWriter(fileToSave.getAbsolutePath(), false)) {
                fw.write(String.format("<span style='color:gray;'>[[ Keresési típus: %s | Keresési érték: %s | Keresési intervallum: %s - %s ]]</span><br>",
                        typeBox.getSelectedItem().toString(), jTextField1.getText(), startDateTextField.getText(), endDateTextField.getText()));
                fw.write(logStr);
            } catch (IOException ioe) {
                System.out.println("[ERROR] Error during export!");
                JOptionPane.showMessageDialog(null, "Hiba történt exportálás közben!\n\n" + ioe.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void StartSearch() {
        System.out.println("[INFO] Start searching..");

        logTextPane.setContentType("text/html");
        HTMLEditorKit hed = new HTMLEditorKit();
        StyleSheet defaultStyle = hed.getStyleSheet();
        StyleSheet style = new StyleSheet();
        style.addStyleSheet(defaultStyle);
        style.addRule("body{color: white; background-color: black;}");
        hed.setStyleSheet(style);
        logTextPane.setEditorKit(hed);
        logTextPane.setText("<span style='color:gray;'>Keresés..</span>");

        String searchText = jTextField1.getText();
        String logType = typeBox.getSelectedItem().toString().trim();
        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try {
            startDateTime = LocalDateTime.parse(startDateTextField.getText(), dtf);
            endDateTime = LocalDateTime.parse(endDateTextField.getText(), dtf);
        } catch (Exception e) {
            System.out.println("[ERROR] Wrong DateTime format!");
            JOptionPane.showMessageDialog(null, "Hibás dátum formátum!", "ERROR", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (logType == null) { JOptionPane.showMessageDialog(null, "Nincsen log típus kiválasztva!", "WARNING", JOptionPane.WARNING_MESSAGE); return; }
        if (Duration.between(startDateTime, endDateTime).toDays() > 30) { JOptionPane.showMessageDialog(null, "Az időintervallumnak kisebbnek kell lennie mint 30 nap!", "WARNING", JOptionPane.WARNING_MESSAGE); return; }
        if (endDateTime.isBefore(startDateTime)) { JOptionPane.showMessageDialog(null, "Kezdő dátum nem lehet nagyobb a záró dátumnál!", "WARNING", JOptionPane.WARNING_MESSAGE); return; }

        // Get DateTimes between start end end date
        List<LocalDate> between = startDateTime.toLocalDate().datesUntil(endDateTime.toLocalDate()).collect(Collectors.toList());
        between.add(endDateTime.toLocalDate());

        ArrayList<String> rawLogLines = new ArrayList<>();
        String logPath = null;
        String logStr = "";
        LocalTime lineTimestamp = null;
        int allLineCount = 0;

        for (LocalDate i : between) {
            logStr += String.format("-- %04d-%02d-%02d --<br>", i.getYear(), i.getMonthValue(), i.getDayOfMonth());
            logPath = String.format("%s/%s_%04d-%02d-%02d.log", logType, logType, i.getYear(), i.getMonthValue(), i.getDayOfMonth());
            rawLogLines = GetLinesFromFile(logPath);
            if (rawLogLines == null) return;
            for (String line : rawLogLines) {
                if (!line.contains(searchText)) continue;
                if (line.charAt(0) == '[') {
                    // If first char equals [
                    // then HH:mm:ss.SSS => YYYY:MM:dd_HH:mm:ss.SSS
                    lineTimestamp = LocalTime.parse(line.substring(line.indexOf("[") + 1, line.indexOf("]")), DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                    line = String.format("[%04d-%02d-%02d_%s", i.getYear(), i.getMonthValue(), i.getDayOfMonth(), line.substring(line.indexOf("[") + 1, line.length()));
                } else System.out.println("[WARNING] Wronly formatted timestamp found!");

                if (searchText.trim().length() > 0) line = line.replaceAll(searchText, "<span style='color: red;'>" + searchText + "</span>");

                if (between.size() == 1) {
                    if (lineTimestamp.isAfter(startDateTime.toLocalTime()) && lineTimestamp.isBefore(endDateTime.toLocalTime())) {
                        logStr += line + "<br>";
                        allLineCount ++;
                    }
                } else {
                    if (i.equals(startDateTime.toLocalDate())) { // If the dt(DateTime) equals the first (start) date => first log file (<1>, 2, 3)
                        if (lineTimestamp.isAfter(startDateTime.toLocalTime())) logStr += line + "<br>";
                    } else if (i.equals(endDateTime.toLocalDate())) { // If the dt(DateTime) equals the last (end) date => last log file (1, 2, <3>)
                        if (lineTimestamp.isBefore(endDateTime.toLocalTime())) logStr += line + "<br>";
                    }
                    allLineCount ++;
                }
            }
        }
        logTextPane.setText(logStr);
        System.out.println("[INFO] " + allLineCount + " line loaded from " + between.size() + " file, " + allLineCount + " line were displayed!");
        infoLabel.setText("Info: " + rawLogLines.size() + " sor beolvasva " + between.size() + " fájlból, ebből " + allLineCount + " megjelenítve!");
        System.out.println("[INFO] Search successful!");
    }

    public void SetCurrentDateTime() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        startDateTextField.setText(dtf.format(now.minus(1, ChronoUnit.DAYS))); // Now - 1 day [normally don't matter because it gets overwritten]
        endDateTextField.setText(dtf.format(now)); // Now
    }

    // GUI init

    public static void main(String args[]) {
        try {
            javax.swing.UIManager.setLookAndFeel(new DarculaLaf());
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(Logger.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        java.awt.EventQueue.invokeLater(() -> new Logger().setVisible(true));
    }
    private void initComponents() {
        exportFileChooser = new javax.swing.JFileChooser();
        jPanel1 = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jTextField1 = new javax.swing.JTextField();
        startDateTextField = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        endDateTextField = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        typeBox = new javax.swing.JComboBox<>();
        searchButton = new javax.swing.JButton();
        infoLabel = new javax.swing.JLabel();
        exportButton = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        logTextPane = new javax.swing.JTextPane();

        exportFileChooser.setDialogType(javax.swing.JFileChooser.SAVE_DIALOG);

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Logger v1.0 # AronDev");
        setLocationRelativeTo(null); // Center the frame

        jLabel1.setText("Érték:");

        jLabel2.setText("Időintervallum:");

        jLabel3.setText("Típus:");

        jLabel4.setText("-től");

        jLabel5.setText("-ig");

        jLabel6.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jLabel6.setText("(yyyy-MM-dd HH:mm:ss)");

        typeBox.addItemListener(evt -> {
            // On combobox item selected
            String seletedLogType = typeBox.getSelectedItem().toString();
            ArrayList<String> selectedLogTypeLogFiles = GetLogFiles(seletedLogType);
            if (selectedLogTypeLogFiles.size() > 0) {
                LocalDateTime lastDateTime = LocalDate.parse(selectedLogTypeLogFiles.get(selectedLogTypeLogFiles.size() - 1).split("_")[1].split("\\.")[0], DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
                startDateTextField.setText(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(lastDateTime));
                infoLabel.setText("Info: " + selectedLogTypeLogFiles.size() + " fájl a(z) '" + seletedLogType + "' típusban!");
            } else infoLabel.setText("Figyelmeztetés: Nincsenek fájlok a '" + seletedLogType + "' típusban!");
        });

        searchButton.setText("Keresés");
        searchButton.setToolTipText("Keresés megkezdése");
        searchButton.addActionListener(evt -> {
            StartSearch();
        });

        exportButton.setText("Export");
        exportButton.setToolTipText("Eredmények exportálása");
        exportButton.addActionListener(evt -> {
            ExportSearch();
        });

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Keresési feltételek"));
        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
                jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel2Layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(jPanel2Layout.createSequentialGroup()
                                                .addComponent(infoLabel)
                                                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                                        .addGroup(jPanel2Layout.createSequentialGroup()
                                                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                        .addComponent(jLabel1)
                                                                        .addComponent(jLabel2)
                                                                        .addComponent(jLabel3))
                                                                .addGap(49, 49, 49)
                                                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                        .addComponent(typeBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                                        .addComponent(jTextField1)
                                                                        .addGroup(jPanel2Layout.createSequentialGroup()
                                                                                .addComponent(startDateTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 131, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                                .addComponent(jLabel4)
                                                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                                                .addComponent(endDateTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 131, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                                .addComponent(jLabel5)
                                                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 81, Short.MAX_VALUE)
                                                                                .addComponent(jLabel6)
                                                                                .addGap(0, 76, Short.MAX_VALUE))))
                                                        .addGroup(jPanel2Layout.createSequentialGroup()
                                                                .addGap(0, 0, Short.MAX_VALUE)
                                                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                                                        .addComponent(searchButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                                        .addComponent(exportButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                                                .addGap(12, 12, 12))))
        );
        jPanel2Layout.setVerticalGroup(
                jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel2Layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(jLabel1)
                                        .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(jLabel2)
                                        .addComponent(startDateTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel4)
                                        .addComponent(endDateTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel5)
                                        .addComponent(jLabel6))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(typeBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel3))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(searchButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(exportButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(infoLabel)
                                .addContainerGap(51, Short.MAX_VALUE))
        );

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("Keresési eredmény"));

        logTextPane.setEditable(false);
        logTextPane.setBackground(new java.awt.Color(0, 0, 0));
        logTextPane.setForeground(new java.awt.Color(255, 255, 255));
        jScrollPane2.setViewportView(logTextPane);

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
                jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jScrollPane2)
        );
        jPanel3Layout.setVerticalGroup(
                jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 307, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
                jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                        .addComponent(jPanel3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
                jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        getAccessibleContext().setAccessibleName("Logger");

        pack();
    }
    
    private javax.swing.JButton searchButton;
    private javax.swing.JButton exportButton;
    private javax.swing.JComboBox<String> typeBox;
    private javax.swing.JFileChooser exportFileChooser;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel infoLabel;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField startDateTextField;
    private javax.swing.JTextField endDateTextField;
    private javax.swing.JTextPane logTextPane;
}
