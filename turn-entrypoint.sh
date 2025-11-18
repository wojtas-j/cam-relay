#!/bin/sh

turnserver \
  --log-file=stdout \
  --realm="$TURN_REALM" \
  --use-auth-secret \
  --static-auth-secret="$TURN_PASSWORD" \
  --user="$TURN_USERNAME:$TURN_PASSWORD" \
  --no-tls \
  --no-dtls
