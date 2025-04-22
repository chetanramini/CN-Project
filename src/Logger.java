import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    private final int peerId;
    private final String logFile;

    public Logger(int peerId) {
        this.peerId = peerId;
        this.logFile = "log_peer_" + peerId + ".log";
    }

    public void log(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String fullMessage = "[" + timestamp + "]: " + message;
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(fullMessage + "\n");
        } catch (IOException e) {
            System.err.println("Logging failed for peer " + peerId);
            e.printStackTrace();
        }
        // Optional: also print to terminal for dev
        System.out.println(fullMessage);
    }
}