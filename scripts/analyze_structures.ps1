Write-Host "Analyzing JMdict entry structures..." -ForegroundColor Cyan

# Load a sample of entries (first 100 from term_bank_13.json)
$entries = Get-Content "JMdict_english_with_examples\term_bank_13.json" | ConvertFrom-Json | Select-Object -First 100

Write-Host "`nAnalyzing $($entries.Count) entries..." -ForegroundColor Yellow

$structureTypes = @{}
$glossaryContentTypes = @{}

foreach ($entry in $entries) {
    $word = $entry[0]
    $glosses = $entry[5]
    
    if ($glosses -is [Array]) {
        foreach ($gloss in $glosses) {
            if ($null -ne $gloss.type) {
                # Structured content
                $structureTypes[$gloss.type]++
                
                if ($gloss.type -eq "structured-content" -and $null -ne $gloss.content) {
                    foreach ($contentItem in $gloss.content) {
                        if ($contentItem.tag -in @("ul", "ol")) {
                            $dataContent = $contentItem.data.content
                            if ($dataContent -in @("glossary", "glosses")) {
                                # Found glossary list, check content type
                                if ($null -ne $contentItem.content) {
                                    $contentType = $contentItem.content.GetType().Name
                                    $key = "$dataContent-$contentType"
                                    $glossaryContentTypes[$key]++
                                    
                                    if ($glossaryContentTypes[$key] -le 2) {
                                        Write-Host "`n  Example: $word" -ForegroundColor Green
                                        Write-Host "    Type: $contentType"
                                        Write-Host "    Content: $($contentItem.content | ConvertTo-Json -Depth 2 -Compress)"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

Write-Host "`n=== STRUCTURE TYPES ===" -ForegroundColor Cyan
$structureTypes.GetEnumerator() | Sort-Object Value -Descending | ForEach-Object {
    Write-Host "  $($_.Key): $($_.Value)"
}

Write-Host "`n=== GLOSSARY CONTENT TYPES ===" -ForegroundColor Cyan
$glossaryContentTypes.GetEnumerator() | Sort-Object Key | ForEach-Object {
    Write-Host "  $($_.Key): $($_.Value)"
}
