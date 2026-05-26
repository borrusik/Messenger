$databaseFiles = @(
    "data\messenger.db",
    "data\messenger.db-shm",
    "data\messenger.db-wal"
)

foreach ($file in $databaseFiles) {
    if (Test-Path -LiteralPath $file) {
        Remove-Item -LiteralPath $file -Force
        Write-Host "Deleted $file"
    }
}

Write-Host "Database is clear. Start the server to create a fresh SQLite file."
