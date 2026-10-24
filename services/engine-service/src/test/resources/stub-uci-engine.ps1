# A stub UCI engine for tests: speaks just enough of the protocol to exercise the process layer.
# If the position contains 'nomove' it reports no legal move, so that path is testable too.
$position = ''

while ($true) {
    $line = [Console]::In.ReadLine()
    if ($null -eq $line) { break }

    if ($line -eq 'uci') {
        [Console]::Out.WriteLine('id name StubEngine')
        [Console]::Out.WriteLine('uciok')
        [Console]::Out.Flush()
    }
    elseif ($line -eq 'isready') {
        [Console]::Out.WriteLine('readyok')
        [Console]::Out.Flush()
    }
    elseif ($line -like 'setoption*') {
        # accepted and ignored
    }
    elseif ($line -like 'position fen*') {
        $position = $line
    }
    elseif ($line -like 'go*') {
        if ($position -match 'nomove') {
            [Console]::Out.WriteLine('bestmove (none)')
        } else {
            [Console]::Out.WriteLine('info depth 1 score cp 20')
            [Console]::Out.WriteLine('bestmove e7e5 ponder b1c3')
        }
        [Console]::Out.Flush()
    }
    elseif ($line -eq 'quit') {
        break
    }
}
