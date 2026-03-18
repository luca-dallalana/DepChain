#!/bin/bash

# Absolute paths (update these to your actual project locations)
SERVER_DIR="/Users/ines/Desktop/SEC/project/SEC_project/server"
APP_DIR="/Users/ines/Desktop/SEC/project/SEC_project/app"
MAIN_DIR="/Users/ines/Desktop/SEC/project/SEC_project"

(cd "$MAIN_DIR" && mvn compile)

# Function to open a new Terminal tab and run a command
open_terminal_tab() {
  osascript <<EOF
  tell application "Terminal"
    activate
    tell application "System Events" to keystroke "t" using {command down}
    delay 0.5
    do script "cd '$1' && $2" in front window
  end tell
EOF
}

# First server process in current tab
osascript <<EOF
  tell application "Terminal"
    activate
    do script "cd '$SERVER_DIR' && mvn exec:java -Dexec.args='0 4'" in front window
  end tell
EOF

# Remaining server processes in new tabs
open_terminal_tab "$SERVER_DIR" "mvn exec:java -Dexec.args='1 4'"
open_terminal_tab "$SERVER_DIR" "mvn exec:java -Dexec.args='2 4'"
open_terminal_tab "$SERVER_DIR" "mvn exec:java -Dexec.args='3 4'"

# App process in new tab
open_terminal_tab "$APP_DIR" "mvn exec:java -Dexec.args='0 4'"
