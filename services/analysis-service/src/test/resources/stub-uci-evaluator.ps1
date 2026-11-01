# A stub UCI engine for the analysis evaluator tests: speaks just enough of the protocol.
# A position containing 'mate' yields a mate score; anything else yields a fixed centipawn score.
$position = ''

while ($true) {
    $line = [Console]::In.ReadLine()
    if ($null -eq $line) { break }

    if ($line -eq 'uci') {
        [Console]::Out.WriteLine('id name StubEvaluator')
        [Console]::Out.WriteLine('uciok')
        [Console]::Out.Flush()
    }
    elseif ($line -eq 'isready') {
        [Console]::Out.WriteLine('readyok')
        [Console]::Out.Flush()
    }
    elseif ($line -like 'position fen*') {
        $position = $line
    }
    elseif ($line -like 'go*') {
        if ($position -match 'mate') {
            [Console]::Out.WriteLine('info depth 1 score mate 2')
        } else {
            [Console]::Out.WriteLine('info depth 1 score cp 150')
        }
        [Console]::Out.WriteLine('bestmove a2a3')
        [Console]::Out.Flush()
    }
    elseif ($line -eq 'quit') {
        break
    }
}
