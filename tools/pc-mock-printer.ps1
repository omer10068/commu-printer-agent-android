# pc-mock-printer.ps1
# Minimal TCP receiver that acts as a stand-in for a network printer on port 9100.
#
# - Accepts sequential connections and saves each non-empty payload to a timestamped
#   .bin file so individual print jobs can be inspected separately.
# - Zero-byte connections are logged as scan probes and skipped; they do not increment
#   the job counter or create files.  The Android LAN scanner opens and immediately
#   closes a TCP connection without sending any data.
# - Shows the first bytes of every received payload in hex without assuming any
#   particular byte sequence (ESC/POS streams vary by printer model and job type).
#
# Usage:
#   .\tools\pc-mock-printer.ps1           # listens on default port 9100
#   .\tools\pc-mock-printer.ps1 -Port 9200
#
# Prerequisites:
#   An inbound Windows Firewall rule for the chosen port must exist before starting.
#   See MILESTONE_6_TWO_PRINTER_TEST_REVIEW.md Section 9.2 for the exact command.

param([int]$Port = 9100)

$outDir = "$env:TEMP\mock-printer-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
New-Item -ItemType Directory -Path $outDir | Out-Null

$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $Port)
$listener.Start()
Write-Host "[$(Get-Date -Format 'HH:mm:ss')] PC Mock Printer listening on :$Port"
Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Output directory: $outDir"
Write-Host "Press Ctrl+C to stop."

$jobIndex = 0
try {
    while ($true) {
        $client = $listener.AcceptTcpClient()
        $remote = $client.Client.RemoteEndPoint
        $tsIn   = Get-Date -Format 'HH:mm:ss.fff'

        $stream = $client.GetStream()
        $stream.ReadTimeout = 5000   # 5 s safety — PrinterChannel always closes after writing

        $ms  = [System.IO.MemoryStream]::new()
        $buf = New-Object byte[] 4096
        try {
            while ($true) {
                $n = $stream.Read($buf, 0, $buf.Length)
                if ($n -le 0) { break }
                $ms.Write($buf, 0, $n)
            }
        } catch [System.IO.IOException] {
            # Normal: client closed connection or 5 s read timeout elapsed
        } finally {
            $client.Close()
        }

        $bytes = $ms.ToArray()

        # Zero-byte connection — LAN scan probe, not a print job
        if ($bytes.Length -eq 0) {
            Write-Host "[$tsIn] Scan probe from $remote (0 bytes, ignored)"
            continue
        }

        $jobIndex++
        $outFile = "$outDir\job-$($jobIndex.ToString('D3'))-$(Get-Date -Format 'HHmmss').bin"
        [System.IO.File]::WriteAllBytes($outFile, $bytes)

        $tsOut = Get-Date -Format 'HH:mm:ss.fff'
        Write-Host ""
        Write-Host "[$tsOut] Job #$jobIndex  from $remote  —  $($bytes.Length) bytes"
        Write-Host "[$tsOut]   Saved: $outFile"

        # First bytes in hex — up to 16, no assumption about content
        $showCount = [Math]::Min(16, $bytes.Length)
        $hexLine   = ($bytes[0..($showCount - 1)] | ForEach-Object { '{0:X2}' -f $_ }) -join ' '
        Write-Host "[$tsOut]   First $showCount bytes: $hexLine"

        # Printable ASCII preview — control bytes shown as '.'
        $preview = [System.Text.Encoding]::UTF8.GetString($bytes) -replace '[^\x20-\x7E]', '.'
        if ($preview.Length -gt 120) { $preview = $preview.Substring(0, 120) + '...' }
        Write-Host "[$tsOut]   Preview: $preview"
    }
} finally {
    $listener.Stop()
    Write-Host ""
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Stopped. $jobIndex job(s) saved to $outDir"
}
