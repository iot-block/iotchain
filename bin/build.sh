#!/usr/bin/env bash

set -e

echo "building jbok app"
sbt ";clean;project appJVM" stage
