#!/bin/bash
java -cp bin PeerProcess 1001 &
sleep 3

java -cp bin PeerProcess 1002 &
sleep 1

java -cp bin PeerProcess 1003 &
echo "✅ Peers are running in the background."
