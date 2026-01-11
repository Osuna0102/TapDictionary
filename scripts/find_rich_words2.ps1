# Find words with rich dictionary data
$termBankPath = "JMdict_english_with_examples\term_bank_13.json"

Write-Host "=== SEARCHING FOR WORDS WITH RICH DATA ===" -ForegroundColor Cyan

$jsonContent = Get-Content $termBankPath -Raw | ConvertFrom-Json
$richWords = @()

foreach ($entry in $jsonContent) {
    $expression = $entry[0]
    $reading = $entry[1]
    $glossesJson = $entry[5]
    
    if ($glossesJson -is [Array] -and $glossesJson[0] -is [String]) {
        continue
    }
    
    $glossesStr = $glossesJson | ConvertTo-Json -Depth 10 -Compress
    
    $hasExamples = $glossesStr -match '"content":"examples"'
    $hasNotes = $glossesStr -match '"content":"notes"'
    $hasReferences = $glossesStr -match '"content":"references"'
    $hasAntonyms = $glossesStr -match '"content":"antonyms"'
    $hasSourceLang = $glossesStr -match '"content":"sourceLanguages"'
    
    $dataTypes = @()
    if ($hasExamples) { $dataTypes += "examples" }
    if ($hasNotes) { $dataTypes += "notes" }
    if ($hasReferences) { $dataTypes += "references" }
    if ($hasAntonyms) { $dataTypes += "antonyms" }
    if ($hasSourceLang) { $dataTypes += "etymology" }
    
    if ($dataTypes.Count -gt 0) {
        $richWords += [PSCustomObject]@{
            Expression = $expression
            Reading = $reading
            DataTypes = ($dataTypes -join ", ")
            DataCount = $dataTypes.Count
        }
    }
    
    if ($richWords.Count -ge 20) {
        break
    }
}

Write-Host ""
Write-Host "Found $($richWords.Count) words with rich data" -ForegroundColor Green
Write-Host ""

$richWords | Sort-Object -Property DataCount -Descending | ForEach-Object {
    Write-Host "$($_.Expression) ($($_.Reading))" -ForegroundColor Yellow
    Write-Host "  Data: $($_.DataTypes)" -ForegroundColor Gray
    Write-Host ""
}

Write-Host ""
Write-Host "=== TOP 5 RECOMMENDATIONS ===" -ForegroundColor Cyan
$top = $richWords | Sort-Object -Property DataCount -Descending | Select-Object -First 5
foreach ($word in $top) {
    Write-Host "* $($word.Expression) - $($word.DataCount) types: $($word.DataTypes)" -ForegroundColor Green
}
