$file = Get-Content "app/src/main/java/com/cinex/player/ui/screens/VideoPlayerScreen.kt"
$braceCount = 0
$parenCount = 0

for ($i = 1149; $i -lt 1230; $i++) {
    $line = $file[$i]
    $braceCount += ([regex]::Matches($line, '\{')).Count
    $braceCount -= ([regex]::Matches($line, '\}')).Count
    $parenCount += ([regex]::Matches($line, '\(')).Count
    $parenCount -= ([regex]::Matches($line, '\)')).Count
    
    Write-Host "Line $($i+1): braces=$braceCount parens=$parenCount | $($line.Substring(0, [Math]::Min(80, $line.Length)))"
}
