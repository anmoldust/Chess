#!/bin/sh
# A stub UCI engine for the analysis evaluator tests: speaks just enough of the protocol.
# A position containing 'mate' yields a mate score; anything else yields a fixed centipawn score.
# POSIX twin of stub-uci-evaluator.ps1, used on non-Windows hosts (CI runs Linux).
position=""

while IFS= read -r line; do
    case "$line" in
        uci)
            echo "id name StubEvaluator"
            echo "uciok"
            ;;
        isready)
            echo "readyok"
            ;;
        position*)
            position="$line"
            ;;
        go*)
            if [ "${position#*mate}" != "$position" ]; then
                echo "info depth 1 score mate 2"
            else
                echo "info depth 1 score cp 150"
            fi
            echo "bestmove a2a3"
            ;;
        quit)
            break
            ;;
    esac
done
