#!/bin/bash

# Array of directories to look in
dirs=("app/src/unstable/assets/sources" "app/src/stable/assets/sources")

# Loop through each directory
for dir in "${dirs[@]}"; do
  if [[ -d "$dir" ]]; then  # Check if directory exists
    for plugin in "$dir"/*; do  # Loop through each plugin folder
      if [[ -d "$plugin" ]]; then
        script_file=$(find "$plugin" -maxdepth 1 -name '*Script.js')
        config_file=$(find "$plugin" -maxdepth 1 -name '*Config.json')
        sign_script="$plugin/sign.sh"
        
        if [[ -f "$sign_script" && -n "$script_file" && -n "$config_file" ]]; then
          sh "$sign_script" "$script_file" "$config_file"
        fi
      fi
    done
  fi
done
