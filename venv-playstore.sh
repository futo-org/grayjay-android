#!/usr/bin/env bash
set -euo pipefail

VENV_DIR="${VENV_DIR:-.venv-playstore}"

if [[ ! -d "$VENV_DIR" ]]; then
  python3 -m venv "$VENV_DIR"
fi

source "$VENV_DIR/bin/activate"

python -m pip install --upgrade pip setuptools wheel
python -m pip install --upgrade google-api-python-client google-auth google-auth-httplib2
