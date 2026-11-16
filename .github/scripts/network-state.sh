#!/usr/bin/env bash
# Snapshot of the runner's DNS/network state, captured before and after the integration tests to pin
# down why a K3s runtime stall wedges the host resolver: getaddrinfo hangs, which freezes the JVM's
# per-host DNS lock (breaking the container force-kill) and the tailscale post-step logout.
#
# Every probe is bounded (timeout) and best-effort (never aborts), so this script cannot hang even on
# a fully wedged box. Pass a label (e.g. "before" / "after") as $1 for the banner.
set +e
label="${1:-net-state}"
echo "########## NET STATE [$label] ##########"

echo "--- /etc/resolv.conf ---"
cat /etc/resolv.conf
echo "--- nsswitch hosts line ---"
grep -E '^hosts:' /etc/nsswitch.conf

echo "--- systemd-resolved active? ---"
systemctl is-active systemd-resolved
echo "--- resolvectl status ---"
# resolvectl status/statistics reach the resolve monitor socket, which is root-only, so use sudo;
# sudo before timeout so timeout runs as root and can actually signal resolvectl
sudo timeout 5 resolvectl status || echo "resolvectl status FAILED/timeout"
echo "--- resolvectl statistics ---"
sudo timeout 5 resolvectl statistics || echo "resolvectl statistics FAILED/timeout"
echo "--- :53 listeners ---"
sudo ss -lunp 'sport = :53'

# glibc getaddrinfo path: exactly what the JVM and the tailscale CLI use
echo "--- getent (glibc/nss path) ---"
timeout 3 getent hosts controlplane.tailscale.com || echo "getent controlplane FAILED/timeout"
timeout 3 getent hosts github.com || echo "getent github FAILED/timeout"
# query systemd-resolved directly: if this answers while getent hangs, the wedge is in glibc/nss, not
# the resolver daemon
echo "--- resolvectl query (direct to resolved) ---"
timeout 5 resolvectl query controlplane.tailscale.com || echo "resolvectl query FAILED/timeout"

echo "--- conntrack count / max ---"
cat /proc/sys/net/netfilter/nf_conntrack_count /proc/sys/net/netfilter/nf_conntrack_max
echo "--- iptables nat rule count ---"
sudo iptables -t nat -S | wc -l
echo "--- iptables FORWARD policy ---"
sudo iptables -S FORWARD | head -3

echo "--- tailscaled procs ---"
pgrep -a tailscaled
echo "--- docker containers ---"
timeout 5 docker ps -a --format '{{.Names}}\t{{.Status}}\t{{.Image}}' || echo "docker ps FAILED/timeout"

echo "--- addr / route ---"
ip -brief addr
ip route

echo "--- systemd-resolved journal tail ---"
sudo journalctl -u systemd-resolved --no-pager -n 30
echo "########## END NET STATE [$label] ##########"
