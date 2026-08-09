#!/bin/sh
# Fix DNS resolution for dl.google.com in sanctioned regions
# This must be run at the start of any RUN step that needs Google APIs
echo "nameserver 8.8.8.8" > /tmp/resolv.google
echo "nameserver 8.8.4.4" >> /tmp/resolv.google
cat /etc/resolv.conf >> /tmp/resolv.google
cp /tmp/resolv.google /etc/resolv.conf 2>/dev/null || true
