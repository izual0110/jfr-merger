#!/usr/bin/env bash

wget https://github.com/async-profiler/async-profiler/releases/download/v3.0/async-profiler-3.0-linux-x64.tar.gz

tar -xf async-profiler-3.0-linux-x64.tar.gz

cp async-profiler-3.0-linux-x64/lib/converter.jar converter.jar
chmod +x converter.jar
