#!/bin/bash
# Deep kernel and etcd diagnosis script
# Run this to identify the process causing CPU soft lockups

set -e

echo "=== Phase 3: Deep Kernel and etcd Diagnosis ==="
echo ""

# 3.1. Monitor kernel logs
echo "=== 3.1. Recent Kernel Messages (last 50) ==="
sudo dmesg | tail -50 | grep -E "soft lockup|BUG|WARNING|panic|etcd|kubelet" || echo "No critical kernel messages found"
echo ""

# Monitor in real-time (non-blocking, runs for 10 seconds)
echo "=== Monitoring kernel messages for 10 seconds (Ctrl+C to stop early) ==="
timeout 10 sudo journalctl -kf --no-pager 2>/dev/null || sudo dmesg -w &
DMESG_PID=$!
sleep 10
kill $DMESG_PID 2>/dev/null || true
echo ""

# 3.2. Check etcd health and disk priority
echo "=== 3.2. etcd Health Check ==="
ETCD_PID=$(pgrep -f etcd || echo "")
if [ -z "$ETCD_PID" ]; then
    echo "WARNING: etcd process not found!"
else
    echo "etcd PID: $ETCD_PID"
    echo "etcd process info:"
    ps aux | grep -E "PID|$ETCD_PID" | head -2
    
    echo ""
    echo "Current IO priority:"
    sudo ionice -p $ETCD_PID || echo "Could not get IO priority"
    
    echo ""
    echo "Setting high IO priority for etcd (realtime, priority 0)..."
    sudo ionice -c2 -n0 -p $ETCD_PID && echo "✓ IO priority set successfully" || echo "✗ Failed to set IO priority"
    
    echo ""
    echo "Verifying new IO priority:"
    sudo ionice -p $ETCD_PID
fi
echo ""

# 3.3. Check for high context switching or interrupts
echo "=== 3.3. Context Switches and Interrupts (vmstat) ==="
if command -v vmstat >/dev/null 2>&1; then
    echo "Sampling for 5 seconds..."
    vmstat 1 5
    echo ""
    echo "Key metrics:"
    echo "- cs: context switches per second (should be < 10000)"
    echo "- in: interrupts per second (should be stable)"
    echo "- r: runnable processes (should be < CPU count)"
else
    echo "vmstat not installed. Install with: sudo apt-get install sysstat"
fi
echo ""

# Additional diagnostics
echo "=== Additional Diagnostics ==="
echo "CPU Load Average:"
uptime
echo ""

echo "Top 10 CPU consumers:"
ps aux --sort=-%cpu | head -11
echo ""

echo "Top 10 Memory consumers:"
ps aux --sort=-%mem | head -11
echo ""

echo "System interrupts:"
cat /proc/interrupts | head -5
echo ""

echo "Context switches:"
grep ctxt /proc/stat
echo ""

echo "=== Diagnosis Complete ==="
echo "Review the output above to identify:"
echo "1. Which process is consuming the most CPU"
echo "2. If etcd is the culprit (check its CPU usage)"
echo "3. If there are high context switches or interrupts"
echo "4. Any kernel messages indicating specific failures"
