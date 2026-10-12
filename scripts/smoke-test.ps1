<#
.SYNOPSIS
    End-to-end scenario against a running local stack (docker compose up), through the API gateway.

.DESCRIPTION
    Onboarding with four-eyes approval, sign-in account provisioning, account opening, cash deposit,
    customer transfer, idempotent retry, access-control checks and e-mail notifications.
    Works with Windows PowerShell 5.1 and PowerShell 7+.

    The customer password is set through the Keycloak admin API to keep the script unattended; in
    real life the customer receives an e-mail (visible in Mailpit) to choose it.

.EXAMPLE
    ./scripts/smoke-test.ps1
#>
param(
    [string] $Gateway = 'http://localhost:8080',
    [string] $Keycloak = 'http://localhost:8180',
    [string] $Mailpit = 'http://localhost:8025',
    [string] $EnvFile = (Join-Path $PSScriptRoot '..\.env')
)

$ErrorActionPreference = 'Stop'
$Realm = 'atlas-bank'

# --- helpers ------------------------------------------------------------------------------------
function Read-DotEnv([string] $Path) {
    $values = @{}
    Get-Content $Path | Where-Object { $_ -match '^\s*[A-Z0-9_]+=' } | ForEach-Object {
        $name, $value = $_ -split '=', 2
        $values[$name.Trim()] = $value.Trim()
    }
    return $values
}

function Step([string] $Message) { Write-Host "`n==> $Message" -ForegroundColor Cyan }
function Ok([string] $Message) { Write-Host "    [OK] $Message" -ForegroundColor Green }
function Fail([string] $Message) { Write-Host "    [FAIL] $Message" -ForegroundColor Red; exit 1 }

function Invoke-Api {
    param([string] $Method, [string] $Uri, [string] $Token, $Body, [hashtable] $Headers = @{})
    $allHeaders = @{} + $Headers
    if ($Token) { $allHeaders['Authorization'] = "Bearer $Token" }
    $request = @{ Method = $Method; Uri = $Uri; Headers = $allHeaders; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $request['ContentType'] = 'application/json'
        $request['Body'] = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 10))
    }
    try {
        $response = Invoke-WebRequest @request
        $json = if ($response.Content) { $response.Content | ConvertFrom-Json } else { $null }
        return [pscustomobject]@{ Status = [int] $response.StatusCode; Body = $json; Headers = $response.Headers }
    } catch {
        $status = [int] $_.Exception.Response.StatusCode
        $json = $null
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            try { $json = $_.ErrorDetails.Message | ConvertFrom-Json } catch { $json = $_.ErrorDetails.Message }
        }
        return [pscustomobject]@{ Status = $status; Body = $json; Headers = @{} }
    }
}

function Expect($Response, [int] $Status, [string] $What) {
    if ($Response.Status -ne $Status) {
        Fail "$What : expected HTTP $Status, got $($Response.Status) $($Response.Body | ConvertTo-Json -Depth 5 -Compress)"
    }
    Ok "$What (HTTP $Status)"
}

function Wait-Until([scriptblock] $Condition, [string] $What, [int] $Seconds = 60) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        $result = & $Condition
        if ($result) { Ok $What; return $result }
        Start-Sleep -Milliseconds 500
    }
    Fail "$What (timed out after $Seconds s)"
}

function Get-UserToken([string] $Username, [string] $Password) {
    $token = Invoke-RestMethod -Method Post -Uri "$Keycloak/realms/$Realm/protocol/openid-connect/token" -Body @{
        grant_type = 'password'; client_id = 'atlas-dev-cli'; username = $Username; password = $Password; scope = 'openid'
    }
    return $token.access_token
}

function Get-KeycloakAdminToken([string] $Password) {
    $token = Invoke-RestMethod -Method Post -Uri "$Keycloak/realms/master/protocol/openid-connect/token" -Body @{
        grant_type = 'password'; client_id = 'admin-cli'; username = 'admin'; password = $Password
    }
    return $token.access_token
}

function New-IdempotencyKey { return [guid]::NewGuid().ToString() }

