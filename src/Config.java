import java.io.*;

public class Config {
    public int numberOfPreferredNeighbors;
    public int unchokingInterval;
    public int optimisticUnchokingInterval;
    public String fileName;
    public int fileSize;
    public int pieceSize;

    public Config(String configFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(configFile));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] tokens = line.split("\\s+");
            if (tokens.length < 2) continue;
            String key = tokens[0];
            String value = tokens[1];
            switch (key) {
                case "NumberOfPreferredNeighbors":
                    numberOfPreferredNeighbors = Integer.parseInt(value);
                    break;
                case "UnchokingInterval":
                    unchokingInterval = Integer.parseInt(value);
                    break;
                case "OptimisticUnchokingInterval":
                    optimisticUnchokingInterval = Integer.parseInt(value);
                    break;
                case "FileName":
                    fileName = value;
                    break;
                case "FileSize":
                    fileSize = Integer.parseInt(value);
                    break;
                case "PieceSize":
                    pieceSize = Integer.parseInt(value);
                    break;
                default:
                    // unknown key, skip
                    break;
            }
        }
        reader.close();
    }
}
