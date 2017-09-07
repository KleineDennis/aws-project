# Bluewhale (tracking pixel) Statistics Service

## What does it do?
Currently Bluewhale does two things for historic reasons.
1. Frontend API for realtime statistics
1. Gets called by tp-events-plus-counter-lambda to increment values in customer-trackstat and classified-trackstat. If there is no highest MIA Tier for today, the service looks it up in article-state-history and uses the last state found.

In the beginning we only had one service, so these 2 Features ended up in the same project. They could be separated in the future.

For legacy reasons we count the mobile calls twice. This is done [here](https://github.com/AutoScout24/bluewhale/blob/master/app/bluewhale/api/counts/SearchDdbClient.scala)

## API
Deeper insights and the documentation of the API can be found here https://confluence.scout24.com/display/TechnologyChange/Bluewhale

## Create a new service

- Copy this repository (`git clone`, `rm -rf .git`)
- Update owner of this repository in the next section
- Rename `tatsu-service` to the new service name in `rakefile`, `build.sbt` and `publish.sh` (+ description in `deploy/stack.json`)
- Change OpsGenie key to your integration API key in `deploy/stack.json`
- Update runbook links in `runbooks/README.md`
- Remove this readme section :-)

## Create security role

- Copy the `tatsu-service` security config in [security-configuration](https://github.com/AutoScout24/security-configuration) repo (fork, change, pull request)

## Create service pipeline

- Copy the `tatsu-service-template` pipeline group in GoCD
- Generate a play application secret [using sbt](https://www.playframework.com/documentation/2.4.x/ApplicationSecret#Generating-an-application-secret). Make sure that the generated secret does not contain backticks or quotes. In GoCD, add an environment variable > secure variable named `GO_APPLICATION_SECRET` with that value.

## Owner

Team owning this repository: Kondor

=======
### Setup for SSH access over S3
- Please ask your member of the security guild to create a PR on the security-configuration-resticted to give you ssh access [Johannes, Daniel or JJ will review it and hopefully merge it.]  
- Add a policy like this to the security configuration:  
https://github.com/AutoScout24/security-configuration/pull/155/files

- If you don't already have an IAM group for your team, please ask Johannes, Daniel or JJ to create one for you.

### Create service pipeline
- Copy the tatsu-service-template pipeline group in GoCD

Add block in your `readme` file who is owning your service:

```
Team owning this repository: <YOUR-TEAM-NAME>@autoscout24.com
```

## Setup project (on OS X 10.10.2)

1. clone tatsu-service-template repository
2. run ./tatsu-service-template/activator
3. run "test" or "run" in activator

## How to manualy deploy your stack

### Prepare Environment

First you need to have [Bundler](http://bundler.io/) installed to manage ruby dependencies.

Run command:

```
$ bundle install --path vendor/bundle
```

To install all dependencies in local folder `vendor/bundle`.

Then you can see list of available tasks by running command:

```
$ rake -T
```

You should see kinda:

```
rake create_or_update  # create or update stack
rake delete            # delete stack
rake deploy            # deploy service
rake dump              # dump template
rake smoke_test        # run smoke tests
```

Now you can update configuration for the AWS Cloud Formation at `*-stack.json` file.

After you changed stack `json` you can check if it valid by running command:

```
$ rake dump
```

### How to Deploy From Your Local

To deploy build from local machine you need to be logged in your console in AWS with `PowerUserAccess`, probably use 
[iam-bash](https://github.com/AutoScout24/iam-bash) tool. Something like:

```
$ aws_assume as24dev PowerUserAccess
```

Now you need number of last successful build from the GO CD for your build, for example here we will use number `19`.

Also you need to have id of the latest AMI. To get the ID, you can go to the GO build:
[https://go.cd.autoscout24.com/go/tab/pipeline/history/amazon-linux-base](https://go.cd.autoscout24.com/go/tab/pipeline/history/amazon-linux-base)

and from last build artifact `ami.txt` copy paste id, it should be something like `ami-ace9a9db`.

Then you can run command like:

```
$ AMI_ID=ami-ace9a9db VERSION=19 rake deploy
```

### Smoke Tests

To run smoke tests you need to be logged in your console in AWS with `PowerUserAccess`, probably use 
[iam-bash](https://github.com/AutoScout24/iam-bash) tool. Something like:

```
$ aws_assume as24dev PowerUserAccess
```

You can run smoke tests from file `smoke_test.rb` by using following command:

```
$ ruby smoke_test.rb
```
