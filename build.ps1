$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

Remove-Item -Recurse -Force build, dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force build/classes, build/test-classes, dist | Out-Null

Get-ChildItem -Recurse src/main/java -Filter *.java |
  Sort-Object FullName |
  ForEach-Object { $_.FullName } |
  Set-Content build/main-sources.txt

javac --release 17 -d build/classes "@build/main-sources.txt"
jar --create --file dist/runtimelens.jar --main-class io.github.shashank022.runtimelens.Main -C build/classes .

Get-ChildItem -Recurse src/test/java -Filter *.java |
  Sort-Object FullName |
  ForEach-Object { $_.FullName } |
  Set-Content build/test-sources.txt

javac --release 17 -cp build/classes -d build/test-classes "@build/test-sources.txt"
java -cp "build/classes;build/test-classes" io.github.shashank022.runtimelens.RuntimeLensTest

Write-Host ""
Write-Host "Built: $Root/dist/runtimelens.jar"
