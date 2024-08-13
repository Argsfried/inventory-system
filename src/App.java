import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Vector;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;

public class App {

    private static final String[] COLUMN_NAMES = {"Asset Tag", "Model", "Manufacturer", "Category", "Quantity", "Serial", "Physical Location", "Where", "Date Received", "Date Recorded", "Note", "Image"};
    private static final File SER_FILE = new File("inventory.ser");
    private static String imagePath = ""; // Class-level field for image path
    private static boolean exporting = false;
    private static File lastImportDirectory = new File(System.getProperty("user.home")); // Default to user's home directory

    public static void main(String[] args) {
        loadLastImportDirectory();
        loadLastExportDirectory();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Inventory System");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1600, 600);
            frame.setLayout(new BorderLayout());

            // Create the table
            DefaultTableModel model = new DefaultTableModel(COLUMN_NAMES, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    // Make only the image column non-editable
                    return column != 11; // Assuming the image column is at index 11
                }
            };
            JTable table = new JTable(model);
            TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
            table.setRowSorter(sorter);
            configureTableForMultilineCells(table);
            configureTableForImageHandling(table);
            JScrollPane scrollPane = new JScrollPane(table);
            frame.add(scrollPane, BorderLayout.CENTER);

            // Add this after creating the table
            model.addTableModelListener(e -> saveTableData(model));
            
            // Create buttons
            JPanel buttonPanel = new JPanel();
            JButton addRowButton = new JButton("Add Row");
            JButton addAssetButton = new JButton("Add Asset");
            JButton deleteRowButton = new JButton("Delete Selected Row");
            JButton deleteAllButton = new JButton("Delete All Rows");
            JButton exportButton = new JButton("Export to Excel");
            JButton importButton = new JButton("Import from Excel");

            buttonPanel.add(addRowButton);
            buttonPanel.add(addAssetButton);
            buttonPanel.add(deleteRowButton);
            buttonPanel.add(deleteAllButton);
            buttonPanel.add(exportButton);
            buttonPanel.add(importButton);

            // Create the search box and dropdown
            JPanel searchPanel = new JPanel();
            JTextField searchField = new JTextField(20);
            JComboBox<String> searchByComboBox = new JComboBox<>(COLUMN_NAMES);
            searchByComboBox.addActionListener(e -> searchField.setEnabled(searchByComboBox.getSelectedIndex() >= 0));

