Smile
=====

This is a tentative serverless version of the [Smile](http://haifengl.github.io/smile/) ML library.
It currently works with AWS Lambda (alternative FaaS infrastructures to come).
This code was installed and tested successfully with SBT 1.4.6 and AdoptOpenJDK 11.0.9.

## For the impatient
- clone this repository
- clone [DSO](https://github.com/crucial-project/dso) and install it using `mvn clean install -Dskiptests`
- clone [Executor](https://github.com/crucial-project/executor) and install it using `mvn clean install -Dskiptests`
- bootstrap a k8s cluster 
- then, in serverless/bin
  1. cp ../../core/src/test/resources/config.properties.tmpl config.properties
  2. edit REGION, ACCOUNT and ROLE accordingly
  3. run deploy.sh -create-dso to deploy DSO over k8s
  4. edit "creson=" in config.properties accordingly
  6. run deploy.sh -create-lambda to deploy over Lambda
  7. edit "aws.lambda.function.arn=" in config.properties accordingly
- go back to the project root dir 
- cp serverless/bin/config.properties core/src/test/resources/config.properties
- launch sbt
- in sbt, execute: test:testOnly smile.classification.RandomForestTest
