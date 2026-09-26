#!/bin/sh
# macOS only : wraps the native executable (or a JVM launch script) into target/Showcase.app, an application bundle, for
# the checks that need one : open files (a .qds document), open URIs (qdshowcase://...), print files, Dock icon and menu.
# LaunchServices routes these Apple events to an application by its bundle and Info.plist.
#
# usage (from the showcase directory) :
#   sh tools/mac-app-bundle.sh [native|jvm]
#   open target/Showcase.app --args -Dshowcase.interactive=true -Dshowcase.pages=desktop-mac-app-events
#   echo test > /tmp/a.qds && open -a target/Showcase.app /tmp/a.qds      # open files
#   open 'qdshowcase://ping?x=1'                                         # open URI
# The event log of the desktop-mac-app-events page shows the events. The bundle has an ad hoc signature : macOS asks
# again for the permissions (Screen Recording, Accessibility) after each build.
set -e
APP=target/Showcase.app
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS"
if [ "$1" = "jvm" ]; then
  JAVA="${JAVA_HOME:-$(/usr/libexec/java_home)}/bin/java"
  cat > "$APP/Contents/MacOS/showcase" <<EOF
#!/bin/sh
exec "$JAVA" --add-opens java.desktop/java.awt=ALL-UNNAMED --add-opens java.desktop/sun.lwawt=ALL-UNNAMED \
  -Dapple.awt.application.name=Showcase -jar "$PWD/target/quarkus-app/quarkus-run.jar" "\$@"
EOF
  chmod +x "$APP/Contents/MacOS/showcase"
else
  RUNNER=$(ls target/*-runner | head -1)
  cp "$RUNNER" "$APP/Contents/MacOS/showcase"
  cp target/*.dylib "$APP/Contents/MacOS/"
fi
cat > "$APP/Contents/Info.plist" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleIdentifier</key><string>io.quarkiverse.desktop.showcase</string>
  <key>CFBundleName</key><string>Showcase</string>
  <key>CFBundleExecutable</key><string>showcase</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>NSHighResolutionCapable</key><true/>
  <key>CFBundleURLTypes</key>
  <array><dict>
    <key>CFBundleURLName</key><string>Showcase URL</string>
    <key>CFBundleURLSchemes</key><array><string>qdshowcase</string></array>
  </dict></array>
  <key>CFBundleDocumentTypes</key>
  <array><dict>
    <key>CFBundleTypeName</key><string>Showcase document</string>
    <key>CFBundleTypeRole</key><string>Viewer</string>
    <key>CFBundleTypeExtensions</key><array><string>qds</string></array>
  </dict></array>
</dict>
</plist>
EOF
codesign --force --deep -s - "$APP"
/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister -f "$APP"
echo "$APP ready"
