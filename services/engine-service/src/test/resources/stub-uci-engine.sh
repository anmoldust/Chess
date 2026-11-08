#!/bin/sh
# A stub UCI engine for tests: speaks just enough of the protocol to exercise the process layer.
# If the position contains 'nomove' it reports no legal move, so that path is testable too.
# POSIX twin of stub-uci-engine.ps1, used on non-Windows hosts (CI runs Linux).
position=""

while IFS= read -r line; do
    case "$line" in
        uci)
            echo "id name StubEngine"
            echo "uciok"
            ;;
        isready)
            echo "readyok"
            ;;
        setoption*)
            # accepted and ignored
            ;;
        position*)
            position="$line"
            ;;
        go*)
            if [ "${position#*nomove}" != "$position" ]; then
                echo "bestmove (none)"
            else
                echo "info depth 1 score cp 20"
                echo "bestmove e7e5 ponder b1c3"
            fi
            ;;
        quit)
            break
            ;;
    esac
done
