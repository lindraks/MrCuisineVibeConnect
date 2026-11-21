#!/sbin/sh
# Enables ADB (USB and network) on the stock Android, without root and without touching /system.
# Run it in TWRP, as root:
#   adb push <public keys, one per line> /tmp/adbkeys.pub
#   adb push tools\robot\twrp-enable-adb.sh /tmp/
#   adb shell sh /tmp/twrp-enable-adb.sh
#   adb pull /tmp/orig            (copies of the files this script replaced, if any)
#
# 1. Persistent properties in /data/property (read by init at boot, like the ones Android writes):
#      persist.sys.usb.config=mtp,adb   starts adbd on USB (the stock default.prop says "mtp");
#      persist.adb.tcp.port=5555        makes adbd also listen on the network.
# 2. Authorizes the given public keys in /data/misc/adb/adb_keys (ro.adb.secure=1 would otherwise
#    ask for a confirmation on the robot's screen, which the stock app may hide).
# The robot's own app and /system are not modified. Afterwards put the ORIGINAL boot image back
# into "boot" (TWRP was only there to run this script).
T=${T:-/tmp}
die() { echo "ERROR: $*"; exit 1; }

[ -s $T/adbkeys.pub ] || die "$T/adbkeys.pub missing or empty"

echo "== /data"
B=$(ls -d /dev/block/platform/*/by-name /dev/block/platform/*/*/by-name 2>/dev/null | head -n 1)
grep -q " /data " /proc/mounts || mount /data 2>/dev/null || mount -t ext4 $B/userdata /data
grep -q " /data " /proc/mounts || die "cannot mount /data"
[ -d /data/property ] || die "/data/property not found: is this the robot's data partition?"
rm -rf $T/orig; mkdir -p $T/orig

echo "== 1. Properties"
for kv in persist.sys.usb.config=mtp,adb persist.adb.tcp.port=5555; do
  n=${kv%%=*}; v=${kv#*=}
  [ -f /data/property/$n ] && cp -p /data/property/$n $T/orig/
  printf '%s' "$v" > /data/property/$n || die "cannot write $n"
  chown 0:0 /data/property/$n
  chmod 0600 /data/property/$n
  chcon u:object_r:property_data_file:s0 /data/property/$n 2>/dev/null
  echo "$n = $(cat /data/property/$n)"
done

echo "== 2. Authorized ADB keys"
mkdir -p /data/misc/adb
[ -f /data/misc/adb/adb_keys ] && cp -p /data/misc/adb/adb_keys $T/orig/
touch /data/misc/adb/adb_keys
n=0
while read -r key; do
  [ -n "$key" ] || continue
  grep -qxF "$key" /data/misc/adb/adb_keys || { echo "$key" >> /data/misc/adb/adb_keys; n=$((n + 1)); }
done < $T/adbkeys.pub
chown 1000:2000 /data/misc/adb /data/misc/adb/adb_keys
chmod 2750 /data/misc/adb
chmod 0640 /data/misc/adb/adb_keys
chcon u:object_r:adb_keys_file:s0 /data/misc/adb /data/misc/adb/adb_keys 2>/dev/null
echo "$n key(s) added, $(grep -c . /data/misc/adb/adb_keys) authorized in total"

sync
echo "== Done. Now put boot-original.img back into boot, then restart the robot."
