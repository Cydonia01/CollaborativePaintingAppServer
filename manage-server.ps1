#!/usr/bin/env pwsh
# GCP Server Management Script
# Manages your collaborative painting server on Google Cloud Platform

param(
    [Parameter(Position=0)]
    [ValidateSet('start', 'stop', 'restart', 'status', 'logs', 'deploy', 'ssh', 'ip', 'help')]
    [string]$Command = 'help',
    
    [string]$Zone = 'us-central1-a',
    [string]$Instance = 'paint-server'
)

function Show-Help {
    Write-Host "GCP Server Management Script" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Usage: .\manage-server.ps1 [command]" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Commands:" -ForegroundColor Green
    Write-Host "  start     - Start the server"
    Write-Host "  stop      - Stop the server"
    Write-Host "  restart   - Restart the server"
    Write-Host "  status    - Check server status"
    Write-Host "  logs      - View server logs (real-time)"
    Write-Host "  deploy    - Build and deploy new version"
    Write-Host "  ssh       - SSH into the server"
    Write-Host "  ip        - Show server's public IP"
    Write-Host "  help      - Show this help"
    Write-Host ""
    Write-Host "Examples:" -ForegroundColor Magenta
    Write-Host "  .\manage-server.ps1 status"
    Write-Host "  .\manage-server.ps1 logs"
    Write-Host "  .\manage-server.ps1 deploy"
}

function Start-Server {
    Write-Host "Starting server..." -ForegroundColor Green
    gcloud compute ssh $Instance --zone=$Zone --command="sudo systemctl start paint-server"
    Write-Host "Server started!" -ForegroundColor Green
}

function Stop-Server {
    Write-Host "Stopping server..." -ForegroundColor Yellow
    gcloud compute ssh $Instance --zone=$Zone --command="sudo systemctl stop paint-server"
    Write-Host "Server stopped!" -ForegroundColor Yellow
}

function Restart-Server {
    Write-Host "Restarting server..." -ForegroundColor Cyan
    gcloud compute ssh $Instance --zone=$Zone --command="sudo systemctl restart paint-server"
    Write-Host "Server restarted!" -ForegroundColor Green
}

function Get-ServerStatus {
    Write-Host "Checking server status..." -ForegroundColor Cyan
    gcloud compute ssh $Instance --zone=$Zone --command="sudo systemctl status paint-server"
}

function Get-ServerLogs {
    Write-Host "Showing server logs (Ctrl+C to exit)..." -ForegroundColor Cyan
    gcloud compute ssh $Instance --zone=$Zone --command="sudo journalctl -u paint-server -f"
}

function Deploy-Server {
    Write-Host "Building project..." -ForegroundColor Cyan
    .\gradlew clean build
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Build successful! Uploading to server..." -ForegroundColor Green
        
        # Upload JAR
        gcloud compute scp app/build/libs/app.jar ${Instance}:~/ --zone=$Zone
        
        # Restart server
        Write-Host "Restarting server with new version..." -ForegroundColor Cyan
        gcloud compute ssh $Instance --zone=$Zone --command="sudo systemctl restart paint-server"
        
        Write-Host "Deployment complete!" -ForegroundColor Green
        Write-Host "Waiting for server to start..."
        Start-Sleep -Seconds 3
        Get-ServerStatus
    } else {
        Write-Host "Build failed!" -ForegroundColor Red
    }
}

function Connect-SSH {
    Write-Host "Connecting to server via SSH..." -ForegroundColor Cyan
    gcloud compute ssh $Instance --zone=$Zone
}

function Get-ServerIP {
    Write-Host "Getting server IP..." -ForegroundColor Cyan
    $ip = gcloud compute instances describe $Instance --zone=$Zone --format="get(networkInterfaces[0].accessConfigs[0].natIP)"
    Write-Host "Server IP: $ip" -ForegroundColor Green
    Write-Host "Server URL: $ip`:5000" -ForegroundColor Green
}

# Execute command
switch ($Command) {
    'start'   { Start-Server }
    'stop'    { Stop-Server }
    'restart' { Restart-Server }
    'status'  { Get-ServerStatus }
    'logs'    { Get-ServerLogs }
    'deploy'  { Deploy-Server }
    'ssh'     { Connect-SSH }
    'ip'      { Get-ServerIP }
    'help'    { Show-Help }
    default   { Show-Help }
}
