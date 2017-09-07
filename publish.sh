#!/bin/bash

set -exuo pipefail

SERVICE_NAME=bluewhale
bucket_region=${AWS_REGION:-${AWS_DEFAULT_REGION:-eu-west-1}}
ARTEFACTS_BUCKET=as24-artifacts-$bucket_region

ME=`basename $0`
OS=`uname`
if [ "$OS" = "Darwin" ] ; then
    MYFULL="$0"
else
    MYFULL=`readlink -sm $0`
fi
MYDIR=`dirname ${MYFULL}`

fail()
{
  echo "[$ME] FAIL: $*"
  exit 1
}

[ -d "${MYDIR}/target" ] || fail "Does not look like a build has happened here. Directory ${MYDIR}/target doesn't exist"

SERVICE_ARTEFACT="${MYDIR}/target/universal/${SERVICE_NAME}-${GO_PIPELINE_LABEL}.tgz"
[ -f "${SERVICE_ARTEFACT}" ] || fail "Artefact doesn't exist: ${SERVICE_ARTEFACT}"

echo "[$ME] Uploading artefacts to S3"
aws s3 cp "${SERVICE_ARTEFACT}" "s3://${ARTEFACTS_BUCKET}/${SERVICE_NAME}/"

