Write-Host "Analyzing ALL JMdict data types..." -ForegroundColor Cyan

$entries = Get-Content "JMdict_english_with_examples\term_bank_13.json" | ConvertFrom-Json | Select-Object -First 200

$dataTypes = @{}
$structureExamples = @{}

foreach ($entry in $entries) {
    $word = $entry[0]
    $glosses = $entry[5]
    
    if ($glosses -is [Array]) {
        foreach ($gloss in $glosses) {
            if ($gloss.type -eq "structured-content" -and $null -ne $gloss.content) {
                foreach ($contentItem in $gloss.content) {
                    if ($contentItem.tag -in @("ul", "ol")) {
                        $dataContent = $contentItem.data.content
                        if ($null -ne $dataContent) {
                            $dataTypes[$dataContent]++
                            
                            # Capture first example of each type
                            if (-not $structureExamples.ContainsKey($dataContent)) {
                                $structureExamples[$dataContent] = @{
                                    word = $word
                                    content = $contentItem.content
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

Write-Host "`n=== ALL DATA TYPES FOUND ===" -ForegroundColor Cyan
$dataTypes.GetEnumerator() | Sort-Object Value -Descending | ForEach-Object {
    Write-Host "  $($_.Key): $($_.Value) entries"
}

Write-Host "`n=== STRUCTURE EXAMPLES ===" -ForegroundColor Yellow
foreach ($type in $structureExamples.Keys | Sort-Object) {
    Write-Host "`n[$type] Example from: $($structureExamples[$type].word)" -ForegroundColor Green
    Write-Host ($structureExamples[$type].content | ConvertTo-Json -Depth 5 -Compress)
}