# --- scenario -----------------------------------------------------------------------------------
$secrets = Read-DotEnv $EnvFile
$suffix = Get-Random -Minimum 100000 -Maximum 999999
$customerEmail = "salma.benali.$suffix@example.ma"
$customerPassword = "Atlas-Demo-$suffix!x"

Step 'Staff sign-in (Keycloak, OpenID Connect)'
$amina = Get-UserToken 'agent.amina' $secrets['ATLAS_DEMO_EMPLOYEE_PASSWORD']
$youssef = Get-UserToken 'agent.youssef' $secrets['ATLAS_DEMO_EMPLOYEE_PASSWORD']
Ok 'agent.amina and agent.youssef authenticated'

Step 'Security at the edge'
Expect (Invoke-Api GET "$Gateway/api/v1/customers") 401 'Anonymous call rejected'
Expect (Invoke-Api GET "$Gateway/api/v1/customers/$([guid]::NewGuid())/history" $amina) 403 'Audit trail restricted to administrators'

Step 'Customer onboarding (maker)'
$registration = Invoke-Api POST "$Gateway/api/v1/customers" $amina @{
    firstName = 'Salma'; lastName = 'Benali'; email = $customerEmail; phoneNumber = '0612345678'
    dateOfBirth = '1992-04-18'; nationalId = "BK$suffix"; preferredLanguage = 'fr'
    address = @{ line = '12 avenue Mohammed V'; city = 'Rabat'; postalCode = '10000'; country = 'MA' }
}
Expect $registration 201 'Customer registered, pending verification'
$customerId = $registration.Body.id
Write-Host "    customer $customerId, national id shown as $($registration.Body.nationalId)"

Step 'KYC approval (checker): four-eyes principle'
$selfApproval = Invoke-Api POST "$Gateway/api/v1/customers/$customerId/verification" $amina
Expect $selfApproval 403 'The registrar cannot approve her own file'
Expect (Invoke-Api POST "$Gateway/api/v1/customers/$customerId/verification" $youssef) 200 'File approved by another employee'

Step 'Sign-in account provisioned asynchronously (outbox -> Keycloak admin API)'
Wait-Until { (Invoke-Api GET "$Gateway/api/v1/customers/$customerId" $amina).Body.identityProvisioned } 'Keycloak account created' | Out-Null

