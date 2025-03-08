import socket
import threading
import sys
import time

class Peer:
    def __init__(self, peer_id, host='localhost', port=6001, connect_to=None):
        self.peer_id = peer_id
        self.host = host
        self.port = port
        self.connect_to = connect_to  # Target peer (host, port) to connect to
        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

    def start_server(self):
        """Starts a server to accept incoming peer connections."""
        self.server_socket.bind((self.host, self.port))
        self.server_socket.listen(5)
        print(f"Peer {self.peer_id} listening on {self.host}:{self.port}")

        while True:
            try:
                client_socket, addr = self.server_socket.accept()
                print(f"Peer {self.peer_id} received connection from {addr}")
                threading.Thread(target=self.handle_client, args=(client_socket,), daemon=True).start()
            except Exception as e:
                print(f"Error in server: {e}")
                break  # Exit loop on error

    def handle_client(self, client_socket):
        """Handles communication with a connected peer."""
        try:
            message = client_socket.recv(1024).decode()
            print(f"Peer {self.peer_id} received: {message}")
            client_socket.send("ACK from Server".encode())
        except Exception as e:
            print(f"Error handling client: {e}")
        finally:
            client_socket.close()

    def connect_to_peer(self):
        """Initiates connection to another peer if specified."""
        if self.connect_to:
            target_host, target_port = self.connect_to
            try:
                client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                client_socket.connect((target_host, target_port))
                client_socket.send(f"Hello from Peer {self.peer_id}".encode())
                response = client_socket.recv(1024).decode()
                
                # ✅ Print response INSIDE the running peer process (Terminal 2)
                print(f"Peer {self.peer_id} connected to {target_host}:{target_port} -> {response}")

                client_socket.close()
            except ConnectionRefusedError:
                print(f"Peer {self.peer_id} could not connect to {target_host}:{target_port}")
            except Exception as e:
                print(f"Connection error: {e}")

# Run as a peer with arguments (peer_id, port, optional target_peer)
if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python peer.py <peer_id> <port> [target_host target_port]")
        sys.exit(1)

    peer_id = int(sys.argv[1])
    port = int(sys.argv[2])
    connect_to = (sys.argv[3], int(sys.argv[4])) if len(sys.argv) == 5 else None

    peer = Peer(peer_id, port=port, connect_to=connect_to)

    # Start server in a separate thread
    server_thread = threading.Thread(target=peer.start_server, daemon=True)
    server_thread.start()

    # Wait a moment for the server to start
    time.sleep(2)

    # ✅ If this peer has a target to connect to, initiate connection
    if connect_to:
        peer.connect_to_peer()

    # Keep the script running to prevent shutdown
    while True:
        try:
            time.sleep(1)
        except KeyboardInterrupt:
            print(f"\nShutting down Peer {peer.peer_id}...")
            sys.exit(0)
