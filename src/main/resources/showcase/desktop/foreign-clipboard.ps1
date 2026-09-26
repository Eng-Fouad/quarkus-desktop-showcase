# Foreign clipboard application of the dt-clipboard page (Windows PowerShell 5.1, Windows Forms) : reads what the
# showcase wrote to the system clipboard, then replaces it with other data, as another application would. The showcase
# then reads that data through the native clipboard code of AWT (a read of its own data never leaves the JVM).
# Inputs : environment variables SHOWCASE_*. Output : one "key=value" line per result, text values in base64 (UTF-8),
# "-" when a value is absent.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

function Write-Result([string] $key, [string] $value) {
    [Console]::Out.WriteLine($key + '=' + $value)
}

function Write-Text([string] $key, $text) {
    if ($null -eq $text) {
        Write-Result $key '-'
    } else {
        Write-Result $key ([Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([string] $text)))
    }
}

function Get-Bytes($data) {
    if ($data -is [System.IO.MemoryStream]) {
        return , $data.ToArray()
    }
    return $null
}

function New-Stream([byte[]] $bytes) {
    return , (New-Object System.IO.MemoryStream (, $bytes))
}

# ------------------------------------------------------------------------ 1. what the showcase wrote (native formats)
$data = $null
for ($i = 0; $null -eq $data -and $i -lt 30; $i++) {
    try {
        $data = [System.Windows.Forms.Clipboard]::GetDataObject()
    } catch {
        Start-Sleep -Milliseconds 100
    }
}
if ($null -eq $data) {
    Write-Result 'error' 'the clipboard cannot be opened'
    exit 2
}
$formats = @($data.GetFormats($false) | Sort-Object)
Write-Text 'formats' ($formats -join "`n")
Write-Text 'text' $data.GetData([System.Windows.Forms.DataFormats]::UnicodeText, $false)
Write-Text 'html' $data.GetData([System.Windows.Forms.DataFormats]::Html, $false)
Write-Text 'rtf' $data.GetData([System.Windows.Forms.DataFormats]::Rtf, $false)
$bitmap = $data.GetData([System.Windows.Forms.DataFormats]::Bitmap, $true)
if ($bitmap -is [System.Drawing.Bitmap]) {
    Write-Result 'image' ('{0}x{1} {2:X8} {3:X8}' -f $bitmap.Width, $bitmap.Height, $bitmap.GetPixel(2, 2).ToArgb(),
            $bitmap.GetPixel($bitmap.Width - 3, $bitmap.Height - 3).ToArgb())
} else {
    Write-Result 'image' '-'
}
$files = $data.GetData([System.Windows.Forms.DataFormats]::FileDrop, $false)
if ($files) {
    Write-Text 'files' ((@($files) | ForEach-Object { [System.IO.Path]::GetFileName($_) }) -join "`n")
} else {
    Write-Result 'files' '-'
}
$url = Get-Bytes ($data.GetData('UniformResourceLocator', $false))
if ($url) {
    Write-Text 'url' ([Text.Encoding]::ASCII.GetString($url).TrimEnd([char] 0))
} else {
    Write-Result 'url' '-'
}
$serialFormat = $env:SHOWCASE_SERIAL_FORMAT
$serialized = Get-Bytes ($data.GetData($serialFormat, $false))
if ($serialized) {
    Write-Result 'serialized' $serialized.Length
} else {
    Write-Result 'serialized' '-'
}

# ------------------------------------------------------------------------------------------ 2. foreign data
$object = New-Object System.Windows.Forms.DataObject
$object.SetData([System.Windows.Forms.DataFormats]::UnicodeText, $env:SHOWCASE_FOREIGN_TEXT)

# CF_HTML : a header with the byte offsets of the document and of the fragment (UTF-8)
$fragment = $env:SHOWCASE_FOREIGN_HTML
$before = '<html><body><!--StartFragment-->'
$after = '<!--EndFragment--></body></html>'
$header = "Version:0.9`r`nStartHTML:{0:D10}`r`nEndHTML:{1:D10}`r`nStartFragment:{2:D10}`r`nEndFragment:{3:D10}`r`n"
$utf8 = [Text.Encoding]::UTF8
$startHtml = ($header -f 0, 0, 0, 0).Length
$startFragment = $startHtml + $utf8.GetByteCount($before)
$endFragment = $startFragment + $utf8.GetByteCount($fragment)
$endHtml = $endFragment + $utf8.GetByteCount($after)
$object.SetData([System.Windows.Forms.DataFormats]::Html,
        ($header -f $startHtml, $endHtml, $startFragment, $endFragment) + $before + $fragment + $after)

$object.SetData([System.Windows.Forms.DataFormats]::Rtf, $env:SHOWCASE_FOREIGN_RTF)

$image = New-Object System.Drawing.Bitmap 40, 30
$graphics = [System.Drawing.Graphics]::FromImage($image)
$graphics.Clear([System.Drawing.Color]::FromArgb(255, 0x2E, 0x7D, 0x32))
$brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 0xFF, 0xB3, 0x00))
$graphics.FillRectangle($brush, 5, 5, 10, 10)
$brush.Dispose()
$graphics.Dispose()
$object.SetData([System.Windows.Forms.DataFormats]::Bitmap, $true, $image)

$drop = New-Object System.Collections.Specialized.StringCollection
[void] $drop.Add($env:SHOWCASE_FOREIGN_FILE)
$object.SetFileDropList($drop)

$object.SetData('UniformResourceLocator', (New-Stream ([Text.Encoding]::ASCII.GetBytes($env:SHOWCASE_FOREIGN_URL + [char] 0))))
if ($serialized) {
    # the serialized Java object written by the showcase, handed back unchanged
    $object.SetData($serialFormat, (New-Stream $serialized))
}
# keep this data out of the Windows clipboard history and of clipboard monitors
$object.SetData('CanIncludeInClipboardHistory', (New-Stream ([byte[]] (0, 0, 0, 0))))
$object.SetData('ExcludeClipboardContentFromMonitorProcessing', (New-Stream ([byte[]] (0, 0, 0, 0))))

[System.Windows.Forms.Clipboard]::SetDataObject($object, $true, 30, 100)
Write-Result 'written' 'true'
