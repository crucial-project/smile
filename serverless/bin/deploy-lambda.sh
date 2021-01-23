#!/usr/bin/env bash

source utils.sh

usage(){
    echo "usage: -[create|delete]"
    exit -1
}

if [ $# -ne 1 ];
then
    usage
fi

AWS_REGION=$(config aws.region)
AWS_ROLE=$(config aws.iam.role)
AWS_LAMBDA_FUNCTION_NAME=$(config aws.lambda.function.name)
AWS_LAMBDA_FUNCTION_HANDLER=$(config aws.lambda.function.handler)

if [[ "$1" == "-create" ]]
then
    APP_JAR="$(config app)-$(config version).jar"
    APP_TEST_JAR="$(config app)-$(config version)-tests.jar"
    CODEDIR=${TMPDIR}/code
    rm -Rf ${CODEDIR}
    rm -f ${TMPDIR}/code.zip
    mkdir -p ${CODEDIR}/lib

    # FIXME
    AWS_SDK_VERSION="1.11.896"
    cd ${PROJDIR}
    sbt compile test:compile
    cd ${DIR}
    modules=("math" "data" "graph" "netlib" "core")
    for module in ${modules[@]};
    do
    	cp -Rf ${PROJDIR}/${module}/target//classes/* ${CODEDIR}/
    	cp -Rf ${PROJDIR}/${module}/target//test-classes/* ${CODEDIR}/
    done
    jar cvf smile.jar -C ${CODEDIR} .
    for j in $(find ~/Implementation/crucial-project/executor/target -maxdepth 1 -iname "*.jar");
    do
    	unzip -q -o $j -d ${CODEDIR}
    done
    for j in $(find ~/Implementation/crucial-project/executor/target -iname "*.jar");
    do
    	unzip -q -o $j -d ${CODEDIR}	
    done
    for j in $(find ~/Implementation/crucial-project/dso/client/target -iname "dso-client*.jar");
    do
    	unzip -q -o $j -d ${CODEDIR}	
    done
    for j in $(find ~/Implementation/crucial-project/dso/server/target -iname "slf4j*.jar");
    do
    	unzip -q -o $j -d ${CODEDIR}	
    done
    for j in $(find ~/.ivy2/cache -iname "*aws-java-sdk-s3*jar" | grep ${AWS_SDK_VERSION});
    do
    	unzip -q -o $j -d ${CODEDIR}
    done
    for j in $(find ~/.ivy2/cache -iname "*aws-java-sdk-core*jar" | grep ${AWS_SDK_VERSION});
    do
    	unzip -q -o $j -d ${CODEDIR}
    done
    for j in $(find ~/.ivy2/cache -iname "*joda-time*jar" | grep 2.8.1);
    do
    	unzip -q -o $j -d ${CODEDIR}
    done

    for j in $(find ~/Implementation/crucial-project/dso/client/target/lib -iname "*.jar");
    do
    	unzip -q -o $j -d ${CODEDIR}	
    done
    cp ${DIR}/exp.config ${CODEDIR}/config.properties
    cd ${CODEDIR} && zip -r -q code.zip * && mv code.zip ${DIR} && cd ${DIR}
    aws lambda create-function \
    	--function-name ${AWS_LAMBDA_FUNCTION_NAME} \
    	--runtime java11 \
    	--role ${AWS_ROLE} \
    	--timeout 60 \
    	--publish \
    	--region ${AWS_REGION} \
    	--memory-size 2000 \
    	--handler ${AWS_LAMBDA_FUNCTION_HANDLER} \
    	--zip-file fileb://${DIR}/code.zip  > ${TMPDIR}/log.dat
    
    cat ${CONFIG_FILE}
    echo "aws.lambda.function.arn=$(grep FunctionArn ${TMPDIR}/log.dat  | awk -F": " '{print $2}' | sed s,[\"\,],,g)" 
elif [[ "$1" == "-delete" ]]
then
    aws lambda delete-function \
	--region ${AWS_REGION} \
	--function-name ${AWS_LAMBDA_FUNCTION_NAME}
else
    usage
fi
