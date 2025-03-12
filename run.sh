#!/bin/bash
java -cp bin Peer 1001 6001 &
java -cp bin Peer 1002 6002 localhost 6001 &
java -cp bin Peer 1003 6003 localhost 6002 &
echo "✅ Peers are running in the background."
