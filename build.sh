#!/bin/bash

set -euo pipefail

ME=`basename $0`
OS=`uname`
if [ "$OS" = "Darwin" ] ; then
    MYFULL="$0"
else
    MYFULL=`readlink -sm $0`
fi
MYDIR=`dirname ${MYFULL}`
echo MYDIR=${MYDIR}

echo PWD=`pwd`

echo ${GO_PIPELINE_LABEL} > ./resources/build.txt

cd ${MYDIR}
exec "${MYDIR}/activator" clean compile test universal:packageZipTarball -Dsbt.log.noformat=true