$adminToken = Get-KeycloakAdminToken $secrets['KEYCLOAK_ADMIN_PASSWORD']
$kcUser = (Invoke-RestMethod -Headers @{ Authorization = "Bearer $adminToken" } `
        -Uri "$Keycloak/admin/realms/$Realm/users?username=$([uri]::EscapeDataString($customerEmail))&exact=true")[0]
Invoke-RestMethod -Method Put -Headers @{ Authorization = "Bearer $adminToken" } -ContentType 'application/json' `
    -Uri "$Keycloak/admin/realms/$Realm/users/$($kcUser.id)/reset-password" `
    -Body (@{ type = 'password'; value = $customerPassword; temporary = $false } | ConvertTo-Json) | Out-Null
Invoke-RestMethod -Method Put -Headers @{ Authorization = "Bearer $adminToken" } -ContentType 'application/json' `
    -Uri "$Keycloak/admin/realms/$Realm/users/$($kcUser.id)" `
    -Body (@{ emailVerified = $true; requiredActions = @() } | ConvertTo-Json) | Out-Null
$customer = Get-UserToken $customerEmail $customerPassword
Ok 'Customer signed in; the token carries the customer_id claim'
$me = Invoke-Api GET "$Gateway/api/v1/customers/me" $customer
Expect $me 200 'Customer reads her own profile'
if ($me.Body.id -ne $customerId) { Fail 'The token is bound to the wrong customer' }

Step 'Account opening (eligibility read from the local projection of customers)'
$current = Wait-Until {
    $r = Invoke-Api POST "$Gateway/api/v1/accounts" $amina @{ customerId = $customerId; type = 'CURRENT'; currency = 'MAD' }
    if ($r.Status -eq 201) { $r.Body }
} 'Current account opened'
$savings = (Invoke-Api POST "$Gateway/api/v1/accounts" $amina @{ customerId = $customerId; type = 'SAVINGS'; currency = 'MAD' }).Body
Write-Host "    current $($current.accountNumber) / savings $($savings.accountNumber)"

Step 'Cash deposit at the counter (asynchronous order, settled by the ledger)'
$deposit = Invoke-Api POST "$Gateway/api/v1/transactions/deposits" $amina @{
    accountNumber = $current.accountNumber; amount = 1000.00; currency = 'MAD'; description = 'Cash deposit'
} @{ 'Idempotency-Key' = (New-IdempotencyKey) }
Expect $deposit 202 "Deposit accepted ($($deposit.Body.reference))"
Wait-Until { (Invoke-Api GET "$Gateway/api/v1/transactions/$($deposit.Body.id)" $amina).Body.status -eq 'COMPLETED' } 'Deposit posted' | Out-Null

Step 'Customer transfer with an idempotency key'
$key = New-IdempotencyKey
$transferBody = @{ sourceAccountNumber = $current.accountNumber; targetAccountNumber = $savings.accountNumber
                   amount = 250.00; currency = 'MAD'; description = 'Monthly savings' }
$transfer = Invoke-Api POST "$Gateway/api/v1/transactions/transfers" $customer $transferBody @{ 'Idempotency-Key' = $key }
Expect $transfer 202 "Transfer accepted ($($transfer.Body.reference))"
$retry = Invoke-Api POST "$Gateway/api/v1/transactions/transfers" $customer $transferBody @{ 'Idempotency-Key' = $key }
if ($retry.Body.id -ne $transfer.Body.id -or "$($retry.Headers['Idempotent-Replayed'])" -ne 'true') { Fail 'Retry was not replayed' }
Ok 'Retry with the same key replayed the original order (no double debit)'
Wait-Until { (Invoke-Api GET "$Gateway/api/v1/transactions/$($transfer.Body.id)" $customer).Body.status -eq 'COMPLETED' } 'Transfer posted' | Out-Null

Step 'Balances and statement'
$balances = (Invoke-Api GET "$Gateway/api/v1/accounts" $customer).Body.content
$currentBalance = ($balances | Where-Object accountNumber -eq $current.accountNumber).balance
$savingsBalance = ($balances | Where-Object accountNumber -eq $savings.accountNumber).balance
if ([decimal] $currentBalance -ne 750 -or [decimal] $savingsBalance -ne 250) { Fail "Unexpected balances $currentBalance / $savingsBalance" }
Ok "Current account 750.00 MAD, savings account 250.00 MAD"
$statement = Invoke-Api GET "$Gateway/api/v1/accounts/$($current.id)/entries" $customer
Ok "Statement: $($statement.Body.page.totalElements) entries"

Step 'Access control on money'
Expect (Invoke-Api POST "$Gateway/api/v1/transactions/deposits" $customer @{
    accountNumber = $current.accountNumber; amount = 1; currency = 'MAD' } @{ 'Idempotency-Key' = (New-IdempotencyKey) }) 403 'Customers cannot record cash deposits'
$overdraft = Invoke-Api POST "$Gateway/api/v1/transactions/transfers" $customer @{
    sourceAccountNumber = $savings.accountNumber; targetAccountNumber = $current.accountNumber; amount = 999.00; currency = 'MAD'
} @{ 'Idempotency-Key' = (New-IdempotencyKey) }
Wait-Until {
    $order = (Invoke-Api GET "$Gateway/api/v1/transactions/$($overdraft.Body.id)" $customer).Body
    $order.status -eq 'REJECTED' -and $order.rejectionCode -eq 'INSUFFICIENT_FUNDS'
} 'Savings cannot go overdrawn: order rejected, nothing moved' | Out-Null

Step 'Notifications'
Wait-Until { (Invoke-Api GET "$Gateway/api/v1/notifications" $customer).Body.page.totalElements -ge 4 } 'Customer notifications recorded' | Out-Null
$mails = Invoke-RestMethod -Uri "$Mailpit/api/v1/search?query=$([uri]::EscapeDataString("to:$customerEmail"))"
Ok "$($mails.messages_count) e-mails delivered to $customerEmail (see $Mailpit)"

Write-Host "`nAll checks passed." -ForegroundColor Green
