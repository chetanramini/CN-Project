public class PeerInfo {
    public int peerId;
    public String hostName;
    public int port;
    public boolean hasFile;

    public PeerInfo(int peerId, String hostName, int port, boolean hasFile) {
        this.peerId = peerId;
        this.hostName = hostName;
        this.port = port;
        this.hasFile = hasFile;
    }

    public static PeerInfo parse(String line) {
        String[] tokens = line.split("\\s+");
        int peerId = Integer.parseInt(tokens[0]);
        String hostName = tokens[1];
        int port = Integer.parseInt(tokens[2]);
        boolean hasFile = tokens[3].equals("1");
        return new PeerInfo(peerId, hostName, port, hasFile);
    }
}
