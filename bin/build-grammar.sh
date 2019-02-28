#!/usr/bin/env bash

DIRNAME=$(dirname $0)
PARENT="$DIRNAME/.."

ANTLR_PATH="$PARENT/bin"
ANTLR_JAR="antlr-4.7.2-complete.jar"
ANTLR="$ANTLR_PATH/$ANTLR_JAR"

if [[ ! -e "$ANTLR" ]]; then
    curl https://www.antlr.org/download/${ANTLR_JAR} -o "$ANTLR"
fi

PARSER_PATH="./parser/shared/src/main/scala/jbok/solidity/grammar"

SOLIDITY_FILE="$PARSER_PATH/Solidity.g4"
OUTPUT_SRC_PATH="$PARSER_PATH"
TEST_FILE="$PARSER_PATH/SolidityTest.sol"
TARGET_PATH="$PARENT/target"

START_RULE="sourceUnit"
ERROR_PATTERN="mismatched|extraneous"

mkdir -p ${TARGET_PATH}

java -jar ${ANTLR} ${SOLIDITY_FILE} -no-listener -visitor -package jbok.solidity.grammar
javac -classpath ${ANTLR} ${OUTPUT_SRC_PATH}/*.java -d ${TARGET_PATH}
java -classpath ${ANTLR}:${TARGET_PATH} org.antlr.v4.gui.TestRig "jbok.solidity.grammar.Solidity" "$START_RULE" < "$TEST_FILE" 2>&1 | grep -qE "$ERROR_PATTERN" && echo "TESTS FAIL!" || echo "TESTS PASS!"

