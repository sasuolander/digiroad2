param(
    [Parameter()][string]$sourcePassword,
    [Parameter()][string]$sourceUser,
    [Parameter()][string]$sourceDB,
    [Parameter()][string]$sourcePort,
    [Parameter()][string]$destinationPassword,
    [Parameter()][string]$municipalities,
    [Parameter()][Boolean]$truncateBoolean
)

# Before running this script set your postgress installation bin(C:\'Program Files'\PostgreSQL\13\bin\) folder into PATH env

$absolutePath = Get-Location
# .\importRoadlink.ps1 -municipalities  "20,10" -sourceUser digiroad2dbuser -sourcePassword password  -sourceDB digiroad2 -sourcePort 9999 -destinationPassword digiroad2 -truncateBoolean 1
$datasource = "postgresql://${sourceUser}:${sourcePassword}@localhost:${sourcePort}/${sourceDB}"
$destinationpoint = "postgresql://digiroad2:${destinationPassword}@localhost:5432/digiroad2"

$truncateroadlink   = "psql -c 'truncate kgv_roadlink;' ${destinationpoint}"
$truncatecomplimentary  = "psql -c 'truncate qgis_roadlinkex;' ${destinationpoint}"

$roadlink1 = "psql -c '\COPY (SELECT * FROM kgv_roadlink WHERE municipalitycode in (${municipalities})) TO ''${absolutePath}\tempSQL.sql'' (ENCODING ''UTF8'');' ${datasource}"
$roadlink2 = "psql -c '\COPY kgv_roadlink FROM ''${absolutePath}\tempSQL.sql'';' ${destinationpoint}"

$complimentary1 = "psql -c '\COPY (SELECT * FROM qgis_roadlinkex WHERE municipalitycode in (${municipalities})) TO ''${absolutePath}\tempSQL2.sql'' (ENCODING ''UTF8'');' ${datasource}"
$complimentary2 = "psql -c '\COPY qgis_roadlinkex FROM ''${absolutePath}\tempSQL2.sql'';' ${destinationpoint}"

if ($truncateBoolean)
{
    Write-Output "truncate"
    Invoke-expression $truncateroadlink
    Invoke-expression $truncatecomplimentary
}
Write-Output "importing ${municipalities}"
Invoke-expression $roadlink1
Invoke-expression $roadlink2

Invoke-expression $complimentary1
Invoke-expression $complimentary2
Write-Output "finish"