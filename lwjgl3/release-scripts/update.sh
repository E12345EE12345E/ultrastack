#!/bin/sh
cd "$(dirname "$0")"
echo "Close UltraStack before updating."
echo
"./jdk/bin/java" Update.java
status=$?
echo
if [ "$status" -ne 0 ]; then
    echo "Update failed. Exit Code: $status"
fi
if [ -t 0 ]; then
    printf "Press Enter to close..."
    read dummy
fi
exit "$status"
