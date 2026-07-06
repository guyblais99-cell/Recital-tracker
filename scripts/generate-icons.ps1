Add-Type -AssemblyName System.Drawing

function Save-HuntIcon {
    param([int]$Size, [string]$OutPath)

    $bmp = New-Object System.Drawing.Bitmap $Size, $Size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.PixelOffsetMode = 'HighQuality'

    $bg = [System.Drawing.Color]::FromArgb(42, 18, 69)
    $green = [System.Drawing.Color]::FromArgb(57, 255, 20)
    $gold = [System.Drawing.Color]::FromArgb(255, 217, 61)
    $cyan = [System.Drawing.Color]::FromArgb(0, 212, 255)

    $g.Clear($bg)

    $center = $Size / 2.0
    $ringSize = [int]($Size * 0.547)
    $ringPad = [int](($Size - $ringSize) / 2)
    $ringPen = New-Object System.Drawing.Pen $green, ([math]::Max(2, $Size * 0.047))
    $g.DrawEllipse($ringPen, $ringPad, $ringPad, $ringSize, $ringSize)

    $dotSize = [int]($Size * 0.094)
    $dotPad = [int](($Size - $dotSize) / 2)
    $g.FillEllipse((New-Object System.Drawing.SolidBrush $gold), $dotPad, $dotPad, $dotSize, $dotSize)

    $crossLen = [int]($Size * 0.117)
    $crossOut = [int]($Size * 0.156)
    $crossPen = New-Object System.Drawing.Pen $cyan, ([math]::Max(2, $Size * 0.039))
    $crossPen.StartCap = 'Round'
    $crossPen.EndCap = 'Round'
    $g.DrawLine($crossPen, $center, $crossOut, $center, $crossLen)
    $g.DrawLine($crossPen, $center, $Size - $crossOut, $center, $Size - $crossLen)
    $g.DrawLine($crossPen, $crossOut, $center, $crossLen, $center)
    $g.DrawLine($crossPen, $Size - $crossOut, $center, $Size - $crossLen, $center)

    $ringPen.Dispose()
    $crossPen.Dispose()
    $g.Dispose()
    $bmp.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Save-HuntIcon -Size 192 -OutPath (Join-Path $root 'icon-192.png')
Save-HuntIcon -Size 512 -OutPath (Join-Path $root 'icon-512.png')
Write-Host "Created scavenger hunt icons in $root"
