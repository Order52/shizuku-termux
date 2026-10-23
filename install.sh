#!/data/data/com.termux/files/usr/bin/bash
# shizuku-termux installer
#   curl -fsSL https://raw.githubusercontent.com/Order52/shizuku-termux/main/install.sh | bash

# Wrapped in a function so `curl | bash` reads the whole script before running it.
main() {
  set -e

  REPO_RAW="https://raw.githubusercontent.com/Order52/shizuku-termux/main"

  if [ -z "$PREFIX" ] || [ ! -d "$PREFIX/bin" ]; then
    echo "Run this inside Termux (https://github.com/termux/termux-app/releases)." >&2
    exit 1
  fi

  echo "Installing the shizuku command..."
  if [ -n "$SZ_LOCAL" ]; then
    cp "$SZ_LOCAL" "$PREFIX/bin/shizuku"
  else
    curl -fsSL "$REPO_RAW/shizuku" -o "$PREFIX/bin/shizuku"
  fi
  chmod +x "$PREFIX/bin/shizuku"

  echo "Setting up adb (can take a minute)..."
  if ! FORCE_SETUP=1 "$PREFIX/bin/shizuku" setup </dev/null; then
    echo "Could not set up adb. Run 'pkg upgrade' and run this installer again." >&2
    exit 1
  fi

  # Shortcuts and autostart for every shell Termux may use (bash, zsh, fish).
  BLOCK=$(printf '%s\n' \
    '# >>> shizuku-termux >>>' \
    'alias sz="shizuku start"' \
    'alias szon="shizuku start"' \
    'alias szoff="shizuku stop"' \
    'alias szs="shizuku status"' \
    '( shizuku --auto >/dev/null 2>&1 & )' \
    '# <<< shizuku-termux <<<')
  for rc in "$HOME/.bashrc" "$HOME/.zshrc"; do
    if [ "$rc" = "$HOME/.bashrc" ] || [ -f "$rc" ]; then
      touch "$rc"
      sed -i '/# >>> shizuku-termux >>>/,/# <<< shizuku-termux <<</d' "$rc"
      printf '%s\n' "$BLOCK" >> "$rc"
    fi
  done

  FISH="$HOME/.config/fish"
  mkdir -p "$FISH/functions" "$FISH/conf.d"
  for pair in "sz:start" "szon:start" "szoff:stop" "szs:status"; do
    name=${pair%%:*}; cmd=${pair#*:}
    printf 'function %s\n    shizuku %s $argv\nend\n' "$name" "$cmd" > "$FISH/functions/$name.fish"
  done
  printf '%s\n' \
    'if status is-interactive' \
    '    command shizuku --auto >/dev/null 2>&1 &' \
    '    disown 2>/dev/null' \
    'end' > "$FISH/conf.d/shizuku-termux.fish"

  # Let the Shizuku Termux app (the APK) ask Termux to run the command.
  PROPS="$HOME/.termux/termux.properties"
  mkdir -p "$HOME/.termux"
  touch "$PROPS"
  if grep -qE '^[[:space:]]*allow-external-apps' "$PROPS"; then
    sed -i -E 's/^[[:space:]]*allow-external-apps.*/allow-external-apps = true/' "$PROPS"
  else
    echo "allow-external-apps = true" >> "$PROPS"
  fi
  command -v termux-reload-settings >/dev/null 2>&1 && termux-reload-settings || true

  echo
  echo "Done. Commands:"
  echo "  sz      start Shizuku"
  echo "  szoff   stop Shizuku"
  echo "  szs     status"
  echo "  shizuku help"
  echo
  echo "Open a new Termux session (or restart Termux) for the shortcuts to work."
  echo "Now starting Shizuku..."
  echo
  "$PREFIX/bin/shizuku" start </dev/null || true
}

main "$@"
