#!/usr/bin/env bash

source utils.sh

usage(){
    echo "usage: -[create-lambda|delete-lambda|create-dso|delete-dso]"
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

CRUCIAL_DIR="${HOME}/Implementation/crucial-project" # FIXME

function build_archive(){
    CODEDIR=${TMPDIR}/code
    rm -Rf ${CODEDIR}
    rm -f ${TMPDIR}/code.zip
    mkdir -p ${CODEDIR}/lib

    AWS_SDK_VERSION="1.11.896" #FIXME
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
    for j in $(find ${CRUCIAL_DIR}/executor/target -maxdepth 1 -iname "*.jar");
    do
    	unzip -q -o $j -d ${CODEDIR}
    done
    for j in $(find ${CRUCIAL_DIR}/executor/target -iname "*.jar");
    do
    	unzip -q -o $j -d ${CODEDIR}	
    done
    for j in $(find ${CRUCIAL_DIR}/dso/client/target -iname "dso-client*.jar");
    do
    	unzip -q -o $j -d ${CODEDIR}	
    done
    for j in $(find ${CRUCIAL_DIR}/dso/client/target/lib -iname "*.jar");
    do
    	unzip -q -o $j -d ${CODEDIR}	
    done
    for j in $(find ${CRUCIAL_DIR}/dso/server/target -iname "slf4j*.jar");
    do
    	unzip -q -o $j -d ${CODEDIR}	
    done
    for j in $(find ${HOME}/.ivy2/cache -iname "*aws-java-sdk-s3*jar" | grep ${AWS_SDK_VERSION});
    do
    	unzip -q -o $j -d ${CODEDIR}
    done
    for j in $(find ${HOME}/.ivy2/cache -iname "*aws-java-sdk-core*jar" | grep ${AWS_SDK_VERSION});
    do
    	unzip -q -o $j -d ${CODEDIR}
    done
    for j in $(find ${HOME}/.ivy2/cache -iname "*joda-time*jar" | grep 2.8.1);
    do
    	unzip -q -o $j -d ${CODEDIR}
    done
    
    cp ${DIR}/config.properties ${CODEDIR}
    cd ${CODEDIR} && zip -r -q code.zip * && mv code.zip ${DIR} && cd ${DIR}
}

if [[ "$1" == "-create-lambda" ]];
then
    build_archive
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
elif [[ "$1" == "-delete-lambda" ]];
then
    aws lambda delete-function \
	--region ${AWS_REGION} \
	--function-name ${AWS_LAMBDA_FUNCTION_NAME}
elif [[ "$1" == "-create-dso" ]];
then
    build_archive
    k8s_rs_create ${DIR}/replicaset.yaml.tmpl 1 1 "LAUNCHED"
    k8s_rs_cp ${DIR}/replicaset.yaml.tmpl ${DIR}/smile.jar "/tmp/smile.jar" # FIXME
    echo "creson=$(k8s_get_service)"
elif [[ "$1" == "-delete-dso" ]];
then
    k8s_rs_delete ${DIR}/replicaset.yaml.tmpl
else
    usage
fi
