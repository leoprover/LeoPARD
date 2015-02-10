#!/bin/sh

classpath="target/classes"
compile=false

# Check for not compile option
while getopts ":r" opt; do
   case $opt in
      c)
         compile=true
         ;;
      \?)
         echo "Invalid optioin: -$OPTARG" >&2
         exit -1
   esac
done

# First compile the project
if [ "$compile" = true ]
then
   make
fi

version=$(sed -n "/version/{s/.*<version>\(.*\)<\/version>.*/\1/;p}" pom.xml | head -n 1)
java -jar target/leopard-$version.jar "$@"
