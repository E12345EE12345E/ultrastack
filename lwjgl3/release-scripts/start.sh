#!/bin/sh
cd "$(dirname "$0")"

# If the glob matches nothing, the literal pattern is left in $1.
set -- UltraStack-*.jar
if [ ! -f "$1" ]; then
    echo "ERROR: No UltraStack-*.jar found. Run the updater first."
    exit 1
fi
if [ "$#" -gt 1 ]; then
    echo "ERROR: Multiple UltraStack-*.jar files found. Leave only one, or run the updater."
    exit 1
fi

FIRST_THREAD=""
if [ "$(uname -s)" = "Darwin" ]; then
    FIRST_THREAD="-XstartOnFirstThread"
fi

exec "./jdk/bin/java" $FIRST_THREAD --enable-native-access=ALL-UNNAMED -jar "$1"
