$word = Read-Host "Enter word to search (e.g., 日本, 食べる)"

Write-Host "`nQuerying database on device for: '$word'`n" -ForegroundColor Cyan

# Query directly on the device using the app's database
$result = adb shell "run-as com.godtap.dictionary sqlite3 databases/dictionary_database 'SELECT entryId, primaryExpression, primaryReading, dictionaryId, length(senses), substr(senses, 1, 300) FROM dictionary_entries WHERE primaryExpression = ''$word'' OR primaryReading = ''$word'' LIMIT 3;'"

if ($result) {
    Write-Host $result
    Write-Host "`nNote: Full senses column truncated to 300 chars" -ForegroundColor Yellow
} else {
    Write-Host "No results found for '$word'" -ForegroundColor Red
}

Write-Host "`nDone!" -ForegroundColor Green
