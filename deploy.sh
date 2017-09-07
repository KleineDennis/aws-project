#!/bin/bash

set -ex

(cd deploy; bundle install --deployment && bundle exec rake deploy)