            searchField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent e) {
                    if (searchByComboBox.getSelectedIndex() >= 0) {
                        int columnIndex = searchByComboBox.getSelectedIndex();
                        String searchText = searchField.getText();
                        filterTable(table, columnIndex, searchText);
                    }
                }
            });

            searchPanel.add(searchField);
            searchPanel.add(searchByComboBox);
            frame.add(searchPanel, BorderLayout.NORTH);

            // Add Row Button Action
            addRowButton.addActionListener(e -> {
                Object[] row = new Object[COLUMN_NAMES.length];
                row[COLUMN_NAMES.length - 1] = "No Image"; // Default image value
                model.addRow(row);
            });

            // Add Asset Button Action
            addAssetButton.addActionListener(e -> {
                // Reset imagePath to default value when starting to add an asset
                imagePath = ""; // Clear previous image path
                
                JDialog dialog = new JDialog(frame, "Add Asset", true);
                dialog.setSize(400, 600); // Increase size
                dialog.setLayout(new BoxLayout(dialog.getContentPane(), BoxLayout.Y_AXIS));

                // Fields for Asset Tag, Model, Manufacturer, Category, Quantity, Serial, Physical Location
                JTextField assetTagField = new JTextField();
                JTextField modelField = new JTextField();
                JTextField manufacturerField = new JTextField();
                JTextField categoryField = new JTextField();
                JTextField quantityField = new JTextField();
                JTextField serialField = new JTextField();
                JTextField physicalLocationField = new JTextField();

                // Date Received as DatePicker
                DatePickerSettings dateSettings = new DatePickerSettings();
                DatePicker dateReceivedPicker = new DatePicker(dateSettings);

                // Where and Notes
                JTextField whereField = new JTextField();
                JTextArea notesArea = new JTextArea(5, 30); // Increased size
                notesArea.setLineWrap(true);
                notesArea.setWrapStyleWord(true);

                // Image field
                JButton imageButton = new JButton("Select Image");
                JFileChooser fileChooser = new JFileChooser();

                imageButton.addActionListener(event -> {
                    int returnValue = fileChooser.showOpenDialog(dialog);
                    if (returnValue == JFileChooser.APPROVE_OPTION) {
                        File selectedFile = fileChooser.getSelectedFile();
                        imagePath = selectedFile.getAbsolutePath(); // Set the selected image path
                        imageButton.setText(selectedFile.getName()); // Update button text
                    } else {
                        imagePath = ""; // Reset if no file is selected
                        imageButton.setText("Select Image"); // Reset button text
                    }
                });

                // Add fields to dialog
                dialog.add(createFieldPanel("Asset Tag", assetTagField));
                dialog.add(createFieldPanel("Model", modelField));
                dialog.add(createFieldPanel("Manufacturer", manufacturerField));
                dialog.add(createFieldPanel("Category", categoryField));
                dialog.add(createFieldPanel("Quantity", quantityField));
                dialog.add(createFieldPanel("Serial", serialField));
                dialog.add(createFieldPanel("Physical Location", physicalLocationField));
                dialog.add(createFieldPanel("Date Received", dateReceivedPicker));
                dialog.add(createFieldPanel("Where", whereField));
                dialog.add(createFieldPanel("\nNote", new JScrollPane(notesArea)));
                dialog.add(createFieldPanel("Image", imageButton));

                JButton submitButton = new JButton("Add Asset");
                dialog.add(submitButton);

                // Update the `Add Asset Button` action listener
                submitButton.addActionListener(event -> {
                    Object[] row = new Object[COLUMN_NAMES.length];
                    row[0] = assetTagField.getText(); // Asset Tag
                    row[1] = modelField.getText(); // Model
                    row[2] = manufacturerField.getText(); // Manufacturer
                    row[3] = categoryField.getText(); // Category
                    row[4] = quantityField.getText(); // Quantity
                    row[5] = serialField.getText(); // Serial
                    row[6] = physicalLocationField.getText(); // Physical Location
                    row[7] = whereField.getText(); // Where
                    row[8] = dateReceivedPicker.getDate(); // Date Received
                    row[9] = new java.util.Date(); // Date Recorded (current date)
                    row[10] = notesArea.getText(); // Notes
                    row[11] = imagePath.isEmpty() ? "No Image" : new File(imagePath).getAbsolutePath(); // Use absolute path
                    model.addRow(row);
                    dialog.dispose();
                    saveTableData(model); // Save after adding
                    imagePath = ""; // Clear imagePath
                });

                dialog.setVisible(true);
            });

            // Delete Selected Row Button Action
            deleteRowButton.addActionListener(e -> {
                int selectedRow = table.getSelectedRow();
                if (selectedRow >= 0) {
                    int response = JOptionPane.showConfirmDialog(frame, "Are you sure you want to delete the selected row?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
                    if (response == JOptionPane.YES_OPTION) {
                        model.removeRow(selectedRow);
                        saveTableData(model); // Save after deletion
                    }
                } else {
                    JOptionPane.showMessageDialog(frame, "No row selected.");
                }
            });

            // Delete All Rows Button Action
            deleteAllButton.addActionListener(e -> extracted(frame, model, table, searchField));

            // Export to Excel Button Action
            exportButton.addActionListener(e -> {
                if (!exporting) {
                    exporting = true;
                    exportToExcel(table);
                    exporting = false;
                }
            });

            // Import from Excel Button Action
            importButton.addActionListener(e -> {
                try {
                    importFromExcel(model, frame);
                } catch (InvalidFormatException e1) {
                    // TODO Auto-generated catch block
                    //e1.printStackTrace();
                }
            });

            // Restore table data
            restoreTableData(model);

            frame.add(buttonPanel, BorderLayout.SOUTH);
            frame.setVisible(true);
        });
    }

    private static void extracted(JFrame frame, DefaultTableModel model, JTable table, JTextField searchField) {
        int response = JOptionPane.showConfirmDialog(frame, "Are you sure you want to delete all rows?", "Confirm Delete All", JOptionPane.YES_NO_OPTION);
        if (response == JOptionPane.YES_OPTION) {
            model.setRowCount(0);
            saveTableData(model); // Save after deletion
            searchField.setText(""); // Clear search field after deletion
            filterTable(table, 0, ""); // Clear filters
        }
    }

    private static void filterTable(JTable table, int columnIndex, String searchText) {
        TableRowSorter<?> sorter = (TableRowSorter<?>) table.getRowSorter();
        RowFilter<Object, Object> rf = null;
        try {
            rf = RowFilter.regexFilter("(?i)" + searchText, columnIndex);
        } catch (java.util.regex.PatternSyntaxException e) {
            return;
        }
        sorter.setRowFilter(rf);
    }

    private static void configureTableForMultilineCells(JTable table) {
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (component instanceof JLabel) {
                    ((JLabel) component).setVerticalAlignment(JLabel.TOP);
                }
                return component;
            }
        });
    }

    private static void configureTableForImageHandling(JTable table) {
        // Set a custom renderer for the image column to show only the file name
        table.getColumnModel().getColumn(11).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String fullPath = (String) value;
                String fileName = fullPath != null && !"No Image".equals(fullPath) ? new File(fullPath).getName() : "No Image";
                return super.getTableCellRendererComponent(table, fileName, isSelected, hasFocus, row, column);
            }
        });
    
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
    
                if (col == 11) { // Assuming image column is at index 11
                    String imagePath = (String) table.getValueAt(row, col);
    
                    // Left-click to view the image
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        if (!"No Image".equals(imagePath)) {
                            new ImageViewerDialog(imagePath).setVisible(true);
                        } else {
                            JOptionPane.showMessageDialog(table, "No image to display.");
                        }
                    }
    
                    // Right-click to replace the image
                    else if (SwingUtilities.isRightMouseButton(e)) {
                        JFileChooser fileChooser = new JFileChooser();
                        int returnValue = fileChooser.showOpenDialog(table);
                        if (returnValue == JFileChooser.APPROVE_OPTION) {
                            File selectedFile = fileChooser.getSelectedFile();
                            table.setValueAt(selectedFile.getAbsolutePath(), row, col); // Store full path
                            saveTableData((DefaultTableModel) table.getModel()); // Save after changing image
                        }
                    }
                }
            }
        });
    }
    
    

    private static JPanel createFieldPanel(String labelText, Component fieldComponent) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        JLabel label = new JLabel(labelText);
        panel.add(label);
        panel.add(fieldComponent);
        return panel;
    }

    private static void saveTableData(DefaultTableModel model) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SER_FILE))) {
            Vector<Vector<Object>> dataVector = model.getDataVector();
            oos.writeObject(new Vector<>(dataVector));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void restoreTableData(DefaultTableModel model) {
        if (SER_FILE.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(SER_FILE))) {
                Vector<?> dataVector = (Vector<?>) ois.readObject();
                for (Object rowData : dataVector) {
                    model.addRow((Vector<?>) rowData);
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private static File lastExportDirectory = new File(System.getProperty("user.home")); // Default to user's home directory

    private static void exportToExcel(JTable table) {
        JFileChooser fileChooser = new JFileChooser(lastExportDirectory); // Start in last export directory
        fileChooser.setFileFilter(new FileNameExtensionFilter("Excel files", "xlsx"));
        
        // Set the default file name to "Inventory.xlsx"
        fileChooser.setSelectedFile(new File("Inventory.xlsx"));
        
        int returnValue = fileChooser.showSaveDialog(table);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            lastExportDirectory = fileChooser.getCurrentDirectory(); // Update last export directory
            
            String filePath = selectedFile.getAbsolutePath();
            if (!filePath.endsWith(".xlsx")) {
                selectedFile = new File(filePath + ".xlsx");
            }

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Inventory");
                DefaultTableModel model = (DefaultTableModel) table.getModel();

                Row headerRow = sheet.createRow(0);
                for (int col = 0; col < model.getColumnCount(); col++) {
                    headerRow.createCell(col).setCellValue(model.getColumnName(col));
                }

                for (int row = 0; row < model.getRowCount(); row++) {
                    Row excelRow = sheet.createRow(row + 1);
                    for (int col = 0; col < model.getColumnCount(); col++) {
                        Object value = model.getValueAt(row, col);
                        if (value != null) {
                            excelRow.createCell(col).setCellValue(value.toString());
                        } else {
                            excelRow.createCell(col).setCellValue(""); // Empty string for null values
                        }
                    }
                }

                try (FileOutputStream fos = new FileOutputStream(selectedFile)) {
                    workbook.write(fos);
                    JOptionPane.showMessageDialog(table, "Data exported successfully.");
                    saveLastExportDirectory(); // Save the last export directory
                } catch (IOException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(table, "Failed to export data.");
                }
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(table, "Failed to export data.");
            }
        }
    }

    private static void saveLastExportDirectory() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("lastExportDirectory.ser"))) {
            oos.writeObject(lastExportDirectory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadLastExportDirectory() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("lastExportDirectory.ser"))) {
            lastExportDirectory = (File) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            lastExportDirectory = new File(System.getProperty("user.home")); // Default to user's home directory
        }
    }

    private static void importFromExcel(DefaultTableModel model, JFrame frame) throws InvalidFormatException {
        JFileChooser fileChooser = new JFileChooser(lastImportDirectory);
        fileChooser.setFileFilter(new FileNameExtensionFilter("Excel files", "xlsx"));
        int returnValue = fileChooser.showOpenDialog(frame);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            lastImportDirectory = fileChooser.getCurrentDirectory(); // Update last import directory
            saveLastImportDirectory();
            try (Workbook workbook = new XSSFWorkbook(selectedFile)) {
                Sheet sheet = workbook.getSheetAt(0);
                model.setRowCount(0); // Clear existing data
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue; // Skip header row
                    Vector<Object> rowData = new Vector<>();
                    for (int cn = 0; cn < COLUMN_NAMES.length; cn++) {
                        if (row.getCell(cn) != null) {
                            rowData.add(row.getCell(cn).toString());
                        } else {
                            rowData.add("");
                        }
                    }
                    model.addRow(rowData);
                }
                saveTableData(model); // Save after import
                JOptionPane.showMessageDialog(frame, "Data imported successfully.");
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(frame, "Failed to import data.");
            }
        }
    }
    

    private static void loadLastImportDirectory() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("lastImportDirectory.ser"))) {
            lastImportDirectory = (File) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            lastImportDirectory = new File(System.getProperty("user.home")); // Default to user's home directory
        }
    }

    private static void saveLastImportDirectory() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("lastImportDirectory.ser"))) {
            oos.writeObject(lastImportDirectory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ImageViewerDialog extends JDialog {
        ImageViewerDialog(String imagePath) {
            setTitle("Image Viewer");
            setSize(600, 400);
            JLabel imageLabel = new JLabel();
            imageLabel.setIcon(new javax.swing.ImageIcon(imagePath));
            JScrollPane scrollPane = new JScrollPane(imageLabel);
            add(scrollPane);
        }
    }
}
