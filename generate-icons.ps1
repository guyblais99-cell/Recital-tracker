Add-Type -AssemblyName System.Drawing

function Save-Icon {
    param([int]$Size, [string]$Path)
    $bmp = New-Object System.Drawing.Bitmap $Size, $Size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.Clear([System.Drawing.Color]::FromArgb(15, 23, 42))
    $rect = New-Object System.Drawing.Rectangle 0, 0, $Size, $Size
    $brush1 = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, `
        ([System.Drawing.Color]::FromArgb(236, 72, 153)), `
        ([System.Drawing.Color]::FromArgb(79, 70, 229)), 45
    $pad = [int]($Size * 0.15)
    $inner = [int]($Size * 0.7)
    $g.FillEllipse($brush1, $pad, $pad, $inner, $inner)
    $fontSize = [math]::Max(8, [int]($Size * 0.38))
    $font = New-Object System.Drawing.Font 'Segoe UI', $fontSize, ([System.Drawing.FontStyle]::Bold)
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = 'Center'
    $sf.LineAlignment = 'Center'
    $g.DrawString('R', $font, [System.Drawing.Brushes]::White, ($Size / 2), ($Size / 2), $sf)
    $g.Dispose()
    $brush1.Dispose()
    $font.Dispose()
    $bmp.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

$dir = Join-Path $PSScriptRoot '..\icons'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
Save-Icon -Size 192 -Path (Join-Path $dir 'icon-192.png')
Save-Icon -Size 512 -Path (Join-Path $dir 'icon-512.png')
Write-Host "Created icons in $dir"
