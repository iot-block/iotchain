#!/usr/bin/env bash

set -e

echo "building iotchain jar"
sbt "project appJVM" assembly
