#!/bin/sh
#Example usage:
#cat script.js | sign-script.sh
#sh sign-script.sh script.js

#Set your key paths here
PRIVATE_KEY_PATH=~/.ssh/id_rsa
PUBLIC_KEY_PATH=~/.ssh/id_rsa.pub

PUBLIC_KEY_PKCS8=$(ssh-keygen -f "$PUBLIC_KEY_PATH" -e -m pkcs8 | tail -n +2 | head -n -1 | tr -d '\n')
echo "This is your public key: '$PUBLIC_KEY_PKCS8'"

if [ $# -eq 0 ]; then
  # No parameter provided, read from stdin
  DATA=$(cat)
else
  # Parameter provided, read from file
  DATA=$(cat "$1")
fi

SIGNATURE=$(echo -n "$DATA" | openssl dgst -sha512 -sign ~/.ssh/id_rsa | base64 -w 0)
echo "This is your signature: '$SIGNATURE'"
