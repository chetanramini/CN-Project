import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class PeerProcess {
    private int localPeerId;
    private Config config;
    private List<PeerInfo> peerInfoList = new ArrayList<>();
    // Use a thread-safe list for all handlers
    private List<ConnectionHandler> handlers = new CopyOnWriteArrayList<>();
    private FileManager fileManager;

    public PeerProcess(int localPeerId) {
        this.localPeerId = localPeerId;
    }

    public void start() {
        try {
            // 1. Load configuration
            config = new Config("Common.cfg");
            readPeerInfo("PeerInfo.cfg");

            // 2. Initialize FileManager
            boolean hasFile = peerInfoList.stream()
                .filter(pi -> pi.peerId == localPeerId)
                .findFirst()
                .get()
                .hasFile;
            fileManager = new FileManager(
                String.valueOf(localPeerId),
                config.fileName,
                config.fileSize,
                config.pieceSize,
                hasFile
            );

            // 3. Start listening for incoming connections
            new Thread(this::startServer).start();
            Thread.sleep(1000);

            // 4. Initiate outgoing connections to lower‐ID peers
            initiateConnections();

            // 5. Schedule random choke/unchoke every 3 seconds
            scheduleChokingTasks();

            System.out.println("Peer " + localPeerId + " setup complete.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void readPeerInfo(String fileName) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    peerInfoList.add(PeerInfo.parse(line));
                }
            }
        }
    }

    private void startServer() {
        int myPort = peerInfoList.stream()
            .filter(pi -> pi.peerId == localPeerId)
            .findFirst().get().port;
        try (ServerSocket serverSocket = new ServerSocket(myPort)) {
            System.out.println("Peer " + localPeerId + " listening on port " + myPort);
            while (true) {
                Socket sock = serverSocket.accept();
                ConnectionHandler handler = new ConnectionHandler(sock, localPeerId, fileManager);
                handlers.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initiateConnections() {
        for (PeerInfo pi : peerInfoList) {
            if (pi.peerId < localPeerId) {
                try {
                    Socket sock = new Socket(pi.hostName, pi.port);
                    ConnectionHandler handler = new ConnectionHandler(sock, localPeerId, fileManager);
                    handlers.add(handler);
                    new Thread(handler).start();
                } catch (IOException e) {
                    System.out.println("Failed to connect to peer " + pi.peerId);
                }
            }
        }
    }

    private void scheduleChokingTasks() {
        ScheduledExecutorService svc = Executors.newScheduledThreadPool(1);
        final int interval = 3;  // seconds
        final int k = config.numberOfPreferredNeighbors;

        svc.scheduleAtFixedRate(() -> {
            // 1. Collect all interested handlers
            List<ConnectionHandler> interested = new ArrayList<>();
            for (ConnectionHandler h : handlers) {
                if (h.isInterested) interested.add(h);
            }

            // 2. Randomly pick k preferred
            Collections.shuffle(interested);
            Set<ConnectionHandler> preferred = new HashSet<>(
                interested.subList(0, Math.min(k, interested.size()))
            );

            // 3. Log selection
            System.out.println("Peer " + localPeerId + " [RAND CHOKE] preferred: " +
                preferred.stream()
                         .map(h -> String.valueOf(h.remotePeerId))
                         .collect(Collectors.joining(","))
            );

            // 4. Send choke/unchoke
            for (ConnectionHandler h : handlers) {
                if (preferred.contains(h)) {
                    h.sendMessage(new Message.UnchokeMessage());
                    h.isUnchoked = true;
                    h.isOptimistic = false;
                    System.out.println(" → Unchoked peer " + h.remotePeerId);
                } else {
                    h.sendMessage(new Message.ChokeMessage());
                    h.isUnchoked = false;
                    h.isOptimistic = false;
                    System.out.println(" → Choked peer  " + h.remotePeerId);
                }
            }
        }, 0, interval, TimeUnit.SECONDS);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java PeerProcess <peerId>");
            System.exit(1);
        }
        new PeerProcess(Integer.parseInt(args[0])).start();
    }
}
