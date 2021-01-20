#!/usr/bin/env bash

source utils.sh

usage(){
    echo "usage: -[create|delete|"
    exit -1
}

if [ $# -ne 1 ];
then
    usage
fi

if [[ "$1" == "-create" ]]
then
    cp /home/otrack/Implementation/crucial-project/dso/client/target/dso-client-1.0.jar ${DIR}/dso-client.jar # FIXME
    k8s_rs_create ${DIR}/replicaset.yaml.tmpl 1 1 "LAUNCHED"
    k8s_rs_cp ${DIR}/replicaset.yaml.tmpl ${DIR}/smile.jar "/tmp/smile.jar" # FIXME
    k8s_rs_cp ${DIR}/replicaset.yaml.tmpl ${DIR}/dso-client.jar "/tmp/dso-client.jar" # FIXME
    echo "creson=$(k8s_get_service)"
else
    k8s_rs_delete ${DIR}/replicaset.yaml.tmpl
fi
