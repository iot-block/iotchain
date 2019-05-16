#!/usr/bin/env bash

for ((i=2;i<256;i++))
do
    sudo ifconfig lo0 alias 127.0.0.${i} up
done