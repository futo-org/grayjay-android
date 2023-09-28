#!/bin/sh
PRIVATE_KEY=$(openssl genpkey -algorithm RSA -outform PEM)
PUBLIC_KEY=$(echo "$PRIVATE_KEY" | openssl rsa -pubout -outform PEM)
echo -en "\nPrivate key:\n$PRIVATE_KEY\n"
echo -en "\nPrivate key (base64):\n$(echo "$PRIVATE_KEY" | base64 -w 0)\n"
echo -en "\nPublic key:\n$PUBLIC_KEY\n"
echo -en "\nPublic key (base64):\n$(echo "$PUBLIC_KEY" | base64 -w 0)\n"