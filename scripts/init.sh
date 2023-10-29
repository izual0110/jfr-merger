#!/usr/bin/env bash

wget https://github.com/async-profiler/async-profiler/releases/download/v2.10/async-profiler-2.10-linux-x64.tar.gz

tar -xf async-profiler-2.10-linux-x64.tar.gz

cp async-profiler-2.10-linux-x64/lib/converter.jar converter.jar
chmod +x converter.jar
