import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    private static final SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static synchronized void log(int peerId, String message) {
        String time = timestampFormat.format(new Date());
        String logMessage = "[" + time + "]: " + message + "\n";
        String fileName = "log_peer_" + peerId + ".log";

        try (FileWriter fw = new FileWriter(fileName, true)) {
            fw.write(logMessage);
        } catch (IOException e) {
            System.err.println("Error writing to " + fileName);
            e.printStackTrace();
        }
    }
}