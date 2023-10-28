#!/usr/bin/env bash

echo "src file: $1";

if [ -n "$2" ]
  then
    echo "$2-$3"
    java -cp converter.jar jfr2flame --cpu --from "$2" --to "$3" "$1" "$1-$2-$3-cpu.html"
    echo "dst cpu file: $1-cpu.html";

    java -cp converter.jar jfr2flame --alloc --from "$2" --to "$3" "$1" "$1-$2-$3-alloc.html"
    echo "dst alloc file: $1-alloc.html";
  else
    java -cp converter.jar jfr2flame --cpu "$1" "$1-cpu.html"
    echo "dst cpu file: $1-cpu.html";

    java -cp converter.jar jfr2flame --alloc "$1" "$1-alloc.html"
    echo "dst alloc file: $1-alloc.html";
fi
