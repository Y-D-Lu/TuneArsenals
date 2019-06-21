#!/system/bin/sh

# 工具函数
function set_rw()
{
    if [[ -f "$1" ]];
    then
        chmod 0664 "$1"
    fi
}

# 工具函数
function set_value()
{
    if [[ -f "$1" ]];
    then
        chmod 0664 "$1"
        echo "$2" > "$1"
    fi
}

paths=`ls /sys/class/power_supply/*/constant_charge_current_max`
# 更改限制 change_limit ?mA
function change_limit() {
    echo "更改限制值为：${1}mA"
    local limit="${1}000"

    for path in $paths
    do
        echo $limit > $path
    done

    if [[ -f /sys/class/qcom-battery/restricted_current ]]; then
        echo $limit > /sys/class/qcom-battery/restricted_current
    fi
}

set_value /sys/class/qcom-battery/restricted_charging 0
set_value /sys/class/power_supply/battery/restricted_charging 0
set_value /sys/class/power_supply/usb/pd_allowed 1
set_value /sys/class/power_supply/allow_hvdcp3 1
set_value /sys/class/power_supply/battery/subsystem/usb/pd_allowed 1
set_value /sys/class/power_supply/battery/safety_timer_enabled 0
set_value /sys/class/power_supply/bms/temp_warm 500
set_rw /sys/class/power_supply/main/constant_charge_current_max
set_rw /sys/class/power_supply/battery/constant_charge_current_max
set_rw /sys/class/qcom-battery/restricted_current

for path in $paths
do
    chmod 0664 $path
done

# 部分设备不支持超过3000的值，所以首次执行先设为3000吧
change_limit 3000
