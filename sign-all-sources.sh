#!/bin/bash

dirs=("app/src/unstable/assets/sources" "app/src/stable/assets/sources")

sign_scripts() {
  local plugin_dir=$1

  if [[ -d "$plugin_dir" ]]; then
    script_file=$(find "$plugin_dir" -maxdepth 2 -name '*Script.js' | head -n 1)
    config_file=$(find "$plugin_dir" -maxdepth 2 -name '*Config.json' | head -n 1)
    sign_script="$plugin_dir/sign.sh"
    
    if [[ -f "$sign_script" && -n "$script_file" && -n "$config_file" ]]; then
      sh "$sign_script" "$script_file" "$config_file"
    fi
  fi
}

for dir in "${dirs[@]}"; do
  if [[ -d "$dir" ]]; then
    for plugin in "$dir"/*; do
      if [[ -d "$plugin" ]]; then
        sign_scripts "$plugin"
      fi
    done
  fi
done