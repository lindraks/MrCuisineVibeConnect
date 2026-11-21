#!/system/bin/sh
# Run as root on the robot (tools\install-robot.ps1 does it after installing Toque).
# 1. Allows Toque in Magisk, without the on-screen prompt: that prompt closes by itself after
#    10 s and Magisk then refuses Toque for good (the robot has no way to reach Magisk's settings).
# 2. Installs a Magisk boot script that repeats step 1 at every start-up: the prompt can still time out
#    at boot (no one is there to answer) and Magisk then stores a refusal for Toque.
# 3. Makes sure Magisk's busybox is in /data/adb/magisk: Toque needs it for the serial port.
U=$(stat -c %u /data/data/app.toque 2>/dev/null)
[ -n "$U" ] || { echo "Toque is not installed"; exit 1; }
if magisk --sqlite "REPLACE INTO policies (uid,policy,until,logging,notification) VALUES($U,2,0,0,0)"; then
  echo "Toque allowed in Magisk"
fi
# Boot script (Magisk runs /data/adb/service.d/*.sh as root at every start-up).
mkdir -p /data/adb/service.d
cat > /data/adb/service.d/toque-su-policy.sh <<'BOOT'
#!/system/bin/sh
# Re-allows Toque in Magisk at boot (written by Toque's tools/robot/setup-root.sh).
LOG=/data/local/tmp/toque-su-policy.log
i=0
while [ ! -d /data/data/app.toque ] && [ $i -lt 120 ]; do sleep 2; i=$((i+1)); done
U=$(stat -c %u /data/data/app.toque 2>/dev/null)
[ -n "$U" ] || { echo "$(date) Toque absent" >> $LOG; exit 0; }
magisk --sqlite "REPLACE INTO policies (uid,policy,until,logging,notification) VALUES($U,2,0,0,0)"   && echo "$(date) Toque (uid $U) autorise" >> $LOG
BOOT
chmod 755 /data/adb/service.d/toque-su-policy.sh
chcon u:object_r:magisk_file:s0 /data/adb/service.d/toque-su-policy.sh 2>/dev/null
echo "Boot script installed (/data/adb/service.d/toque-su-policy.sh)"
if [ ! -x /data/adb/magisk/busybox ]; then
  for b in /data/app/com.topjohnwu.magisk*/lib/arm*/libbusybox.so; do
    [ -f "$b" ] || continue
    mkdir -p /data/adb/magisk
    cp "$b" /data/adb/magisk/busybox && chmod 755 /data/adb/magisk/busybox
    chcon u:object_r:magisk_file:s0 /data/adb/magisk/busybox 2>/dev/null
    echo "Magisk busybox installed"
    break
  done
fi
[ -x /data/adb/magisk/busybox ] || echo "WARNING: no busybox in /data/adb/magisk (install the Magisk app: adb install Magisk-vXX.apk)"
