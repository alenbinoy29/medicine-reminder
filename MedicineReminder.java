import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MedicineReminder extends JFrame {

    Connection con;
    JTextField nameField = new JTextField();
    JTextField doseField = new JTextField();
    JSpinner timeSpinner = new JSpinner(new SpinnerDateModel());
    JComboBox<String> freqBox = new JComboBox<>(new String[]{"Once a day", "Twice a day", "Three times a day"});
    DefaultTableModel model = new DefaultTableModel(new String[]{"ID", "Medicine", "Dosage", "Time", "Frequency"}, 0) {
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    JTable table = new JTable(model);
    JLabel clock = new JLabel(" ");
    SimpleDateFormat hhmm = new SimpleDateFormat("HH:mm");
    String lastChecked = "";

    MedicineReminder() throws SQLException {
        super("Medicine Reminder");
        con = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/medicine_db?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true",
                "root", "root@123");
        con.createStatement().execute("CREATE TABLE IF NOT EXISTS medicine ("
                + "id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(100), dosage VARCHAR(50), "
                + "time VARCHAR(5), frequency VARCHAR(30))");
        setupScreen();
        loadTable();
        new javax.swing.Timer(1000, e -> tick()).start();
    }

    void setupScreen() {
        timeSpinner.setEditor(new JSpinner.DateEditor(timeSpinner, "HH:mm"));

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        form.add(new JLabel("Medicine name"));
        form.add(nameField);
        form.add(new JLabel("Dosage"));
        form.add(doseField);
        form.add(new JLabel("Time (24 hour)"));
        form.add(timeSpinner);
        form.add(new JLabel("Frequency"));
        form.add(freqBox);

        JButton addBtn = new JButton("Add");
        JButton updateBtn = new JButton("Update");
        JButton deleteBtn = new JButton("Delete");
        JButton clearBtn = new JButton("Clear");
        JButton soundBtn = new JButton("Test sound");
        addBtn.addActionListener(e -> addMedicine());
        updateBtn.addActionListener(e -> updateMedicine());
        deleteBtn.addActionListener(e -> deleteMedicine());
        clearBtn.addActionListener(e -> clearForm());
        soundBtn.addActionListener(e -> playSound());

        JPanel buttons = new JPanel();
        buttons.add(addBtn);
        buttons.add(updateBtn);
        buttons.add(deleteBtn);
        buttons.add(clearBtn);
        buttons.add(soundBtn);

        clock.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(buttons, BorderLayout.CENTER);
        bottom.add(clock, BorderLayout.SOUTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> fillForm());
        table.getColumnModel().getColumn(0).setMaxWidth(50);

        add(form, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        setSize(620, 480);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    void addMedicine() {
        if (nameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter the medicine name.");
            return;
        }
        String sql = "INSERT INTO medicine (name, dosage, time, frequency) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nameField.getText().trim());
            ps.setString(2, doseField.getText().trim());
            ps.setString(3, hhmm.format(timeSpinner.getValue()));
            ps.setString(4, (String) freqBox.getSelectedItem());
            ps.executeUpdate();
            clearForm();
            loadTable();
        } catch (SQLException ex) {
            showError(ex);
        }
    }

    void updateMedicine() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a medicine from the table first.");
            return;
        }
        int id = (int) model.getValueAt(row, 0);
        String sql = "UPDATE medicine SET name = ?, dosage = ?, time = ?, frequency = ? WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nameField.getText().trim());
            ps.setString(2, doseField.getText().trim());
            ps.setString(3, hhmm.format(timeSpinner.getValue()));
            ps.setString(4, (String) freqBox.getSelectedItem());
            ps.setInt(5, id);
            ps.executeUpdate();
            clearForm();
            loadTable();
        } catch (SQLException ex) {
            showError(ex);
        }
    }

    void deleteMedicine() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a medicine from the table first.");
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this, "Delete this medicine?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM medicine WHERE id = ?")) {
            ps.setInt(1, (int) model.getValueAt(row, 0));
            ps.executeUpdate();
            clearForm();
            loadTable();
        } catch (SQLException ex) {
            showError(ex);
        }
    }

    void loadTable() {
        model.setRowCount(0);
        try (ResultSet rs = con.createStatement().executeQuery("SELECT * FROM medicine ORDER BY time")) {
            while (rs.next()) {
                model.addRow(new Object[]{rs.getInt("id"), rs.getString("name"), rs.getString("dosage"),
                        rs.getString("time"), rs.getString("frequency")});
            }
        } catch (SQLException ex) {
            showError(ex);
        }
    }

    void fillForm() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        nameField.setText((String) model.getValueAt(row, 1));
        doseField.setText((String) model.getValueAt(row, 2));
        freqBox.setSelectedItem(model.getValueAt(row, 4));
        try {
            timeSpinner.setValue(hhmm.parse((String) model.getValueAt(row, 3)));
        } catch (java.text.ParseException ex) {
            // ignore, time stays as it was
        }
    }

    void clearForm() {
        table.clearSelection();
        nameField.setText("");
        doseField.setText("");
        timeSpinner.setValue(new Date());
        freqBox.setSelectedIndex(0);
    }

    void tick() {
        Date now = new Date();
        clock.setText("Current time: " + new SimpleDateFormat("hh:mm:ss a").format(now));

        String minute = hhmm.format(now);
        if (minute.equals(lastChecked)) return;
        lastChecked = minute;

        StringBuilder msg = new StringBuilder();
        try (PreparedStatement ps = con.prepareStatement("SELECT name, dosage FROM medicine WHERE time = ?")) {
            ps.setString(1, minute);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                msg.append("Take ").append(rs.getString(1)).append(" - ").append(rs.getString(2)).append("\n");
            }
        } catch (SQLException ex) {
            return;
        }

        if (msg.length() > 0) {
            playSound();
            setState(Frame.NORMAL);
            toFront();
            JOptionPane.showMessageDialog(this, msg.toString(), "Medicine Reminder", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    void playSound() {
        new Thread(() -> {
            try {
                float rate = 44100;
                AudioFormat af = new AudioFormat(rate, 16, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(af);
                line.open(af);
                line.start();
                byte[] gap = new byte[(int) (rate * 2 * 0.3)];
                byte[] tone = new byte[(int) (rate * 2 * 0.25)];
                for (int i = 0; i < tone.length / 2; i++) {
                    short v = (short) (Math.sin(i * 2 * Math.PI * 880 / rate) * 20000);
                    tone[2 * i] = (byte) (v & 0xff);
                    tone[2 * i + 1] = (byte) (v >> 8);
                }
                line.write(gap, 0, gap.length);
                for (int n = 0; n < 3; n++) {
                    line.write(tone, 0, tone.length);
                    line.write(gap, 0, gap.length);
                }
                line.drain();
                line.close();
            } catch (Exception ex) {
                ex.printStackTrace();
                Toolkit.getDefaultToolkit().beep();
            }
        }).start();
    }

    void showError(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(() -> {
            try {
                new MedicineReminder().setVisible(true);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(null, "Could not connect to MySQL:\n" + ex.getMessage());
            }
        });
    }
}
