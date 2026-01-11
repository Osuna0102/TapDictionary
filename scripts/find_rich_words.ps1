# Find words with rich dictionary data (examples, notes, references, etc.)
$termBankPath = "JMdict_english_with_examples\term_bank_13.json"

Write-Host "=== SEARCHING FOR WORDS WITH RICH DATA ===" -ForegroundColor Cyan
Write-Host ""

$jsonContent = Get-Content $termBankPath -Raw | ConvertFrom-Json
$richWords = @()

foreach ($entry in $jsonContent) {
    $expression = $entry[0]
    $reading = $entry[1]
    $glossesJson = $entry[5]
    
    # Skip if glosses is just a plain array
    if ($glossesJson -is [Array] -and $glossesJson[0] -is [String]) {
        continue
    }
    
    # Convert glosses to string and check for rich data markers
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
            DataTypes = $dataTypes -join ", "
            DataCount = $dataTypes.Count
        }
    }
    
    # Stop after finding 20 rich words
    if ($richWords.Count -ge 20) {
        break
    }
}

Write-Host "Found $($richWords.Count) words with rich data:" -ForegroundColor Green
Write-Host ""

$richWords | Sort-Object -Property DataCount -Descending | ForEach-Object {
    Write-Host "📚 $($_.Expression) ($($_.Reading))" -ForegroundColor Yellow
    Write-Host "   Data: $($_.DataTypes)" -ForegroundColor Gray
    Write-Host ""
}

Write-Host ""
Write-Host "=== TOP RECOMMENDATIONS ===" -ForegroundColor Cyan
$top = $richWords | Sort-Object -Property DataCount -Descending | Select-Object -First 5
foreach ($word in $top) {
    $expr = $word.Expression
    $count = $word.DataCount
    $types = $word.DataTypes
    Write-Host "* $expr - has $count data types: $types" -ForegroundColor Green
}
