#!/bin/bash

LOGGING=1
REV=03fc394fd5f2cb6b4dc5abe202e7016f34dd8777
START=./core/src/main/java/smile/classification/RandomForest.java

deps=(${START})
todo=(${START})

log() {
    if [[ LOGGING -eq 1 ]]
    then
	local message=$@
	echo >& 2 "["$(date +%s:%N)"] ${message}"
    fi
}

array_contains () {
    local seeking=$1; shift
    local in=1
    for element; do
        if [[ $element == "$seeking" ]]; then
            in=0
            break
        fi
    done
    echo $in
}

dep () {
    local file=$1
    grep -e "^import "  ${file}  | grep smile | awk -F'.' '{print $3".java"}' | sed s/\;//g | awk '{print "find . -iname "$1}' | bash
}

next () {
    local files=("$@")
    for f in ${files[@]}
    do
	for d in $(dep ${f})
	do
	    echo ${d}
	done
    done
}

while [ ${#todo[@]} -ne 0 ]
do
    log "DEPS ${deps[@]}"
    tmp=($(next "${todo[@]}" | sort -u))
    todo=()
    for d in ${tmp[@]}
    do
	if [[ $(array_contains ${d} ${deps[@]}) == 1 ]]
	then
	    log "+d ${d}"
	    deps+=(${d})
	    todo+=("${d}")
	fi
    done    
done

modifs=$(for d in ${deps[@]}
	 do
	     git diff --stat  ${REV} ${d} | head -n 1 | awk '{print $3}'
	 done | awk '{sum+=$1} END {print sum}')

lines=$(
    for d in ${deps[@]}
    do
	git show ${REV}:${d} > /dev/null 2>&1
	if [ $? -eq 0 ]
	then
	    git cat-file -p $(git ls-tree ${REV} ${d} | cut -d " " -f 3 | cut -f 1) | wc -l
	fi
    done  | awk '{sum+=$1} END {print sum}')

echo "stats: "${modifs}" "${lines}" "$(echo "scale=4; ${modifs}/${lines}*100" | bc)
