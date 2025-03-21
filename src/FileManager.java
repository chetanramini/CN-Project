import java.io.*;
import java.util.BitSet;

public class FileManager {
    private String peerFolder;    // Folder where files are stored (1001, 1002, etc.)
    private String fileName;      // File name (e.g., "tree.jpg")
    private int fileSize;         // Total file size in bytes
    private int pieceSize;        // Size of each piece in bytes
    private int numPieces;        // Total number of pieces (calculated)
    private BitSet bitfield;      // Tracks which pieces are available

    public FileManager(String peerFolder, String fileName, int fileSize, int pieceSize, boolean hasFile) throws IOException {
        if (!isValidPeerFolder(peerFolder)) {
            throw new IOException("Invalid peer folder: " + peerFolder);
        }
        this.peerFolder = peerFolder;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.pieceSize = pieceSize;
        this.numPieces = (fileSize + pieceSize - 1) / pieceSize;

        this.bitfield = new BitSet(numPieces);
        if (hasFile) {
            bitfield.set(0, numPieces, true);
            splitFileIntoPieces();
        }
    }

    // Checks if the given folder is a valid peer directory (strictly between 1001 - 1006)
    private boolean isValidPeerFolder(String folderName) {
        File folder = new File(folderName);
        if (!folder.exists() || !folder.isDirectory()) {
            return false;
        }

        try {
            int peerId = Integer.parseInt(folderName);
            return peerId >= 1001 && peerId <= 1006;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public BitSet getBitfield() {
        return bitfield;
    }

    private void splitFileIntoPieces() throws IOException {
        File file = new File(peerFolder, fileName);
        if (!file.exists()) {
            System.out.println("Error: File " + fileName + " not found in folder " + peerFolder);
            return;
        }
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            for (int i = 0; i < numPieces; i++) {
                int currentPieceSize = Math.min(pieceSize, fileSize - i * pieceSize);
                byte[] pieceData = new byte[currentPieceSize];
                int bytesRead = is.read(pieceData);
                if (bytesRead != currentPieceSize) {
                    throw new IOException("Error reading piece " + i);
                }
                File pieceFile = new File(peerFolder, "piece_" + i + ".dat");
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(pieceFile))) {
                    os.write(pieceData);
                }
            }
        }
        System.out.println("File split into " + numPieces + " pieces.");
    }

    public void writePiece(int pieceIndex, byte[] data) throws IOException {
        if (pieceIndex < 0 || pieceIndex >= numPieces) {
            throw new IllegalArgumentException("Invalid piece index");
        }
        File pieceFile = new File(peerFolder, "piece_" + pieceIndex + ".dat");
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(pieceFile))) {
            os.write(data);
        }
        bitfield.set(pieceIndex, true);
        System.out.println("Wrote piece " + pieceIndex + " to " + peerFolder);
    }

    public boolean isComplete() {
        return bitfield.cardinality() == numPieces;
    }
}
