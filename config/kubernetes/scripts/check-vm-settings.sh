#!/bin/bash
# Phase 2: VM Environment Check Script
# Run this to verify VM settings and IO performance

set -e

echo "=== Phase 2: Host and VM Environment Check ==="
echo ""

# 2.1. Check host resources (from inside VM - limited info)
echo "=== 2.1. VM Resource Usage ==="
echo "CPU Load:"
uptime
echo ""

echo "Memory:"
free -h
echo ""

echo "Disk Space:"
df -h | grep -E "Filesystem|/dev/"
echo ""

# 2.2. Check paravirtualization (can't directly check, but can infer)
echo "=== 2.2. Virtualization Info ==="
echo "CPU Info:"
lscpu | grep -E "Model name|CPU\(s\)|Virtualization"
echo ""

echo "Kernel modules related to virtualization:"
lsmod | grep -E "kvm|virtio" || echo "No KVM/virtio modules found (may indicate paravirtualization not optimal)"
echo ""

# 2.3. Check for IO latency
echo "=== 2.3. Disk IO Performance ==="
if command -v iostat >/dev/null 2>&1; then
    echo "Sampling disk IO for 5 seconds..."
    echo "Key metrics to watch:"
    echo "- %util: should be < 80%"
    echo "- await: average wait time, should be < 50ms"
    echo "- svctm: service time, should be < 20ms"
    echo ""
    iostat -x 1 5
else
    echo "iostat not installed."
    echo "Install with: sudo apt-get update && sudo apt-get install -y sysstat"
    echo ""
    echo "Alternative: Check disk stats manually"
    echo "Before:"
    cat /proc/diskstats | grep -E "sda|vda" | head -1
    sleep 2
    echo "After 2 seconds:"
    cat /proc/diskstats | grep -E "sda|vda" | head -1
fi
echo ""

echo "=== VM Settings Recommendations ==="
echo "Please verify in VirtualBox GUI:"
echo "1. System → Acceleration → Paravirtualization Interface: should be 'KVM'"
echo "2. System → Processor: should have 4+ CPUs"
echo "3. System → Acceleration → Enable VT-x/AMD-V: should be enabled"
echo "4. System → Acceleration → Enable Nested VT-x/AMD-V: should be enabled"
echo ""
echo "On Windows host, check Task Manager:"
echo "- CPU usage should not be at 100%"
echo "- Disk response time should be < 50ms"
echo "- Available memory should be > 2GB"
